package tv.rocketbeans.android.rbtvsendeplan;

import java.util.Calendar;

/**
 * Data type holding event information.
 */
public class Event {

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
     * Distinguishes between different airing types
     */
    public enum Type {
        LIVE,
        NEW,
        RERUN
    }

    public Event(Calendar startDate, Calendar endDate, String title, Type type) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
        this.type = type;
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
}
