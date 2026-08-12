package com.winlator.wowmobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.core.AppUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * WoW Mobile home screen: select the game folder, provision it for
 * ConsolePortLK + touch controls, and play.
 */
public class WowMobileActivity extends AppCompatActivity {
    public static final String PREF_GAME_FOLDER = "wow_game_folder";
    private SharedPreferences preferences;
    private TextView tvStatus;
    private TextView tvFolder;
    private TextView tvRealmlist;
    private Button btPlay;
    private final PreloaderDialog preloaderDialog = new PreloaderDialog(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wow_main_activity);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        tvStatus = findViewById(R.id.TVWowStatus);
        tvFolder = findViewById(R.id.TVWowFolder);
        tvRealmlist = findViewById(R.id.TVWowRealmlist);
        btPlay = findViewById(R.id.BTPlay);

        btPlay.setOnClickListener((v) -> onPlay());
        findViewById(R.id.BTSelectFolder).setOnClickListener((v) -> showFolderPicker());
        findViewById(R.id.BTWowSettings).setOnClickListener((v) -> startActivity(new Intent(this, WowSettingsActivity.class)));
        findViewById(R.id.BTContainerSettings).setOnClickListener((v) -> startActivity(new Intent(this, ContainerSettingsActivity.class)));
        findViewById(R.id.BTAdvanced).setOnClickListener((v) -> startActivity(new Intent(this, MainActivity.class)));

        if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MainActivity.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
                updateStatus();
            }
            else finish();
        }
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, MainActivity.PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        // Pick up phase-B provisioning after the guided first login + logout
        GameFolder gameFolder = getGameFolder();
        if (gameFolder != null && gameFolder.isValid()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                Provisioner provisioner = new Provisioner(this, gameFolder);
                if (provisioner.getStatus() == Provisioner.Status.PENDING) {
                    provisioner.provision();
                    runOnUiThread(this::updateStatus);
                }
            });
        }
    }

    public GameFolder getGameFolder() {
        String path = preferences.getString(PREF_GAME_FOLDER, null);
        return path != null ? new GameFolder(path) : null;
    }

    private void updateStatus() {
        GameFolder gameFolder = getGameFolder();

        if (gameFolder == null) {
            tvFolder.setText(R.string.wow_no_folder_selected);
            tvStatus.setText(R.string.wow_status_select_folder);
            tvRealmlist.setText("");
            btPlay.setEnabled(false);
            return;
        }

        tvFolder.setText(gameFolder.root.getPath());

        if (!gameFolder.isValid()) {
            tvStatus.setText(R.string.wow_status_invalid_folder);
            tvRealmlist.setText("");
            btPlay.setEnabled(false);
            return;
        }

        Provisioner provisioner = new Provisioner(this, gameFolder);
        tvRealmlist.setText(getString(R.string.wow_realm_server)+": "+provisioner.getActiveRealmlist());
        btPlay.setEnabled(true);

        switch (provisioner.getStatus()) {
            case PENDING:
                tvStatus.setText(R.string.wow_status_pending);
                break;
            case WAITING_FIRST_LOGIN:
                tvStatus.setText(R.string.wow_status_first_login);
                break;
            case READY:
                tvStatus.setText(R.string.wow_status_ready);
                break;
            default:
                tvStatus.setText(R.string.wow_status_invalid_folder);
                break;
        }
    }

    private void onPlay() {
        GameFolder gameFolder = getGameFolder();
        if (gameFolder == null || !gameFolder.isValid()) {
            showFolderPicker();
            return;
        }

        preloaderDialog.show(R.string.wow_preparing_game);
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            Provisioner provisioner = new Provisioner(this, gameFolder);
            boolean provisioned = provisioner.provision();

            handler.post(() -> {
                if (!provisioned) {
                    preloaderDialog.close();
                    AppUtils.showToast(this, R.string.wow_provision_failed);
                    return;
                }

                WowContainerHelper helper = new WowContainerHelper(this);
                helper.ensureContainerAsync(gameFolder, (container) -> {
                    preloaderDialog.close();
                    if (container == null) {
                        AppUtils.showToast(this, R.string.wow_container_failed);
                        return;
                    }
                    File shortcutFile = helper.ensureShortcut(container, gameFolder);
                    startActivity(helper.createLaunchIntent(container, shortcutFile));
                });
            });
        });
    }

    private void showFolderPicker() {
        GameFolder current = getGameFolder();
        File startDir = current != null && current.root.isDirectory() ?
            current.root : Environment.getExternalStorageDirectory();
        showFolderPickerAt(startDir);
    }

    private void showFolderPickerAt(final File dir) {
        final ArrayList<File> entries = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();

        File parent = dir.getParentFile();
        boolean hasParent = parent != null && !dir.equals(Environment.getExternalStorageDirectory()) && parent.canRead();
        if (hasParent) {
            entries.add(parent);
            names.add("..");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            ArrayList<File> dirs = new ArrayList<>();
            for (File file : files) if (file.isDirectory() && !file.getName().startsWith(".")) dirs.add(file);
            dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File file : dirs) {
                entries.add(file);
                names.add(file.getName());
            }
        }

        GameFolder candidate = new GameFolder(dir);
        String title = dir.getPath().replace(Environment.getExternalStorageDirectory().getPath(), getString(R.string.internal_storage));
        if (candidate.isValid()) title += " ✓";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        ListView listView = new ListView(this);
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        builder.setView(listView);

        if (candidate.isValid()) {
            builder.setPositiveButton(R.string.wow_use_this_folder, (d, w) -> {
                preferences.edit().putString(PREF_GAME_FOLDER, dir.getPath()).apply();
                updateStatus();
            });
        }
        builder.setNegativeButton(R.string.cancel, (d, w) -> {});

        final AlertDialog dialog = builder.create();
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            dialog.dismiss();
            showFolderPickerAt(entries.get(position));
        });
        dialog.show();
    }
}
