package com.winlator.wowmobile;

import android.content.Context;
import android.content.Intent;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Manages the single Winlator container used to run WoW, plus the launch shortcut
 * that ties the container, the game exe and the touch controls profile together.
 */
public class WowContainerHelper {
    public static final String CONTAINER_NAME = "WoW";
    public static final String SHORTCUT_NAME = "World of Warcraft";
    public static final String CONTROLS_PROFILE_NAME = "WoW ConsolePortLK";
    public static final String GAME_DRIVE_LETTER = "F";
    public static final String DEFAULT_SCREEN_SIZE = "960x432";

    private final Context context;
    private final ContainerManager manager;

    public WowContainerHelper(Context context) {
        this.context = context;
        this.manager = new ContainerManager(context);
    }

    public Container getContainer() {
        for (Container container : manager.getContainers()) {
            if (CONTAINER_NAME.equals(container.getName())) return container;
        }
        return null;
    }

    /** Creates the WoW container with tuned defaults if it does not exist yet. */
    public void ensureContainerAsync(GameFolder gameFolder, Callback<Container> callback) {
        Container existing = getContainer();
        if (existing != null) {
            ensureGameDrive(existing, gameFolder);
            callback.call(existing);
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", DEFAULT_SCREEN_SIZE);
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(context));
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES + GAME_DRIVE_LETTER+":"+gameFolder.root.getPath());
            data.put("startupSelection", Container.STARTUP_SELECTION_AGGRESSIVE);
            manager.createContainerAsync(data, callback);
        }
        catch (JSONException e) {
            callback.call(null);
        }
    }

    /** Keeps the F: drive pointing at the currently selected game folder. */
    private void ensureGameDrive(Container container, GameFolder gameFolder) {
        String drives = container.getDrives();
        String gameDrive = GAME_DRIVE_LETTER+":"+gameFolder.root.getPath();

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (com.winlator.container.Drive drive : Container.drivesIterator(drives)) {
            if (drive.letter.equals(GAME_DRIVE_LETTER)) {
                sb.append(gameDrive);
                found = true;
            }
            else sb.append(drive.letter).append(":").append(drive.path);
        }
        if (!found) sb.append(gameDrive);

        String newDrives = sb.toString();
        if (!newDrives.equals(drives)) {
            container.setDrives(newDrives);
            container.saveData();
        }
    }

    /** Writes the launch shortcut binding exe + controls profile, then returns it. */
    public File ensureShortcut(Container container, GameFolder gameFolder) {
        File desktopDir = new File(container.getUserDir(), "Desktop");
        if (!desktopDir.isDirectory()) desktopDir.mkdirs();

        File shortcutFile = new File(desktopDir, SHORTCUT_NAME+".desktop");
        String dosPath = WineUtils.unixToDOSPath(gameFolder.getWowExe().getPath(), container);

        // Shortcut.unescapeDOSPath applies its unescape pass twice, so the stored
        // Exec path needs two rounds of escaping to survive parsing.
        String content = "[Desktop Entry]\n" +
            "Name="+SHORTCUT_NAME+"\n" +
            "Exec=wine "+StringUtils.escapeDOSPath(StringUtils.escapeDOSPath(dosPath))+"\n" +
            "StartupWMClass=wow.exe\n" +
            "\n[Extra Data]\n";

        int profileId = getControlsProfileId();
        if (profileId > 0) content += "controlsProfile="+profileId+"\n";

        FileUtils.writeString(shortcutFile, content);
        return shortcutFile;
    }

    private int getControlsProfileId() {
        InputControlsManager inputControlsManager = new InputControlsManager(context);
        for (ControlsProfile profile : inputControlsManager.getProfiles()) {
            if (CONTROLS_PROFILE_NAME.equals(profile.getName())) return profile.id;
        }
        return 0;
    }

    public Intent createLaunchIntent(Container container, File shortcutFile) {
        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcutFile.getPath());
        return intent;
    }
}
