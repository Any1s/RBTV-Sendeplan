package tv.rocketbeans.android.rbtvsendeplan;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Fragment used to manage the user preferences
 */
public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}
