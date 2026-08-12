package com.winlator.wowmobile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a WoW 3.3.5a installation folder on shared storage.
 */
public class GameFolder {
    public final File root;

    public GameFolder(File root) {
        this.root = root;
    }

    public GameFolder(String path) {
        this(new File(path));
    }

    public boolean isValid() {
        return getWowExe().isFile();
    }

    public File getWowExe() {
        // Wow.exe with any casing
        File exact = new File(root, "Wow.exe");
        if (exact.isFile()) return exact;
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equalsIgnoreCase("wow.exe")) return file;
            }
        }
        return exact;
    }

    public File getDataDir() {
        return new File(root, "Data");
    }

    /**
     * Detects the locale folder (enUS, enGB, deDE, ...) inside Data/.
     * A locale folder is a 4-letter directory; prefer the one holding realmlist.wtf.
     */
    public File getLocaleDir() {
        File[] files = getDataDir().listFiles();
        if (files == null) return null;

        File candidate = null;
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory() && name.length() == 4 &&
                Character.isLowerCase(name.charAt(0)) && Character.isUpperCase(name.charAt(2))) {
                if (new File(file, "realmlist.wtf").isFile()) return file;
                if (candidate == null) candidate = file;
            }
        }
        return candidate;
    }

    public String getLocale() {
        File localeDir = getLocaleDir();
        return localeDir != null ? localeDir.getName() : null;
    }

    public File getRealmlistFile() {
        File localeDir = getLocaleDir();
        return localeDir != null ? new File(localeDir, "realmlist.wtf") : null;
    }

    public File getConfigWtf() {
        return new File(root, "WTF/Config.wtf");
    }

    public File getAddOnsDir() {
        return new File(root, "Interface/AddOns");
    }

    public File getAccountRootDir() {
        return new File(root, "WTF/Account");
    }

    /** Account folders appear only after the first login. */
    public List<File> getAccountDirs() {
        ArrayList<File> accounts = new ArrayList<>();
        File[] files = getAccountRootDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().equals("SavedVariables")) accounts.add(file);
            }
        }
        return accounts;
    }

    public File getMarkerFile() {
        return new File(root, "wowmobile.json");
    }
}
