package de.mbdevelopment.android.rbtvsendeplan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.locks.Lock;

/**
 * Service for fetching calendar data from different sources.
 */
public class DataService extends Service {

    /**
     * The base request URL that is used to fetch the calendar
     */
    private static final String BASE_URL = "https://www.googleapis.com/calendar/v3/calendars/h6tfehdpu3jrbcrn9sdju9ohj8@group.calendar.google.com/events";

    /**
     * Filename for the local calendar copy
     */
    public static final String ON_DISK_FILE = "calendar.local";

    /**
     * Intent extra to force the download, ignoring locally stored backups and any settings for
     * periodical refreshing but still honoring the Wifi preference
     */
    public static final String EXTRA_FORCE_DOWNLOAD = "force_download";

    /**
     * Intent extra to request loading the data. This will use backup files if there are any and
     * will otherwise attempt to download the data, honoring the Wifi preference.
     */
    public static final String EXTRA_LOAD_DATA = "load_data";

    /**
     * Use this action to update preferences
     */
    public static final String ACTION_UPDATE_PREFERENCES = "update_prefs";

    /**
     * Broadcast type signaling a loading status update
     */
    public static final String BROADCAST_STATUS_UPDATE = "loading_status";

    /**
     * Intent extra key for broadcast status updates
     */
    public static final String EXTRA_STATUS = "loading_status_key";

    /**
     * Status signaling that loading has started
     */
    public static final int STATUS_LOADING_STARTED = 1;

    /**
     * Status signaling that loading has finished
     */
    public static final int STATUS_LOADING_FINISHED = 2;

    /**
     * Use this as the intent id when calling this service to ensure that it is started only when
     * needed
     */
    public static final int INTENT_ID = 0;

    /**
     * Indicates if the data is currently being loaded
     */
    private boolean isLoading = false;

    /**
     * Application preferences
     */
    private SharedPreferences prefs;

    /**
     * Used to notify listeners of download status
     */
    private LocalBroadcastManager broadcastManager;

    /**
     * Exception that can be thrown if parsing data failed
     */
    public class ParseException extends Exception {}

    /**
     * Writes the event groups to internal storage
     */
    private class FileWriter implements Runnable {

        private final SerializableSparseArray<EventGroup> eventGroups;

        public FileWriter(SerializableSparseArray<EventGroup> eventGroups) {
            this.eventGroups = eventGroups;
        }

        @Override
        public void run() {
            // Write
            Lock lock = null;
            try {
                FileOutputStream fo = openFileOutput(ON_DISK_FILE, Context.MODE_PRIVATE);
                lock = FileLockHolder.getInstance().getWriteLock(ON_DISK_FILE);
                lock.lock();
                BufferedOutputStream bo = new BufferedOutputStream(fo);
                ObjectOutput oo = new ObjectOutputStream(bo);
                oo.writeObject(eventGroups);
                oo.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Attempts to load the event groups from internal storage
      */
    private class FileLoader implements Runnable {

        @SuppressWarnings("unchecked") // Deserializing produces a compiler warning
        @Override
        public void run() {
            // Read
            Lock lock = null;
            try {
                FileInputStream fi = openFileInput(ON_DISK_FILE);
                lock = FileLockHolder.getInstance().getReadLock(ON_DISK_FILE);
                lock.lock();
                BufferedInputStream bi = new BufferedInputStream(fi);
                ObjectInput oi = new ObjectInputStream(bi);
                onBackupRecovered((SerializableSparseArray<EventGroup>) oi.readObject());
                oi.close();
            } catch (FileNotFoundException e) {
                // No backup in storage
                onBackupRecovered(null);
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Downloads the calendar as json via the Google Calendar API, groups the entries by day and
     * passes the result to the {@link de.mbdevelopment.android.rbtvsendeplan.DataHolder}.
     */
    private class CalendarDownloadTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            StringBuilder builder = new StringBuilder();
            HttpClient client = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(URI.create(params[0]));
            try {
                HttpResponse response = client.execute(httpGet);
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode == 200) {
                    HttpEntity entity = response.getEntity();
                    InputStream content = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                } else {
                    publishProgress(getString(R.string.error_download_failed));
                }
            } catch (IOException e) {
                publishProgress(getString(R.string.error_download_failed));
            }
            return builder.toString();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null) {
                for(String v : values) {
                    Toast.makeText(DataService.this, v, Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        protected void onPostExecute(String s) {
            SerializableSparseArray<EventGroup> eventGroups = null;
            try {
                JSONObject json = new JSONObject(s);
                JSONArray events = json.getJSONArray("items");
                eventGroups = groupEvents(events);
            } catch (ParseException | JSONException e) {
                Toast.makeText(DataService.this, getString(R.string.error_data_format),
                        Toast.LENGTH_SHORT).show();
            }

            onLoadFinished();

            if (eventGroups != null) {
                // Update holder
                DataHolder.getInstance().updateEventGroups(eventGroups);

                // Write to storage
                new Thread(new FileWriter(eventGroups)).start();
            }

            stopSelf(); // Work done
        }
    }

    /**
     * Groups JSON entries of events by day and translates them into an internal, more lightweight
     * data structure.
     * @param events Array of events
     * @return An array containing the groups of events
     * @throws org.json.JSONException if there is an unexpected error in the calendar JSON
     * @throws DataService.ParseException if parsing
     * the event data failed
     */
    private SerializableSparseArray<EventGroup> groupEvents (JSONArray events) throws JSONException,
            ParseException {
        SerializableSparseArray<EventGroup> eventGroups = new SerializableSparseArray<>();
        Calendar curDate = null;
        ArrayList<Event> curList = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < events.length(); i++) {
            Calendar eventStartDate = Utils.getCalendarFromJSON(events.getJSONObject(i)
                    .getJSONObject("start"));
            Calendar eventEndDate = Utils.getCalendarFromJSON(events.getJSONObject(i)
                    .getJSONObject("end"));

            if (eventStartDate == null || eventEndDate == null) {
                // Error retrieving the dates, abort
                throw new ParseException();
            }

            if (curDate == null) {
                curDate = eventStartDate;
                curList = new ArrayList<>();
            } else if (!Utils.isSameDay(curDate, eventStartDate)) {
                eventGroups.put(j++, new EventGroup(curDate, curList));
                curDate = eventStartDate;
                curList = new ArrayList<>();
            }
            Pair<Event.Type, String> p = processSummary(events.getJSONObject(i)
                    .getString("summary"));
            String id = events.getJSONObject(i).getString("id");
            String recurringId = parseRecurringId(events.getJSONObject(i));
            if (recurringId == null) {
                // Single time event
                curList.add(new Event((Calendar) eventStartDate.clone()
                        , (Calendar) eventEndDate.clone(), p.second, p.first, id));
            } else {
                // Instance of a recurring event
                curList.add(new Event((Calendar) eventStartDate.clone()
                        , (Calendar) eventEndDate.clone(), p.second, p.first, id, recurringId));
            }
        }
        eventGroups.put(j, new EventGroup(curDate, curList)); // Last group

        return eventGroups;
    }

    /**
     * Parses the recurringEventId for an event in the JSON representation from the Calendar API
     * @param event The JSONObject containing the event data
     * @return The recurringEventId if it is found, null else
     */
    private String parseRecurringId(JSONObject event) {
        String recurringId = null;
        try {
            recurringId = event.getString("recurringEventId");
        } catch (JSONException e) {
            // No recurring event id is found, so this is no instance of a recurring event
        }

        return recurringId;
    }

    /**
     * Parses the summary into type (new, live, rerun) and title.
     * @param summary The 'summary' field from the Google Calendar
     * @return {@link android.util.Pair} of
     * {@link de.mbdevelopment.android.rbtvsendeplan.Event.Type} and title
     */
    private Pair<Event.Type, String> processSummary(String summary) {
        Event.Type type;
        String title;
        if (summary.startsWith("[N] ")) {
            type = Event.Type.NEW;
            title = summary.substring(4);
        } else if (summary.startsWith("[N]")) {
            type = Event.Type.NEW;
            title = summary.substring(3);
        } else if (summary.startsWith("[L] ")) {
            type = Event.Type.LIVE;
            title = summary.substring(4);
        } else if (summary.startsWith("[L]")) {
            type = Event.Type.LIVE;
            title = summary.substring(3);
        } else {
            type = Event.Type.RERUN;
            title = summary;
        }

        return Pair.create(type, title);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Do not allow binding
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean refreshPeriodically = prefs.getBoolean(getString(R.string.pref_refresh_key), true);

        // Update alarm for refreshing
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent alarmIntent = new Intent(getApplicationContext(), DataService.class);
        alarmIntent.putExtra("alarm", true);
        PendingIntent pendingAlarmIntent = PendingIntent.getService(getApplicationContext(),
                INTENT_ID, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (refreshPeriodically) {
            // Set new alarm in case of periodical refreshing
            long refreshPeriod = Long.parseLong(prefs.getString(
                    getString(R.string.pref_refresh_time_key),
                    getString(R.string.pref_refresh_time_default))) * 60000L;

            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + refreshPeriod, pendingAlarmIntent);
        } else {
            // Cancel possibly set alarms
            alarmManager.cancel(pendingAlarmIntent);
        }

        // Shut down if the service was only started to update preferences
        if (intent != null && intent.getAction() != null &&
                intent.getAction().equals(ACTION_UPDATE_PREFERENCES)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // No multiple loads
        if (isLoading) {
            return START_STICKY;
        }

        // Get data from storage
        if (intent != null && intent.hasExtra(EXTRA_LOAD_DATA) &&
                intent.getBooleanExtra(EXTRA_LOAD_DATA, false)) {
            recoverBackup();
            return START_STICKY;
        }

        // Check if the settings allow the download or if it should be forced and stop the service
        // if neither is the case.
        if (!refreshPeriodically &&
                (intent == null ||
                        !intent.hasExtra(EXTRA_FORCE_DOWNLOAD)
                        || !intent.getBooleanExtra(EXTRA_FORCE_DOWNLOAD, false)
                )) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Download
        startDownloadTask();

        return START_STICKY;
    }

    /**
     * Builds the download request and starts a background task to fetch the data. Honors the Wifi
     * preference.
     */
    private void startDownloadTask() {
        // Download if preferences allow it
        if (prefs.getBoolean(getString(R.string.pref_wifi_key), true)) {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (!wifi.isConnected()) {
                Toast.makeText(this, getString(R.string.wifi_not_connected), Toast.LENGTH_LONG)
                        .show();
                onLoadFinished();
                stopSelf(); // Can not continue without preference change
                return;
            }
        }

        setLoading(true);
        broadcast(STATUS_LOADING_STARTED);

        // Get localized time
        Calendar localTime = Calendar.getInstance();

        // Build query URL
        String calendarUrl = BASE_URL;
        calendarUrl += "?singleEvents=true";
        calendarUrl += "&orderBy=startTime";
        calendarUrl += "&timeZone=" + localTime.getTimeZone().getID();
        calendarUrl += "&timeMin=" + buildMinTime((Calendar) localTime.clone());
        calendarUrl += "&timeMax=" + buildMaxTime((Calendar) localTime.clone());
        calendarUrl += "&key=" + Config.GOOGLE_API_KEY;

        // Fetch Google calendar
        new CalendarDownloadTask().execute(calendarUrl);
    }

    /**
     * Builds a request string to fetch events beginning from the day before the input day
     * @param cal Reference day calendar
     * @return Request string
     */
    private String buildMinTime(Calendar cal) {
        // Include day before today
        cal.add(Calendar.DAY_OF_YEAR, -1);

        // Build string
        String res = "";
        res += cal.get(Calendar.YEAR);
        res += "-" + String.format("%02d", cal.get(Calendar.MONTH) + 1); // Calendar counts from 0
        res += "-" + String.format("%02d", cal.get(Calendar.DAY_OF_MONTH));
        res += "T00:00:00.0z";
        return res;
    }

    /**
     * Builds a request string to fetch events up to two weeks ahead from the input day
     * @param cal Reference day calendar
     * @return Request string
     */
    private String buildMaxTime (Calendar cal) {
        // Include up to two weeks ahead of now
        cal.add(Calendar.WEEK_OF_YEAR, 2);

        // Build string
        String res = "";
        res += cal.get(Calendar.YEAR);
        res += "-" + String.format("%02d", cal.get(Calendar.MONTH) + 1); // Calendar counts from 0
        res += "-" + String.format("%02d", cal.get(Calendar.DAY_OF_MONTH));
        res += "T00:00:00.0z";
        return res;
    }

    /**
     * Tries to recover a backup from storage
     */
    private void recoverBackup () {
        // Restore local copy only if no version upgrades are pending
        if(prefs.getBoolean(getString(R.string.pref_version_upgraded), false)) {
            setLoading(true);
            broadcast(STATUS_LOADING_STARTED);
            new Thread(new FileLoader()).start();
        } else {
            onBackupRecovered(null);
        }
    }

    /**
     * Called when a backup recovery has been completed. Stores the recovered data in the
     * {@link de.mbdevelopment.android.rbtvsendeplan.DataHolder} or attempts to download new data
     * if the recovery failed.
     * @param eventGroups Recovery result
     */
    private void onBackupRecovered(SerializableSparseArray<EventGroup> eventGroups) {
        if (eventGroups == null) {
            // Download
            startDownloadTask();
        } else {
            // Give data
            DataHolder.getInstance().updateEventGroups(eventGroups);
            onLoadFinished();

            // Everything done, stop service.
            stopSelf();
        }
    }

    /**
     * Must be called after loading has finished. Wraps up the service and notifies listeners of
     * the finished status.
     */
    private void onLoadFinished() {
        setLoading(false);
        broadcast(STATUS_LOADING_FINISHED);
        OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
    }

    /**
     * Sends a local broadcast through the {@link android.support.v4.content.LocalBroadcastManager}
     * @param type Status type
     */
    private void broadcast(int type) {
        Intent broadcastIntent = new Intent(BROADCAST_STATUS_UPDATE);
        broadcastIntent.putExtra(EXTRA_STATUS, type);
        broadcastManager.sendBroadcast(broadcastIntent);
    }

    /**
     * Sets loading state locally and in shared preferences
     * @param isLoading Loading state
     */
    public void setLoading(boolean isLoading) {
        prefs.edit().putBoolean(getString(R.string.pref_is_loading), isLoading).apply();
        this.isLoading = isLoading;
    }
}
