package de.mbdevelopment.android.rbtvsendeplan;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Class of helper functions.
 */
class Utils {

    /**
     * Parses the date from a JSON representation of a calendar entry
     * @param event Calendar entry
     * @return Calendar set to the time of the event
     */
    public static Calendar getCalendarFromJSON(JSONObject event) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        Calendar ret = Calendar.getInstance();
        try {
            ret.setTime(sdf.parse(event.getString("dateTime")));
        } catch (Exception e) {
            // Date could not be read
            return null;
        }

        return ret;
    }

    /**
     * Determines if two calendar dates are on the same day
     * @param a First calendar
     * @param b Second calendar
     * @return true if both calendars are on the same day, false else
     */
    public static boolean isSameDay(Calendar a, Calendar b) {
        return !(a == null || b == null) &&
                a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
                a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);

    }
}
