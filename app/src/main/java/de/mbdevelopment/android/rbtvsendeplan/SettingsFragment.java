package de.mbdevelopment.android.rbtvsendeplan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;

/**
 * Fragment used to manage the user preferences
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Register self als listener for changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        // Set initial summary state
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference preference = getPreferenceScreen().getPreference(i);

            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); j++) {
                    updatePreferenceSummary(preferenceGroup.getPreference(j));
                }
            } else {
                updatePreferenceSummary(preference);
            }
        }
    }

    @Override
    public void onPause() {
        // Unregister self as listener for changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);

        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreferenceSummary(findPreference(key));
    }

    /**
     * Updates the summary of the preference with the currently corresponding data.
     * @param preference The Preference to be updated
     */
    private void updatePreferenceSummary(Preference preference) {
        if (preference == null) return;

        if (preference.getKey().equals(getString(R.string.pref_reminder_offset_key))) {
            // Reminder offset preference
            NumberPickerPreference offsetPreference = (NumberPickerPreference) preference;
            offsetPreference.setSummary(String.format(
                    getString(R.string.pref_reminder_offset_summary),
                    offsetPreference.getEntry()));
            return;
        }

        if (preference.getKey().equals(getString(R.string.pref_refresh_time_key))) {
            // Refresh timer preference
            ListPreference timerPreference = (ListPreference) preference;
            timerPreference.setSummary(String.format(getString(R.string.pref_refresh_time_summary),
                    timerPreference.getEntry()));
        }
    }
}
