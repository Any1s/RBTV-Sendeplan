package de.mbdevelopment.android.rbtvsendeplan;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;

/**
 * Data type for a group of {@link Event}s.
 */
class EventGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Day of all contained events
     */
    private final Calendar date;

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

    /**
     * Checks if the group contains an event
     * @param id The event's ID
     * @return true if the group contains the event, false else
     */
    public boolean contains(String id) {
        for (Event e : events) {
            if (e.getId().equals(id)) return true;
        }
        return false;
    }
}
