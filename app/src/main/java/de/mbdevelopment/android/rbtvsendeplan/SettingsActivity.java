package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.os.Bundle;


/**
 * Activity providing access to user preferences
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}
