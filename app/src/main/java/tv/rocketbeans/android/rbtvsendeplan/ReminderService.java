package tv.rocketbeans.android.rbtvsendeplan;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Manages reminders for events. Always start this Service by calling startService() before binding
 * to it by calling onBind() to ensure it stays running in the background, or else the mapping
 * is lost and reminders cannot be cancelled anymore.
 */
public class ReminderService extends Service {

    /**
     * Flag indicating that a reminder status has changed
     */
    public static final int FLAG_DATA_CHANGED = 1;

    /**
     * Filename for the backup of the list of events with reminders
     */
    private static final String BACKUP_FILENAME = "evens.bak";

    /**
     * Identifier string for the event id in {@link Intent} extras
     */
    public static final String EXTRA_ID = "event_id";

    /**
     * Signal to indicate a freshly booted system
     */
    public static final String EXTRA_BOOT = "boot";

    /**
     * The preference name the counter is saved under
     */
    private static final String PREF_COUNTER = "reminder_service_counter";

    /**
     * Indicates if this instance has been started before
     */
    private boolean mRunning = false;

    /**
     * Mapping to link {@link Event#id} and a corresponding reminder alarm Intent
     */
    private Map<String, Integer> eventToIntentMap = new HashMap<>();

    /**
     * Mapping to link {@link Event#id} and the corresponding {@link Event}
     */
    private Map<String, Event> idToEventMap = new HashMap<>();

    /**
     * Binder given to clients on bind
     */
    private final ServiceBinder mBinder = new ServiceBinder();

    /**
     * Alarm manager for setting alarms
     */
    private AlarmManager alarmManager;

    /**
     * Used as alarm ID
     */
    private int alarmCounter;

    /**
     * Messenger used to communicate with the activity
     */
    private Messenger messageHandler;

    /**
     * Queue used to schedule event lists to be written to internal storage
     */
    protected ArrayBlockingQueue<HashMap<Event, Integer>> writeQueue =
            new ArrayBlockingQueue<>(10);

    /**
     * Thread used to write backup data
     */
    private Thread backupWriterThread;

    /**
     * Binder class that grants access to this service. The only method will return a reference to
     * this service with which it's public methods can be accessed.
     */
    public class ServiceBinder extends Binder {

        /**
         * Gets a reference to the service
         * @return Reference to the service
         */
        ReminderService getReminderService() {
            return ReminderService.this;
        }
    }

    /**
     * Writes serializable Map
     */
    private class BackupWriter implements Runnable {

        protected ArrayBlockingQueue<HashMap<Event, Integer>> queue;
        private HashMap<Event, Integer> currentMap;
        private final Service service;

        public BackupWriter(ArrayBlockingQueue<HashMap<Event, Integer>> queue, Service service) {
            this.queue = queue;
            this.service = service;
        }

        @Override
        public void run() {
            while (true) {
                try{
                    currentMap = queue.take();
                    FileOutputStream fo = openFileOutput(BACKUP_FILENAME, MODE_PRIVATE);
                    BufferedOutputStream bo = new BufferedOutputStream(fo);
                    ObjectOutput oo = new ObjectOutputStream(bo);
                    oo.writeObject(currentMap);
                    oo.close();
                    PreferenceManager.getDefaultSharedPreferences(service)
                            .edit().putInt(PREF_COUNTER, alarmCounter).commit();
                } catch (InterruptedException e) {
                    // Do not wait for more data.
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // TODO remove
    private String printMap(HashMap<Event, Integer> map) {
        String ret = "[";
        for (Map.Entry<Event, Integer> entry : map.entrySet()) {
            ret += " (" + entry.getKey().getId() + ", " + entry.getValue() + ")";
        }
        return ret += " ]";
    }

    @Override
    public void onCreate() {
        // Restore counter as not to duplicate any alarm IDs
        alarmCounter = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREF_COUNTER, 0);

        // Start backup writer thread
        BackupWriter backupWriter = new BackupWriter(writeQueue, this);
        backupWriterThread = new Thread(backupWriter);
        backupWriterThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        // Restore alarmCounter
        if (intent != null && intent.hasExtra(EXTRA_BOOT)) {
            // Start fresh counter as there are no used alarm IDs yet
            alarmCounter = 0;
            restoreBackup(true);
        }

        // Restore reminders on service restore
        if(!mRunning) {
            restoreBackup(false);
            addDummyReminder();
        }

        // Get messenger from activity
        if (intent != null && intent.hasExtra(ScheduleActivity.EXTRA_MESSENGER)) {
            Bundle extras = intent.getExtras();
            messageHandler = (Messenger) extras.get(ScheduleActivity.EXTRA_MESSENGER);
        }

        // Catch alarm events
        if (intent != null && intent.hasExtra(EXTRA_ID)) {
            handleAlarm(intent.getStringExtra(EXTRA_ID));
        }

        // Indicate this instance has been started at least once, so a backup should have been
        // loaded
        mRunning = true;

        return START_STICKY;
    }

    /**
     * Handles setting a notification for an event identified by id
     * @param id ID of the event that caused the notification
     */
    private void handleAlarm(String id) {
        // Notification
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        Intent intent = new Intent(this, ScheduleActivity.class);
        PendingIntent clickIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.abc_btn_radio_material) // TODO other icon
                .setContentTitle(idToEventMap.get(id).getTitle())
                .setContentText(getString(R.string.reminder_text))
                .setContentIntent(clickIntent)
                .setAutoCancel(true);
        if (pref.getBoolean(getString(R.string.pref_wifi_key), true)) {
            builder.setVibrate(new long[] {0, 500, 500, 500});
        }
        if (pref.getBoolean(getString(R.string.pref_led_key), true)) {
            builder.setLights(Color.RED, 3000, 3000);
        }
        // TODO Audio notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // There should always be only one show running at a time, so we can use a single ID
        notificationManager.notify(0, builder.build());

        // Remove handled event from mapping and check service termination condition
        eventToIntentMap.remove(id);
        idToEventMap.remove(id);

        // Store backup
        scheduleBackup();

        // Notify activity
        sendMessage(FLAG_DATA_CHANGED);
        if (eventToIntentMap.isEmpty()) stopSelf();
    }

    /**
     * Sends a message to the activity containing one int flag
     * @param key flag
     */
    private void sendMessage(int key) {
        if (messageHandler == null) return;

        Message message = Message.obtain();
        message.arg1 = key;
        try {
            messageHandler.send(message);
        } catch (RemoteException e) {
            // TODO handle?
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        // Save alarm counter
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt(PREF_COUNTER, alarmCounter).commit();

        // Shut down backup writer thread
        if (backupWriterThread != null) {
            backupWriterThread.interrupt();
        }
    }

    /**
     * Adds a reminder for an event
     * @param event The event to be reminded of
     */
    private void addReminder(Event event) {
        Intent alarmIntent = new Intent(this, ReminderService.class);
        alarmIntent.putExtra(EXTRA_ID, event.getId());
        PendingIntent pendingAlarmIntent = PendingIntent.getService(this, alarmCounter,
                alarmIntent, 0);

        eventToIntentMap.put(event.getId(), alarmCounter++);
        idToEventMap.put(event.getId(), event);

        alarmManager.set(AlarmManager.RTC_WAKEUP, event.getStartDate().getTimeInMillis(),
                pendingAlarmIntent);
    }
    private void addDummyReminder() {
        Calendar start = Calendar.getInstance();
        Calendar stop = Calendar.getInstance();
        start.roll(Calendar.SECOND, 10);
        stop.roll(Calendar.MINUTE, 1);
        Event event = new Event(start, stop,"Dummy Event", Event.Type.RERUN, "dummyid");
        Intent alarmIntent = new Intent(this, ReminderService.class);
        alarmIntent.putExtra(EXTRA_ID, event.getId());
        PendingIntent pendingAlarmIntent = PendingIntent.getService(this, alarmCounter,
                alarmIntent, 0);

        eventToIntentMap.put(event.getId(), alarmCounter++);
        idToEventMap.put(event.getId(), event);

        alarmManager.set(AlarmManager.RTC_WAKEUP, event.getStartDate().getTimeInMillis(),
                pendingAlarmIntent);
    }

    /**
     * Removes a reminder for an event
     * @param event The event no longer to be reminded of
     */
    private void removeReminder(Event event) {
        idToEventMap.remove(event.getId());
        if (eventToIntentMap.containsKey(event.getId())) {
            Intent intent = new Intent(this, ReminderService.class);
            PendingIntent cancelIntent = PendingIntent.getService(this,
                    eventToIntentMap.remove(event.getId()), intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.cancel(cancelIntent);
        }
    }

    /**
     * Toggles the reminder state for an event. An event that had a reminder set will have it
     * removed, an event that had no reminder set will have one set now.
     * @param event The event to get it's reminder state toggled
     */
    public void toggleState(Event event) {
        if (idToEventMap.containsKey(event.getId())) {
            removeReminder(event);
        } else {
            addReminder(event);
        }
        scheduleBackup();
    }

    /**
     * Determines if there exists an alarm for an event
     * @param event The event to be checked
     * @return true if an alarm is set, false else
     */
    public boolean hasReminder(Event event) {
        return eventToIntentMap.containsKey(event.getId());
    }

    /**
     * Updates the reminder dates based on the supplied event group data
     * @param eventGroups new data
     */
    public void updateReminderDates(SparseArray<EventGroup> eventGroups) {
        boolean hasChanged = false;
        for (int i = 0; i < eventGroups.size(); i++) {
            // Check all events in each group
            for (Event e : eventGroups.get(i).getEvents()) {
                if (idToEventMap.containsKey(e.getId())) {
                    hasChanged = hasChanged || updateEventDate(e);
                }
            }
        }
        // Inform activity if any changes occurred
        if (hasChanged) sendMessage(FLAG_DATA_CHANGED);

        // Remove reminders for events that are no longer present in the data
        removeObsoleteReminders(eventGroups);
        scheduleBackup();
    }

    /**
     * Updates the reminder for an event identified by it's id if neccessary
     * @param event1 Event to be checked
     * @return true if a reminder has been changed, false else
     */
    private boolean updateEventDate(Event event1) {
        Event event2 = idToEventMap.get(event1.getId());
        if (!hasReminder(event2)) {
            // No reminder has to be updated since none is set
            return false;
        }

        if (event1.getStartDate().compareTo(event2.getStartDate()) != 0
                || event1.getEndDate().compareTo(event2.getEndDate()) != 0) {
            // Event date has changed, the reminder has to be updated
            removeReminder(event2);
            // Only add new reminder, if the start date has not passed
            // TODO notify user of changed start date?
            if (Calendar.getInstance().compareTo(event1.getStartDate()) == -1) {
                addReminder(event1);
            }

            return true;
        }

        return false;
    }

    /**
     * Deletes reminders for events that are no longer in the data set
     * @param eventGroups The underlying data set
     */
    private void removeObsoleteReminders(SparseArray<EventGroup> eventGroups) {
        Iterator<Map.Entry<String, Event>> iterator = idToEventMap.entrySet().iterator();
        Map.Entry<String, Event> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            boolean stillExists = false;
            for (int i = 0; i < eventGroups.size(); i++) {
                stillExists = stillExists || eventGroups.get(i).contains(entry.getValue());
                if (stillExists) break;
            }
            if (!stillExists) {
                // Event does not longer exist in the underlying data set -> remove it
                // Important: Remove from idToEventMap with the iterator first to prevent race a
                // condition! The iterator would fail if removeReminder() removed data from it's
                // underlying set!
                iterator.remove();
                removeReminder(entry.getValue());
            }
        }
    }

    /**
     * Puts a backup copy of the list of currently scheduled events on a queue that is to be written
     * to internal storage
     */
    private void scheduleBackup() {
        HashMap<Event, Integer> backupMap = new HashMap<>();
        for (Event e : idToEventMap.values()) {
            backupMap.put(e, eventToIntentMap.get(e.getId()));
        }
        try {
            writeQueue.put(backupMap);
        } catch (InterruptedException e) {
            // Stop writing
            return;
        }
    }

    /**
     * Tries to read the backup file from internal storage and restore the reminders. Should only be
     * called if the system has been (re)booted, because only in that case the alarms are lost.
     * @param hard Set to true if new alarms should be created, false if only the references should
     *             be restored
     */
    private void restoreBackup(boolean hard) {
        HashMap<Event, Integer> events = null;
        try {
            FileInputStream fi = openFileInput(BACKUP_FILENAME);
            BufferedInputStream bi = new BufferedInputStream(fi);
            ObjectInput oi = new ObjectInputStream(bi);
            events = (HashMap<Event, Integer>) oi.readObject();
            oi.close();
        } catch (FileNotFoundException e) {
            // No backup in storage
        } catch(ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }

        if (events == null) {
            // No backup loaded
            return;
        }

        if (hard) {
            // Restore backup and create new reminders/alarms
            for (Map.Entry<Event, Integer> entry : events.entrySet()) {
                addReminder(entry.getKey());
            }
            //  On hard restore, new IDs are set that need to be stored now
            scheduleBackup();
        } else {
            // Restore backup but do not create new reminders/alarms
            for (Map.Entry<Event, Integer> entry : events.entrySet()) {
                idToEventMap.put(entry.getKey().getId(), entry.getKey());
                eventToIntentMap.put(entry.getKey().getId(), entry.getValue());
            }
        }
    }
}
