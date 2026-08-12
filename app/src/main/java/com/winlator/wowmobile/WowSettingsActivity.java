package com.winlator.wowmobile;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Edits WoW's own configuration (Config.wtf + realmlist.wtf) while the game is off.
 */
public class WowSettingsActivity extends AppCompatActivity {
    private static final String[] RESOLUTIONS = {"800x360", "960x432", "1200x540", "1600x720"};
    private static final String[] FARCLIP_LABELS = {"Near (fastest)", "Medium", "Far (slower)"};
    private static final String[] FARCLIP_VALUES = {"400", "727", "1000"};

    private GameFolder gameFolder;
    private Provisioner provisioner;
    private Spinner sRealmlist;
    private EditText etCustomRealmlist;
    private Spinner sResolution;
    private Spinner sFarclip;
    private ArrayList<String> realmlistItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wow_settings_activity);
        setSupportActionBar(findViewById(R.id.Toolbar));
        getSupportActionBar().setTitle(R.string.wow_settings);

        String path = PreferenceManager.getDefaultSharedPreferences(this).getString(WowMobileActivity.PREF_GAME_FOLDER, null);
        if (path == null || !(gameFolder = new GameFolder(path)).isValid()) {
            AppUtils.showToast(this, R.string.wow_status_select_folder);
            finish();
            return;
        }
        provisioner = new Provisioner(this, gameFolder);

        sRealmlist = findViewById(R.id.SRealmlist);
        etCustomRealmlist = findViewById(R.id.ETCustomRealmlist);
        sResolution = findViewById(R.id.SResolution);
        sFarclip = findViewById(R.id.SFarclip);

        TextView tvLocale = findViewById(R.id.TVLocale);
        String locale = gameFolder.getLocale();
        tvLocale.setText(getString(R.string.wow_detected_locale)+": "+(locale != null ? locale : "?"));

        loadRealmlists();
        loadGraphics();

        findViewById(R.id.BTSave).setOnClickListener((v) -> save());
    }

    /** Active realm + any commented alternatives kept in realmlist.wtf + our default. */
    private void loadRealmlists() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        String active = provisioner.getActiveRealmlist();
        items.add(active);

        File file = gameFolder.getRealmlistFile();
        if (file != null && file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    String uncommented = trimmed.replaceFirst("^#+ *", "");
                    if (uncommented.toLowerCase().startsWith("set realmlist")) {
                        String host = uncommented.substring("set realmlist".length()).trim();
                        if (!host.isEmpty()) items.add(host);
                    }
                }
            }
        }
        items.add(Provisioner.DEFAULT_REALMLIST);

        realmlistItems = new ArrayList<>(items);
        realmlistItems.add(getString(R.string.wow_custom_realmlist));
        sRealmlist.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, realmlistItems));
        sRealmlist.setSelection(realmlistItems.indexOf(active));

        sRealmlist.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean custom = position == realmlistItems.size()-1;
                etCustomRealmlist.setVisibility(custom ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        etCustomRealmlist.setVisibility(View.GONE);
    }

    private void loadGraphics() {
        sResolution.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, RESOLUTIONS));
        String resolution = provisioner.getConfigValue("gxResolution");
        int index = 1;
        for (int i = 0; i < RESOLUTIONS.length; i++) if (RESOLUTIONS[i].equals(resolution)) index = i;
        sResolution.setSelection(index);

        sFarclip.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FARCLIP_LABELS));
        String farclip = provisioner.getConfigValue("farclip");
        int farclipIndex = 1;
        for (int i = 0; i < FARCLIP_VALUES.length; i++) if (FARCLIP_VALUES[i].equals(farclip)) farclipIndex = i;
        sFarclip.setSelection(farclipIndex);
    }

    private void save() {
        String host;
        if (sRealmlist.getSelectedItemPosition() == realmlistItems.size()-1) {
            host = etCustomRealmlist.getText().toString().trim();
            if (host.isEmpty()) {
                AppUtils.showToast(this, R.string.wow_custom_realmlist);
                return;
            }
        }
        else host = realmlistItems.get(sRealmlist.getSelectedItemPosition());

        provisioner.ensureRealmlist(host);
        provisioner.setConfigValue("gxResolution", RESOLUTIONS[sResolution.getSelectedItemPosition()]);
        provisioner.setConfigValue("farclip", FARCLIP_VALUES[sFarclip.getSelectedItemPosition()]);

        AppUtils.showToast(this, R.string.wow_settings_saved);
        finish();
    }
}
