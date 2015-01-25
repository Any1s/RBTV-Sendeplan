package tv.rocketbeans.android.rbtvsendeplan;

import java.util.Calendar;
import java.util.List;

/**
 * Data type for a group of {@link Event}s.
 */
public class EventGroup {

    /**
     * Day of all contained events
     */
    private Calendar date;

    /**
     * List of events in this group
     */
    private final List<Event> events;

    public EventGroup(Calendar date, List<Event> events) {
        this.date = date;
        this.events = events;
    }

    public Calendar getDate() {
        return date;
    }

    public List<Event> getEvents() {
        return events;
    }
}
