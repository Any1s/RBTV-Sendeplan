package de.mbdevelopment.android.rbtvsendeplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver to start services on device (re)boot in order to restore the alarms and refresh data.
 */
public class DeviceBootedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // Start reminder service
        Intent reminderIntent = new Intent(context, ReminderService.class);
        reminderIntent.putExtra(ReminderService.EXTRA_BOOT, true);
        context.startService(reminderIntent);

        // Start data service
        Intent dataIntent = new Intent(context, DataService.class);
        context.startService(dataIntent);
    }
}
