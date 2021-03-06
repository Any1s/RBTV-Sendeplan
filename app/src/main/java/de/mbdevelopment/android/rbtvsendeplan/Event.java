package de.mbdevelopment.android.rbtvsendeplan;

import java.io.Serializable;
import java.util.Calendar;

/**
 * Data type holding event information.
 */
public class Event implements Serializable {

    static final long serialVersionUID = 6083523564013247943L; // Backward compatibility

    /**
     * Start date of the event
     */
    private final Calendar startDate;

    /**
     * End date of the event
     */
    private final Calendar endDate;

    /**
     * Name of the event
     */
    private final String title;

    /**
     * Airing type of the event
     */
    private final Type type;

    /**
     * The Google Calendar id
     */
    private final String id;

    /**
     * The Google Calendar id of the corresponding recurring event
     */
    private final String recurringId;

    /**
     * Determines if an event is currently running in respect to the device's time
     * @return true if the event has started and has not yet finished, false else
     */
    public boolean isCurrentlyRunning() {
        Calendar now = Calendar.getInstance();
        return now.compareTo(getStartDate()) == 1 && now.compareTo(getEndDate()) == -1;
    }

    /**
     * Distinguishes between different airing types
     */
    public enum Type {
        LIVE,
        NEW,
        RERUN
    }

    public Event(Calendar startDate, Calendar endDate, String title, Type type, String id) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
        this.type = type;
        this.id = id;
        this.recurringId = null;
    }

    public Event(Calendar startDate, Calendar endDate, String title, Type type, String id
            , String recurringId) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
        this.type = type;
        this.id = id;
        this.recurringId = recurringId;
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public String getTitle() {
        return title;
    }

    public Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getRecurringId() {
        return recurringId;
    }

    public boolean isRecurring() {
        return (recurringId != null);
    }
}
