package de.mbdevelopment.android.rbtvsendeplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Receiver used to perform one-time operations after app upgrades.
 */
public class UpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only respond to valid actions
        if (!intent.getAction().equals("android.intent.action.MY_PACKAGE_REPLACED")) return;

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());

        // Check if data update frequency is within the current limits and adjust them if not
        int frequency = Integer.parseInt(preferences
                .getString(context.getString(R.string.pref_refresh_time_key), "0"));
        if (frequency < 15) {
            preferences.edit()
                    .putString(context.getString(R.string.pref_refresh_time_key), "15")
                    .apply();
        }
    }
}
