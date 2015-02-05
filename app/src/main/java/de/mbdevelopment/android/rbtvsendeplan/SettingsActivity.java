package de.mbdevelopment.android.rbtvsendeplan;

import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;


/**
 * Activity providing access to user preferences
 */
public class SettingsActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // TODO workaround
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setBackgroundDrawable(
                new ColorDrawable(getResources().getColor(R.color.action_bar_background)));

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}
