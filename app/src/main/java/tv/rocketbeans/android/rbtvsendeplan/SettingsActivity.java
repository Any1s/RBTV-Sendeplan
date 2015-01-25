package tv.rocketbeans.android.rbtvsendeplan;

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
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}
