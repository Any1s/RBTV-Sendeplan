package de.mbdevelopment.android.rbtvsendeplan;

import java.util.Observable;

/**
 * Singleton providing application-wide caching.
 */
public class DataHolder extends Observable {

    /**
     * Grouped list of events
     */
    private SerializableSparseArray<EventGroup> eventGroups;

    // Private constructor. Prevents instantiation from other classes.
    private DataHolder() {}

    /**
     * Implements Bill Pugh's version of the singleton pattern instantiation
     */
    private static class InstanceHolder {
        private static final DataHolder INSTANCE = new DataHolder();
    }

    /**
     * Gets the singleton DataHolder
     * @return The singleton instance
     */
    public static DataHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public SerializableSparseArray<EventGroup> getEventGroups() {
        return eventGroups;
    }

    /**
     * Updates the cache data for the event groups
     * @param eventGroups Cached event groups
     */
    public void updateEventGroups(SerializableSparseArray<EventGroup> eventGroups) {
        this.eventGroups = eventGroups;
        setChanged();
        notifyObservers();
    }
}
