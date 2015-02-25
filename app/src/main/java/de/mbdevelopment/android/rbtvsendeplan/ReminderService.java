package de.mbdevelopment.android.rbtvsendeplan;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Manages reminders for events. Always start this Service by calling startService() before binding
 * to it by calling onBind() to ensure it stays running in the background, or else the mapping
 * is lost and reminders cannot be cancelled anymore.
 */
public class ReminderService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Flag indicating that a reminder status has changed
     */
    public static final int FLAG_DATA_CHANGED = 1;

    /**
     * Filename for the backup of the list of events with reminders
     */
    public static final String BACKUP_EVENTS_FILENAME = "evens.bak";

    /**
     * Filename for the backup of the recurring events with reminders for all instances
     */
    private static final String BACKUP_RECURRING_FILENAME = "recurring.bak";

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
     * List of the recurringEventIds for which reminders are set for all instances of the event
     */
    private List<String> recurringReminders = new ArrayList<>();

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
     * Offset before the start time of events that is used to schedule reminders in milliseconds
     */
    private int reminderOffset;

    /**
     * Queue used to schedule event lists to be written to internal storage
     */
    protected ArrayBlockingQueue<BackupQueueElement> writeQueue = new ArrayBlockingQueue<>(16);

    /**
     * Thread used to write backup data
     */
    private Thread backupWriterThread;

    /**
     * Flag indicating if the current app version has performed all necessary upgrades
     */
    private boolean versionUpgraded;

    /**
     * Bitmap that holds the Android Wear Fullscreen background image
     */
    Bitmap wear_bg;

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

        protected ArrayBlockingQueue<BackupQueueElement> queue;
        private BackupQueueElement currentElement;
        private final Service service;

        public BackupWriter(ArrayBlockingQueue<BackupQueueElement> queue, Service service) {
            this.queue = queue;
            this.service = service;
        }

        @Override
        public void run() {
            while (true) {
                try{
                    currentElement = queue.take();
                    FileOutputStream fo = openFileOutput(currentElement.filename, MODE_PRIVATE);
                    BufferedOutputStream bo = new BufferedOutputStream(fo);
                    ObjectOutput oo = new ObjectOutputStream(bo);
                    oo.writeObject(currentElement.element);
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

    @Override
    public void onCreate() {
        // Load preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Preferences are in minutes. Multiply by 60.000 to convert to milliseconds
        reminderOffset = 60000 * Integer.parseInt(
                preferences.getString(getString(R.string.pref_reminder_offset_key),
                        getString(R.string.pref_reminder_offset_default)));
        preferences.registerOnSharedPreferenceChangeListener(this);

        // Android Wear fullscreen background image from drawable
        wear_bg = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                R.drawable.bg_wear);

        // Restore counter as not to duplicate any alarm IDs
        alarmCounter = preferences.getInt(PREF_COUNTER, 0);

        // Get upgrade flag
        versionUpgraded = preferences.getBoolean(getString(R.string.pref_version_upgraded), false);

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
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        Intent intent = new Intent(this, ScheduleActivity.class);

        Uri twitch = Uri.parse(ScheduleActivity.TWITCH_URL);
        Intent twitchIntent = new Intent(Intent.ACTION_VIEW, twitch);
        PendingIntent twitchPendingIntent = PendingIntent.getActivity(this, 0, twitchIntent, 0);

        // Android Wear specific notification settings
        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender()
                        .setBackground(wear_bg);

        PendingIntent clickIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(idToEventMap.get(id).getTitle())
                .setContentText(String.format(getString(R.string.reminder_text),
                        sdf.format(idToEventMap.get(id).getStartDate().getTime())))
                .setContentIntent(clickIntent)
                .addAction(R.drawable.ic_play_circle_fill_white_36dp,
                        getString(R.string.notification_open_twitch_action_text),
                        twitchPendingIntent)
                .extend(wearableExtender)
                .setAutoCancel(true);
        if (pref.getBoolean(getString(R.string.pref_vibrate_key), true)) {
            builder.setVibrate(new long[] {0, 500, 500, 500});
        }
        if (pref.getBoolean(getString(R.string.pref_led_key), true)) {
            builder.setLights(Color.RED, 3000, 3000);
        }
        String notificationPref = pref.getString(getString(R.string.pref_notification_ringtone_key),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());
        if (notificationPref.length() > 0) {
            // The string for 'silent' is "" with a length of 0, therefore a sound has to be set for
            // lengths higher than that
            builder.setSound(Uri.parse(notificationPref));
        }
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
        // Do not set more than one reminder per event
        if (idToEventMap.containsKey(event.getId())) return;

        // Add reminder
        Intent alarmIntent = new Intent(this, ReminderService.class);
        alarmIntent.putExtra(EXTRA_ID, event.getId());
        PendingIntent pendingAlarmIntent = PendingIntent.getService(this, alarmCounter,
                alarmIntent, 0);

        eventToIntentMap.put(event.getId(), alarmCounter++);
        idToEventMap.put(event.getId(), event);

        alarmManager.set(AlarmManager.RTC_WAKEUP,
                event.getStartDate().getTimeInMillis() - reminderOffset,
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
        if (eventGroups == null || eventGroups.size() < 1) return;

        // Perform one-time upgrades on application version changes that require it
        if (!versionUpgraded) refreshReminderData(eventGroups);

        boolean hasChanged = false;
        for (int i = 0; i < eventGroups.size(); i++) {
            // Check all events in each group
            for (Event e : eventGroups.get(i).getEvents()) {
                if (idToEventMap.containsKey(e.getId())) {
                    hasChanged = hasChanged || updateEventDate(e);
                }
            }
        }

        // Remove reminders for events that are no longer present in the data
        hasChanged = hasChanged || removeObsoleteReminders(eventGroups);

        // Add reminders for new instances of recurring events on the index
        hasChanged = hasChanged || addNewRecurringInstances(eventGroups);

        // Inform activity if any changes occurred
        if (hasChanged) sendMessage(FLAG_DATA_CHANGED);

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
    private boolean removeObsoleteReminders(SparseArray<EventGroup> eventGroups) {
        if (eventGroups == null) return false;
        boolean hasChanged = false;
        Iterator<Map.Entry<String, Event>> iterator = idToEventMap.entrySet().iterator();
        Map.Entry<String, Event> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            boolean stillExists = false;
            for (int i = 0; i < eventGroups.size(); i++) {
                stillExists = eventGroups.get(i).contains(entry.getValue().getId());
                if (stillExists) break;
            }
            if (!stillExists) {
                // Event does not longer exist in the underlying data set -> remove it
                // Important: Remove from idToEventMap with the iterator first to prevent race a
                // condition! The iterator would fail if removeReminder() removed data from it's
                // underlying set!
                iterator.remove();
                removeReminder(entry.getValue());
                hasChanged = true;
            }
        }

        return hasChanged;
    }

    /**
     * Adds reminders for new instances of recurring events that are listed in the index. New means
     * that the instance does not have a reminder set yet. Existing reminders will not be altered by
     * this function. Past instances will not be added.
     * @param eventGroups The underlying data set
     * @return true if a reminder has been added, false else
     */
    private boolean addNewRecurringInstances(SparseArray<EventGroup> eventGroups) {
        if (eventGroups == null || recurringReminders == null || recurringReminders.isEmpty()) {
            return false;
        }
        Calendar now = Calendar.getInstance();
        Calendar startOffset;
        boolean hasChanged = false;
        for (int i = 0; i < eventGroups.size(); i++) {
            for (Event e : eventGroups.get(i).getEvents()) {
                if (!idToEventMap.containsKey(e.getId())
                        && recurringReminders.contains(e.getRecurringId())) {
                    // Add only future reminders accounting for the offset
                    startOffset = (Calendar) e.getStartDate().clone();
                    startOffset.add(Calendar.MILLISECOND, -1 * reminderOffset);
                    if (!e.isCurrentlyRunning() && startOffset.compareTo(now) == 1) {
                        addReminder(e);
                        hasChanged = true;
                    }
                }
            }
        }

        return hasChanged;
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
        BackupQueueElement eventsBackup = new BackupQueueElement();
        eventsBackup.element = backupMap;
        eventsBackup.filename = BACKUP_EVENTS_FILENAME;
        BackupQueueElement recurringBackup = new BackupQueueElement();
        recurringBackup.element = recurringReminders;
        recurringBackup.filename = BACKUP_RECURRING_FILENAME;
        try {
            writeQueue.put(eventsBackup);
            writeQueue.put(recurringBackup);
        } catch (InterruptedException e) {
            // Stop writing
        }
    }

    /**
     * Tries to read the backup file from internal storage and restore the reminders. Should only be
     * called if the system has been (re)booted, because only in that case the alarms are lost.
     * @param hard Set to true if new alarms should be created, false if only the references should
     *             be restored
     */
    private void restoreBackup(boolean hard) {
        HashMap<Event, Integer> events = readEventsBackup();
        List<String> recurring = readRecurringBackup();

        if (events == null) {
            // No backup loaded
            return;
        }

        // Remove reminders for past events
        Calendar now = Calendar.getInstance();
        Event currentEvent;
        Iterator<Map.Entry<Event, Integer>> iterator = events.entrySet().iterator();
        while (iterator.hasNext()) {
            currentEvent = iterator.next().getKey();
            if (currentEvent.getStartDate().compareTo(now) < 0) {
                iterator.remove();
            }
        }

        if (hard) {
            // Restore backup and create new reminders/alarms.
            for (Map.Entry<Event, Integer> entry : events.entrySet()) {
                addReminder(entry.getKey());
            }
            recurringReminders = recurring;
            //  On hard restore, new IDs are set that need to be stored now
            scheduleBackup();
        } else {
            // Restore backup but do not create new reminders/alarms.
            // Event instances
            for (Map.Entry<Event, Integer> entry : events.entrySet()) {
                idToEventMap.put(entry.getKey().getId(), entry.getKey());
                eventToIntentMap.put(entry.getKey().getId(), entry.getValue());
            }
            // Recurring event list
            recurringReminders = recurring;
        }
    }

    /**
     * Tries to read the event reminder backup
     * @return The HashMap of event reminders or null if none is found
     */
    @SuppressWarnings("unchecked") // Deserializing produces a compiler warning
    private HashMap<Event, Integer> readEventsBackup() {
        HashMap<Event, Integer> events = null;
        try {
            FileInputStream fi = openFileInput(BACKUP_EVENTS_FILENAME);
            BufferedInputStream bi = new BufferedInputStream(fi);
            ObjectInput oi = new ObjectInputStream(bi);
            events = (HashMap<Event, Integer>) oi.readObject();
            oi.close();
        } catch (FileNotFoundException e) {
            // No backup in storage
        } catch(ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }

        return events;
    }

    /**
     * Tries to read the recurring reminders list backup
     * @return The list of recurring reminders or null if none is found
     */
    @SuppressWarnings("unchecked") // Deserializing produces a compiler warning
    private List<String> readRecurringBackup() {
        List<String> recurring = null;
        try {
            FileInputStream fi = openFileInput(BACKUP_RECURRING_FILENAME);
            BufferedInputStream bi = new BufferedInputStream(fi);
            ObjectInput oi = new ObjectInputStream(bi);
            recurring = (List<String>) oi.readObject();
            oi.close();
        } catch (FileNotFoundException e) {
            // No backup in storage
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }

        return recurring;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Listen for reminder offset changes
        if (key.equals(getString(R.string.pref_reminder_offset_key))) {
            onOffsetChange(Integer.parseInt(sharedPreferences.getString(key,
                    getString(R.string.pref_reminder_offset_default))) * 60000); // see onCreate()
        }
    }

    /**
     * Changes the offset of all scheduled reminders to the new value by removing and re-adding the
     * reminders
     * @param newOffset The new offset in milliseconds
     */
    private void onOffsetChange(int newOffset) {
        if (reminderOffset == newOffset) return;

        reminderOffset = newOffset;
        Event[] events = new Event[idToEventMap.size()];
        idToEventMap.values().toArray(events);
        // Reset all reminders to incorporate the new offset
        for (Event event : events) {
            removeReminder(event);
            addReminder(event);
        }
    }

    /**
     * Tests if a reminder is set for all instances of an
     * {@link de.mbdevelopment.android.rbtvsendeplan.Event}
     * @param event The event to be checked
     * @return true if a reminder is set for all instances, false else
     */
    public boolean hasRecurringReminder(Event event) {
        return recurringReminders != null && recurringReminders.contains(event.getRecurringId());
    }

    /**
     * Adds reminders for all instances of a recurring event
     * @param event An instance of the recurring event
     * @param eventList The list of all available events
     */
    public void addRecurringReminder(Event event, List<Event> eventList) {
        Calendar now = Calendar.getInstance();
        Calendar startOffset;
        String recurringId = event.getRecurringId();
        // Add reminders for all instances
        for (Event e : eventList) {
            if (e.getRecurringId() != null && e.getRecurringId().equals(recurringId)) {
                startOffset = (Calendar) e.getStartDate().clone();
                startOffset.add(Calendar.MILLISECOND, -1 * reminderOffset);
                if (!e.isCurrentlyRunning() && startOffset.compareTo(now) == 1) {
                    // Add reminders for future instances only
                    addReminder(e);
                }
            }
        }
        // Add recurringEventId to index
        if (recurringReminders == null) recurringReminders = new ArrayList<>();
        recurringReminders.add(recurringId);

        scheduleBackup();
    }

    /**
     * Deletes reminders for all instances of a recurring event
     * @param event An instance of the recurring event
     */
    public void deleteRecurringReminder(Event event) {
        String recurringId = event.getRecurringId();
        Event[] events = new Event[idToEventMap.size()];
        idToEventMap.values().toArray(events);
        // Delete reminders for all instances
        for (Event e: events) {
            if (e.getRecurringId() != null && e.getRecurringId().equals(recurringId)) {
                removeReminder(e);
            }
        }

        recurringReminders.remove(recurringId);

        scheduleBackup();
    }

    /**
     * Refreshes the reminder data mapping to contain new object versions
     * @param eventGroups New schedule data
     */
    private void refreshReminderData(SparseArray<EventGroup> eventGroups) {
        EventGroup currentGroup;
        for (int i = 0; i < eventGroups.size(); i++) {
            currentGroup = eventGroups.get(i);
            for (Event e : currentGroup.getEvents()) {
                if (idToEventMap.containsKey(e.getId())) {
                    // Replace old event object by new one
                    idToEventMap.put(e.getId(), e);
                }
            }
        }

        // Save state
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(getString(R.string.pref_version_upgraded), true).commit();
        versionUpgraded = true;
    }
}
