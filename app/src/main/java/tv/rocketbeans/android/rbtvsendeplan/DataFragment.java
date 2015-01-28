package tv.rocketbeans.android.rbtvsendeplan;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final String BASE_URL = "https://www.googleapis.com/calendar/v3/calendars/h6tfehdpu3jrbcrn9sdju9ohj8%40group.calendar.google.com/events";

    /**
     * Grouped list of events
     */
    private SparseArray<EventGroup> eventGroups;

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
            Log.e(TAG, "Activities using this fragment must implement it's Callbacks interface!");
        }

        if (firstAttach) {
            firstAttach = false;
            // Get Preferences
            preferences = PreferenceManager.getDefaultSharedPreferences(activity);

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
    public void onDetach() {
        super.onDetach();
        callbacks = null;
        activity = null;
    }

    public SparseArray<EventGroup> getEventGroups() {
        return eventGroups;
    }

    /**
     * Loads the calendar data via the Google Calendar API in background
     */
    public void loadCalendarData() {
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
        new CalendarDownloadTask().execute(calendarUrl);
    }

    /**
     * Builds a request string to fetch events beginning from the day before the input day
     * @param cal Reference day calendar
     * @return Request string
     */
    private String buildMinTime(Calendar cal) {
        // Include day before today
        cal.roll(Calendar.DAY_OF_MONTH, -1);

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
        cal.roll(Calendar.WEEK_OF_YEAR, 2);

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
     * @throws JSONException
     */
    private SparseArray<EventGroup> groupEvents (JSONArray events) throws JSONException {
        SparseArray<EventGroup> eventGroups = new SparseArray<>();
        Calendar curDate = null;
        ArrayList<Event> curList = null;
        int j = 0;
        for (int i = 0; i < events.length(); i++) {
            Calendar eventStartDate = Utils.getCalendarFromJSON(events.getJSONObject(i)
                    .getJSONObject("start"));
            Calendar eventEndDate = Utils.getCalendarFromJSON(events.getJSONObject(i)
                    .getJSONObject("end"));
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
            curList.add(new Event((Calendar) eventStartDate.clone(), (Calendar) eventEndDate.clone()
                    , p.second, p.first, id));
        }
        eventGroups.put(j++, new EventGroup(curDate, curList)); // Last group

        return eventGroups;
    }

    /**
     * Parses the summary into type (new, live, rerun) and title.
     * @param summary The 'summary' field from the Google Calendar
     * @return {@link android.util.Pair} of {@link tv.rocketbeans.android.rbtvsendeplan.Event.Type}
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
    private class CalendarDownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            StringBuilder builder = new StringBuilder();
            HttpClient client = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(params[0]);
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
                    // TODO display download failed message?
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return builder.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            try {
                JSONObject json = new JSONObject(s);
                JSONArray events = json.getJSONArray("items");
                eventGroups = groupEvents(events);
                if (callbacks != null) callbacks.onDataLoaded(eventGroups);
            } catch (JSONException e) {
                // TODO
                e.printStackTrace();
            }

            // Hide download indicator
            if (activity != null) {
                activity.findViewById(R.id.download_indicator).setVisibility(View.GONE);
            }

            // Loading has finished
            isLoading = false;
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
}
