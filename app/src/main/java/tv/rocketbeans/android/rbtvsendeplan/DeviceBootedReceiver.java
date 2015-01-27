package tv.rocketbeans.android.rbtvsendeplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver to start the reminder service on device (re)boot in order to restore the alarms.
 */
public class DeviceBootedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, ReminderService.class);
        serviceIntent.putExtra(ReminderService.EXTRA_BOOT, true);
        context.startService(serviceIntent);
    }
}
