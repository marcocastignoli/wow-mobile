package com.winlator.wowmobile;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.ContainerDetailFragment;
import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.core.AppUtils;

/**
 * Hosts Winlator's container editor for the single WoW container.
 */
public class ContainerSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wow_fragment_activity);
        setSupportActionBar(findViewById(R.id.Toolbar));

        Container container = new WowContainerHelper(this).getContainer();
        if (container == null) {
            AppUtils.showToast(this, R.string.wow_container_not_created_yet);
            finish();
            return;
        }

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.FLFragmentContainer, new ContainerDetailFragment(container.id))
            .commit();
    }
}
