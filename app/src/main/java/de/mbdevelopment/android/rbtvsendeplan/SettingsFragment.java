package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.RingtonePreference;

/**
 * Fragment used to manage the user preferences
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Context to use for resource operations
     */
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        context = activity.getApplicationContext();
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

        // Register self als listener for changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // Set initial summary state
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference preference = getPreferenceScreen().getPreference(i);

            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); j++) {
                    updatePreferenceSummary(sharedPreferences, preferenceGroup.getPreference(j));
                }
            } else {
                updatePreferenceSummary(sharedPreferences, preference);
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
        updatePreferenceSummary(sharedPreferences, findPreference(key));
    }

    /**
     * Updates the summary of the preference with the currently corresponding data.
     * @param preference The Preference to be updated
     */
    private void updatePreferenceSummary(SharedPreferences sharedPreferences,
                                         Preference preference) {
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
            return;
        }

        if (preference.getKey().equals(getString(R.string.pref_notification_ringtone_key))) {
            // Refresh ringtone name
            RingtonePreference ringtonePreference = (RingtonePreference) preference;
            String ringtoneString = sharedPreferences.getString(preference.getKey(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());
            String name;
            if (ringtoneString.equals("")) {
                // No ringtone
                name = getString(R.string.pref_notification_ringtone_none);
            } else {
                Uri ringtoneUri = Uri.parse(ringtoneString);
                Ringtone ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
                name = ringtone == null ? "" : ringtone.getTitle(context);
            }
            ringtonePreference.setSummary(String.format(
                    getString(R.string.pref_notification_ringtone_summary), name));
        }
    }
}
