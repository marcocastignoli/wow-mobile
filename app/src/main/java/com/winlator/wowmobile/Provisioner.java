package com.winlator.wowmobile;

import android.content.Context;

import com.winlator.core.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Idempotent provisioning of a WoW 3.3.5a folder for ConsolePortLK + touch controls.
 *
 * Everything runs off a marker file (wowmobile.json) stored in the game folder, so
 * re-running is cheap and only applies pending or out-of-date steps.
 */
public class Provisioner {
    public static final int PROVISION_VERSION = 1;
    public static final String DEFAULT_REALMLIST = "logon.therawow.com";
    private static final String ADDONS_ASSET = "wowmobile/ConsolePortLK.zip";

    public enum Status {
        INVALID_FOLDER,      // Wow.exe not found
        PENDING,             // phase A not applied yet
        WAITING_FIRST_LOGIN, // phase A done, no account folder yet
        READY                // phase A done and all known accounts patched
    }

    private final Context context;
    private final GameFolder gameFolder;
    private JSONObject marker;

    public Provisioner(Context context, GameFolder gameFolder) {
        this.context = context;
        this.gameFolder = gameFolder;
        loadMarker();
    }

    private void loadMarker() {
        marker = new JSONObject();
        File file = gameFolder.getMarkerFile();
        if (file.isFile()) {
            try {
                marker = new JSONObject(FileUtils.readString(file));
            }
            catch (JSONException e) {}
        }
    }

    private void saveMarker() {
        FileUtils.writeString(gameFolder.getMarkerFile(), marker.toString());
    }

    public Status getStatus() {
        if (!gameFolder.isValid()) return Status.INVALID_FOLDER;
        if (marker.optInt("provisionVersion", 0) < PROVISION_VERSION) return Status.PENDING;

        List<File> accounts = gameFolder.getAccountDirs();
        if (accounts.isEmpty()) return Status.WAITING_FIRST_LOGIN;

        JSONObject provisionedAccounts = marker.optJSONObject("accounts");
        for (File account : accounts) {
            if (provisionedAccounts == null ||
                provisionedAccounts.optInt(account.getName(), 0) < PROVISION_VERSION) return Status.PENDING;
        }
        return Status.READY;
    }

    public String getActiveRealmlist() {
        File file = gameFolder.getRealmlistFile();
        if (file != null && file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith("set realmlist")) {
                    return trimmed.substring("set realmlist".length()).trim();
                }
            }
        }
        return marker.optString("realmlist", DEFAULT_REALMLIST);
    }

    /**
     * Applies all pending steps. Safe to call repeatedly.
     * @return true if everything applicable succeeded
     */
    public boolean provision() {
        if (!gameFolder.isValid()) return false;

        try {
            if (marker.optInt("provisionVersion", 0) < PROVISION_VERSION) {
                if (!installAddons()) return false;
                if (!patchToc()) return false;
                ensureRealmlist(marker.optString("realmlist", DEFAULT_REALMLIST));
                patchConfigWtf();
                marker.put("provisionVersion", PROVISION_VERSION);
                saveMarker();
            }

            // Per-account steps: applied as soon as the account folder exists
            // (which happens after the first login + logout).
            JSONObject provisionedAccounts = marker.optJSONObject("accounts");
            if (provisionedAccounts == null) {
                provisionedAccounts = new JSONObject();
                marker.put("accounts", provisionedAccounts);
            }

            for (File accountDir : gameFolder.getAccountDirs()) {
                String name = accountDir.getName();
                if (provisionedAccounts.optInt(name, 0) < PROVISION_VERSION) {
                    patchBindingsCache(accountDir);
                    seedSavedVariables(accountDir);
                    provisionedAccounts.put(name, PROVISION_VERSION);
                    saveMarker();
                }
            }
            return true;
        }
        catch (JSONException e) {
            return false;
        }
    }

    /** Extracts the bundled ConsolePortLK release into Interface/AddOns. */
    private boolean installAddons() {
        File addOnsDir = gameFolder.getAddOnsDir();
        if (!addOnsDir.isDirectory() && !addOnsDir.mkdirs()) return false;

        try (ZipInputStream zis = new ZipInputStream(context.getAssets().open(ADDONS_ASSET))) {
            byte[] buffer = new byte[64 * 1024];
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File target = new File(addOnsDir, entry.getName());
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(addOnsDir.getCanonicalPath())) continue;

                if (entry.isDirectory()) {
                    target.mkdirs();
                }
                else {
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(target))) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) os.write(buffer, 0, count);
                    }
                }
            }
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    /**
     * Makes ConsolePort keyboard bindings account-wide instead of per-character,
     * so every character shares the working control scheme.
     */
    private boolean patchToc() {
        File tocFile = new File(gameFolder.getAddOnsDir(), "ConsolePort/ConsolePort.toc");
        if (!tocFile.isFile()) return false;

        String content = FileUtils.readString(tocFile);
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String out = line;
            if (line.startsWith("## SavedVariables:") && !line.contains("ConsolePortBindingSet")) {
                out = line.trim() + ", ConsolePortBindingSet";
            }
            else if (line.startsWith("## SavedVariablesPerCharacter:")) {
                out = line.replace("ConsolePortBindingSet,", "").replace(", ConsolePortBindingSet", "")
                          .replace("ConsolePortBindingSet", "").replace(":,", ":");
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(out);
        }
        return FileUtils.writeString(tocFile, sb.toString());
    }

    /**
     * Sets the active realm in Data/&lt;locale&gt;/realmlist.wtf, preserving any
     * commented-out alternatives the user keeps in the file.
     */
    public boolean ensureRealmlist(String host) {
        File file = gameFolder.getRealmlistFile();
        if (file == null) {
            File localeDir = gameFolder.getLocaleDir();
            if (localeDir == null) return false;
            file = new File(localeDir, "realmlist.wtf");
        }

        List<String> kept = new ArrayList<>();
        if (file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                // Drop previous active lines; keep comments and blanks as-is
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") &&
                    trimmed.toLowerCase().startsWith("set realmlist")) continue;
                kept.add(line.replace("\r", ""));
            }
        }
        while (!kept.isEmpty() && kept.get(kept.size()-1).trim().isEmpty()) kept.remove(kept.size()-1);

        StringBuilder sb = new StringBuilder();
        for (String line : kept) sb.append(line).append("\n");
        sb.append("set realmlist ").append(host).append("\n");

        boolean result = FileUtils.writeString(file, sb.toString());
        if (result) {
            try {
                marker.put("realmlist", host);
                saveMarker();
            }
            catch (JSONException e) {}
            // Keep Config.wtf in sync (WoW rewrites it on exit either way)
            setConfigValue("realmList", host);
        }
        return result;
    }

    /** Tuned defaults for Mali-class phones; existing user keys are preserved. */
    private void patchConfigWtf() {
        String locale = gameFolder.getLocale();
        String[][] defaults = {
            {"locale", locale != null ? locale : "enUS"},
            {"realmList", marker.optString("realmlist", DEFAULT_REALMLIST)},
            {"hwDetect", "0"},
            {"gxWindow", "1"},
            {"gxMaximize", "1"},
            {"gxResolution", "960x432"},
            {"gxRefresh", "60"},
            {"gxMultisampleQuality", "0.000000"},
            {"gxFixLag", "0"},
            {"videoOptionsVersion", "3"},
            {"movie", "0"},
            {"readTOS", "1"},
            {"readEULA", "1"},
            {"readTerminationWithoutNotice", "1"},
            {"accounttype", "LK"},
            {"farclip", "727"},
            {"textureFilteringMode", "0"},
            {"particleDensity", "0.10000000149012"},
            {"baseMip", "1"},
            {"environmentDetail", "0.5"},
            {"weatherDensity", "0"},
            {"ffxGlow", "0"},
            {"ffxDeath", "0"}
        };
        for (String[] pair : defaults) setConfigValue(pair[0], pair[1]);
    }

    /** Sets (or replaces) a single SET key in WTF/Config.wtf. */
    public boolean setConfigValue(String key, String value) {
        File file = gameFolder.getConfigWtf();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();

        List<String> lines = new ArrayList<>();
        boolean found = false;
        if (file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("SET "+key+" ")) {
                    if (found) continue; // drop duplicates
                    lines.add("SET "+key+" \""+value+"\"");
                    found = true;
                }
                else if (!trimmed.isEmpty()) lines.add(line.replace("\r", ""));
            }
        }
        if (!found) lines.add("SET "+key+" \""+value+"\"");

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");
        return FileUtils.writeString(file, sb.toString());
    }

    public String getConfigValue(String key) {
        File file = gameFolder.getConfigWtf();
        if (file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("SET "+key+" ")) {
                    return trimmed.substring(("SET "+key+" ").length()).replace("\"", "").trim();
                }
            }
        }
        return null;
    }

    /**
     * The keystroke → ConsolePort button map. The touch overlay emits these keys,
     * ConsolePortLK turns them into virtual gamepad buttons.
     */
    private static final String[][] KEY_BINDINGS = {
        {"Y", "CP_R_UP"}, {"B", "CP_R_RIGHT"}, {"N", "CP_R_DOWN"}, {"H", "CP_R_LEFT"},
        {"I", "CP_L_UP"}, {"L", "CP_L_RIGHT"}, {"K", "CP_L_DOWN"}, {"J", "CP_L_LEFT"},
        {"Q", "CP_T1"}, {"E", "CP_T2"},
        {"G", "CP_X_LEFT"}, {"V", "CP_X_RIGHT"},
        {"F", "INTERACTTARGET"}
    };

    /**
     * Rewrites bindings-cache.wtf: removes stale "bind KEY NONE" suppressions and
     * conflicting binds for our keys, then appends the ConsolePort map.
     */
    private void patchBindingsCache(File accountDir) {
        File file = new File(accountDir, "bindings-cache.wtf");

        List<String> kept = new ArrayList<>();
        if (file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim().replace("\r", "");
                if (trimmed.isEmpty()) continue;
                boolean conflicting = false;
                for (String[] binding : KEY_BINDINGS) {
                    if (trimmed.startsWith("bind "+binding[0]+" ")) {
                        conflicting = true;
                        break;
                    }
                }
                if (!conflicting) kept.add(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : kept) sb.append(line).append("\n");
        for (String[] binding : KEY_BINDINGS) sb.append("bind ").append(binding[0]).append(" ").append(binding[1]).append("\n");
        FileUtils.writeString(file, sb.toString());
    }

    /**
     * Seeds account-wide ConsolePort settings so the addon boots calibrated:
     * Xbox layout, stick emulation from movement keys, and our key calibration.
     * Never overwrites an existing configuration.
     */
    private void seedSavedVariables(File accountDir) {
        File savedVariablesDir = new File(accountDir, "SavedVariables");
        if (!savedVariablesDir.isDirectory() && !savedVariablesDir.mkdirs()) return;

        File file = new File(savedVariablesDir, "ConsolePort.lua");
        if (file.isFile() && FileUtils.readString(file).contains("ConsolePortSettings")) return;

        String seed =
            "ConsolePortSettings = {\n" +
            "\t[\"type\"] = \"XBOX\",\n" +
            "\t[\"skipGuideBtn\"] = true,\n" +
            "\t[\"skipCP_T3\"] = true,\n" +
            "\t[\"skipCP_T4\"] = true,\n" +
            "\t[\"skipCP_T5\"] = true,\n" +
            "\t[\"skipCP_T6\"] = true,\n" +
            "\t[\"interactWith\"] = \"CP_R_DOWN\",\n" +
            "\t[\"autoLootDefault\"] = true,\n" +
            "\t[\"stickRadialType\"] = 2,\n" +
            "\t[\"stickRadialLocal\"] = true,\n" +
            "\t[\"calibration\"] = {\n" +
            "\t\t[\"CP_R_UP\"] = \"Y\",\n" +
            "\t\t[\"CP_R_RIGHT\"] = \"B\",\n" +
            "\t\t[\"CP_R_DOWN\"] = \"N\",\n" +
            "\t\t[\"CP_R_LEFT\"] = \"H\",\n" +
            "\t\t[\"CP_L_UP\"] = \"I\",\n" +
            "\t\t[\"CP_L_RIGHT\"] = \"L\",\n" +
            "\t\t[\"CP_L_DOWN\"] = \"K\",\n" +
            "\t\t[\"CP_L_LEFT\"] = \"J\",\n" +
            "\t\t[\"CP_T1\"] = \"Q\",\n" +
            "\t\t[\"CP_T2\"] = \"E\",\n" +
            "\t\t[\"CP_X_LEFT\"] = \"G\",\n" +
            "\t\t[\"CP_X_RIGHT\"] = \"V\",\n" +
            "\t},\n" +
            "}\n";
        FileUtils.writeString(file, seed);
    }
}
