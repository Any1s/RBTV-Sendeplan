package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
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

public class DataFragment extends Fragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * The Fragment's tag to be used by the {@link android.app.FragmentManager}
     */
    public static final String TAG = "data_fragment";

    /**
     * The base request URL that is used to fetch the calendar
     */
    private static final String BASE_URL = "https://www.googleapis.com/calendar/v3/calendars/h6tfehdpu3jrbcrn9sdju9ohj8@group.calendar.google.com/events";

    /**
     * Filename for the local calendar copy
     */
    public static final String ON_DISK_FILE = "calendar.local";

    /**
     * Grouped list of events
     */
    private SerializableSparseArray<EventGroup> eventGroups;

    /**
     * The callbacks to be used on events
     */
    private Callbacks callbacks;

    /**
     * Indicates if the data is currently being loaded
     */
    private boolean isLoading = false;

    /**
     * References the last activity that issued a load instruction
     */
    private Activity activity;

    /**
     * Preferences
     */
    private SharedPreferences preferences;

    /**
     * Indicates if the calendar schould be refreshed periodically
     */
    private boolean refreshPeriodically;

    /**
     * The calendar refresh period in minutes
     */
    private int refreshPeriod;

    /**
     * Nano time of the last refresh
     * @see System#nanoTime()
     */
    private long refreshTimestamp = 0;

    /**
     * Handler for the automatic refresher
     */
    private Handler refreshHandler;

    /**
     * Indicates if the fragment is attached for the first time
     */
    private boolean firstAttach = true;

    /**
     * Used to refresh the calendar data periodically
     */
    private Runnable calendarRefresher = new Runnable() {
        @Override
        public void run() {
            callbacks.refreshCalendarData();
            refreshHandler.postDelayed(calendarRefresher, refreshPeriod * 60000);
        }
    };

    /**
     * Classes using this fragment must implement this interface. It's functions will be called
     * by this class on certain events.
     */
    public interface Callbacks {
        /**
         * Called when the fragment has finished to load the requested data
         * @param eventGroups The data that has been loaded
         */
        public void onDataLoaded(SparseArray<EventGroup> eventGroups);

        /**
         * Called when the periodical refresh is due
         */
        public void refreshCalendarData();
    }

    /**
     * Exception that can be thrown if parsing data failed
     */
    public class ParseException extends Exception {}

    /**
     * Writes the event groups to internal storage
     */
    private class FileWriter implements Runnable {

        @Override
        public void run() {
            if (activity == null) return;

            // Write
            try {
                FileOutputStream fo = activity.openFileOutput(ON_DISK_FILE, Context.MODE_PRIVATE);
                BufferedOutputStream bo = new BufferedOutputStream(fo);
                ObjectOutput oo = new ObjectOutputStream(bo);
                oo.writeObject(eventGroups);
                oo.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Loading has finished
            isLoading = false;
        }
    }

    // Attempts to load the event groups from internal storage
    private class FileLoader implements Runnable {

        @SuppressWarnings("unchecked") // Deserializing produces a compiler warning
        @Override
        public void run() {
            if (activity == null) return;

            // Read
            try {
                FileInputStream fi = activity.openFileInput(ON_DISK_FILE);
                BufferedInputStream bi = new BufferedInputStream(fi);
                ObjectInput oi = new ObjectInputStream(bi);
                eventGroups = (SerializableSparseArray<EventGroup>) oi.readObject();
                oi.close();
            } catch (FileNotFoundException e) {
                // No backup in storage
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }

            if (callbacks != null && eventGroups != null) callbacks.onDataLoaded(eventGroups);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Do not destroy this fragment on configuration changes
        setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (refreshPeriodically) {
            stopRefreshingPeriodically();
        }

        // Unregister
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Starts refreshing the calendar periodically
     * @param delay Refreshing is started after this delay in milliseconds
     */
    private void startRefreshingPeriodically(long delay) {
        refreshHandler.postDelayed(calendarRefresher, delay);
    }

    /**
     * Stops the periodic refreshing of the calendar
     */
    private void stopRefreshingPeriodically() {
        refreshHandler.removeCallbacks(calendarRefresher);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        this.activity = activity;

        try {
            this.callbacks = (Callbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + "must implement the Callbacks interface!");
        }

        if (firstAttach) {
            // The first time this fragment is attached is the first time, the app is started by the
            // user

            firstAttach = false;

            // Get Preferences
            preferences = PreferenceManager.getDefaultSharedPreferences(activity);

            // Restore local copy on first start if no version upgrades are pending
            if(preferences.getBoolean(getString(R.string.pref_version_upgraded), false)) {
                new Thread(new FileLoader()).start();
            }

            // Load configuration
            refreshPeriodically = preferences.getBoolean(getString(R.string.pref_refresh_key), true);
            refreshPeriod = Integer.parseInt(preferences.getString(
                    getString(R.string.pref_refresh_time_key),
                    getString(R.string.pref_refresh_time_default)));

            // Register as change listener
            preferences.registerOnSharedPreferenceChangeListener(this);

            refreshHandler = new Handler();

            // Refresh data periodically
            if (refreshPeriodically) {
                startRefreshingPeriodically(refreshPeriod);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!firstAttach) {
            // The app has been started before
            // If the configured period has elapsed without a reload, do it now
            // 60.000.000.000 nanoseconds are one minute
            if (refreshPeriodically
                    && (refreshTimestamp < (System.nanoTime() - refreshPeriod * 60000000000L))) {
                // Remove current timer and start with a fresh one to prevent multiple reloads
                // within one refreshPeriod
                stopRefreshingPeriodically();
                startRefreshingPeriodically(0);
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
        activity = null;
    }

    public SerializableSparseArray<EventGroup> getEventGroups() {
        return eventGroups;
    }

    /**
     * Loads the calendar data via the Google Calendar API in background
     */
    public void loadCalendarData(Context context) {
        if (isLoading) return;

        // Show indicator
        if (activity != null) {
            activity.findViewById(R.id.download_indicator).setVisibility(View.VISIBLE);
        }

        // Remember loading state
        isLoading = true;

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
        new CalendarDownloadTask(context).execute(calendarUrl);
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
     * Groups JSON entries of events by day and translates them into an internal, more lightweight
     * data structure.
     * @param events Array of events
     * @return An array containing the groups of events
     * @throws org.json.JSONException if there is an unexpected error in the calendar JSON
     * @throws de.mbdevelopment.android.rbtvsendeplan.DataFragment.ParseException if parsing the
     * event data failed
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
        eventGroups.put(j++, new EventGroup(curDate, curList)); // Last group

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
     * @return {@link android.util.Pair} of {@link de.mbdevelopment.android.rbtvsendeplan.Event.Type}
     * and title
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

    /**
     * Downloads the calendar as json via the Google Calendar API, groups the entries by day and
     * passes the result to the callback.
     */
    private class CalendarDownloadTask extends AsyncTask<String, String, String> {

        /**
         * Context used to fetch resources. This context is used rather than the Fragment's own
         * methods because this Fragment can exist without being attached to an activity and so can
         * this AsyncTask. Using the Fragment's methods during an unattached state would cause a
         * NullPointerException.
         */
        private final Context context;

        public CalendarDownloadTask (Context context) {
            this.context = context;
        }

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
                    publishProgress(context.getString(R.string.error_download_failed));
                }
            } catch (IOException e) {
                publishProgress(context.getString(R.string.error_download_failed));
            }
            return builder.toString();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null) {
                for(String v : values) {
                    Toast.makeText(context, v, Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        protected void onPostExecute(String s) {
            try {
                JSONObject json = new JSONObject(s);
                JSONArray events = json.getJSONArray("items");
                setEventGroups(groupEvents(events));
                if (callbacks != null) callbacks.onDataLoaded(eventGroups);
            } catch (ParseException | JSONException e) {
                Toast.makeText(context, context.getString(R.string.error_data_format),
                        Toast.LENGTH_SHORT).show();
            }

            // Hide download indicator
            if (activity != null) {
                activity.findViewById(R.id.download_indicator).setVisibility(View.GONE);
            }

            // Remember the time
            refreshTimestamp = System.nanoTime();

            // Write to storage
            new Thread(new FileWriter()).start();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_refresh_key))) {
            // Start or stop refreshing depending on the new setting
            refreshPeriodically = sharedPreferences.getBoolean(key, true);
            if (refreshPeriodically) {
                startRefreshingPeriodically(0);
            } else {
                stopRefreshingPeriodically();
            }
        } else if (key.equals(getString(R.string.pref_refresh_time_key))) {
            // Restart refreshing with the new period
            if (refreshPeriodically) {
                refreshPeriod = Integer.parseInt(sharedPreferences.getString(key, "5"));
                stopRefreshingPeriodically();
                startRefreshingPeriodically(0);
            }
        }
    }

    private synchronized void setEventGroups(SerializableSparseArray<EventGroup> eventGroups) {
        this.eventGroups = eventGroups;
    }
}
