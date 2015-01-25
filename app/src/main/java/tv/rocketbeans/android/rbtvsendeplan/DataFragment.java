package tv.rocketbeans.android.rbtvsendeplan;

import android.app.Activity;
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
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

public class DataFragment extends Fragment {
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
     * Classes using this fragment must implement this interface. It's functions will be called
     * by this class on certain events.
     */
    public interface Callbacks {
        public void onDataLoaded(SparseArray<EventGroup> eventGroups);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not destroy this fragment on configuration changes
        setRetainInstance(true);
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
        calendarUrl += "&timeMin=" + buildMinTime(localTime);
        calendarUrl += "&key=" + Config.GOOGLE_API_KEY;

        // Fetch Google calendar
        new CalendarDownloadTask().execute(calendarUrl);
    }

    /**
     * Builds a request string to fetch events from the day before the input day to whenever the
     * list of entries ends.
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
            curList.add(new Event((Calendar) eventStartDate.clone(), (Calendar) eventEndDate.clone()
                    , p.second, p.first));
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
                    Log.e(ScheduleActivity.class.toString(), "Failed to download file " + statusCode);
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
}
