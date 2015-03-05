package de.mbdevelopment.android.rbtvsendeplan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

public class OneDayScheduleWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new OneDayScheduleRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class OneDayScheduleRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private List<Event> eventList;
    List<String> reminderList;
    private Context context;
    private int appwidgetId;

    public OneDayScheduleRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        appwidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID
                , AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public void onCreate() {
        // Nothing to do here, since heavy lifting has to be deferred to onDataSetChanged()
    }

    @Override
    public void onDestroy() {
        // Nothing to clean up here
    }

    @Override
    public int getCount() {
        return eventList == null ? 0 : eventList.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (eventList == null || position >= eventList.size()) {
            return null;
        }
        Event event = eventList.get(position);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        RemoteViews remoteViews = new RemoteViews(context.getPackageName()
                ,R.layout.widget_event_row);
        remoteViews.setTextViewText(R.id.widget_event_date
                ,sdf.format(event.getStartDate().getTime()));

        // Set background color and show type symbol
        int backgroundColor = R.color.default_background;
        int typeSymbol;
        if (event.getType() == Event.Type.NEW) {
            backgroundColor = R.color.new_background;
            typeSymbol = R.drawable.ic_new;
        } else if (event.getType() == Event.Type.LIVE) {
            backgroundColor = R.color.live_background;
            typeSymbol = R.drawable.ic_live;
        } else {
            typeSymbol = R.drawable.ic_rerun;
        }

        remoteViews.setImageViewResource(R.id.widget_event_type, typeSymbol);
        remoteViews.setInt(R.id.widget_event_row, "setBackgroundResource", backgroundColor);
        remoteViews.setImageViewResource(R.id.widget_event_reminder, getReminderResource(event));

        // Set title and indicate the currently running
        if (event.isCurrentlyRunning()) {
            SpannableString runningIndicator = new SpannableString("   " +
                    context.getResources().getString(R.string.running_indicator));
            runningIndicator.setSpan(new ForegroundColorSpan(
                            context.getResources().getColor(R.color.running_indicator)),
                    3, runningIndicator.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            runningIndicator.setSpan(new StyleSpan(Typeface.BOLD), 0, runningIndicator.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            remoteViews.setTextViewText(R.id.widget_event_name
                    , TextUtils.concat(event.getTitle(), runningIndicator));
        } else {
            remoteViews.setTextViewText(R.id.widget_event_name, event.getTitle());
        }

        // Set on click intent for this item
        Intent fillInIntent = new Intent();
        remoteViews.setOnClickFillInIntent(R.id.widget_event_name, fillInIntent);

        return remoteViews;
    }

    @Override
    public RemoteViews getLoadingView() {
        // Returning null uses the default loading view.
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @SuppressWarnings("unchecked") // Deserializing produces a compiler warning
    @Override
    public void onDataSetChanged() {
        // Doing heavy lifting is allowed here.
        // Get data from storage
        SerializableSparseArray<EventGroup> eventGroups = null;
        HashMap<Event, Integer> reminders = null;
        Lock eventLock = null;
        Lock reminderLock = null;
        try {
            // Schedule
            FileInputStream fi = context.openFileInput(DataService.ON_DISK_FILE);
            eventLock = FileLockHolder.getInstance().getReadLock(DataService.ON_DISK_FILE);
            eventLock.lock();
            BufferedInputStream bi = new BufferedInputStream(fi);
            ObjectInput oi = new ObjectInputStream(bi);
            eventGroups = (SerializableSparseArray<EventGroup>) oi.readObject();
            oi.close();

            // Reminders
            fi = context.openFileInput(ReminderService.BACKUP_EVENTS_FILENAME);
            reminderLock = FileLockHolder.getInstance().getReadLock(ReminderService.BACKUP_EVENTS_FILENAME);
            reminderLock.lock();
            bi = new BufferedInputStream(fi);
            oi = new ObjectInputStream(bi);
            reminders = (HashMap<Event, Integer>) oi.readObject();
            oi.close();

        } catch (FileNotFoundException e) {
            // No backup yet means no data yet
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        } finally {
            if (eventLock != null) {
                eventLock.unlock();
            }
            if (reminderLock != null) {
                reminderLock.unlock();
            }
        }

        if (eventGroups != null) {
            // Find shows to display. Display the current show, two before and 7 after. Maximum
            // Time difference is +/- one day.
            eventList = getCurrentEventList(eventGroups);
            // Set update intent to the end of the currently running show if one is found
            Event curEvent = findCurrentEvent(eventGroups);
            if (curEvent != null) {
                Intent updateIntent = new Intent(context, OneDayScheduleWidgetProvider.class);
                updateIntent.setAction(OneDayScheduleWidgetProvider.UPDATE_ACTION);
                int[] idExtra = {appwidgetId};
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, idExtra);
                PendingIntent pendingUpdateintent = PendingIntent.getBroadcast(context
                        , OneDayScheduleWidgetProvider.UPDATE_INTENT_ID
                        , updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                // Schedule the update one second after the end of the currently running show and do
                // not wake up the device by using RTC instead of RTC_WAKEUP.
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                am.set(AlarmManager.RTC
                        , curEvent.getEndDate().getTimeInMillis() + 1000
                        , pendingUpdateintent);
            }
        }
        
        if (reminders != null) {
            reminderList = new ArrayList<>(reminders.size());
            for (Event e : reminders.keySet()) {
                reminderList.add(e.getId());
            }
        }
    }

    /**
     * Assembles the list of events depending on the time of the call
     * @param eventGroups The grouped events the list will be based on
     * @return The complete list of events to be displayed by the widget
     */
    private List<Event> getCurrentEventList(SerializableSparseArray<EventGroup> eventGroups) {
        // Generate temporary list that is easier to handle
        List<Event> tmpList = new ArrayList<>(eventGroups.size() * 10);
        for (int i = 0; i < eventGroups.size(); i++) {
            tmpList.addAll(eventGroups.get(i).getEvents());
        }

        // Find index of currently running show
        int index = 0;
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < tmpList.size(); i++) {
            if ((tmpList.get(i).getStartDate().compareTo(now) <= 0
                    && tmpList.get(i).getEndDate().compareTo(now) > 0)
                    || tmpList.get(i).getStartDate().compareTo(now) > 0) {
                // Event is currently running or it is the first event to be aired next
                index = i;
                break;
            }
        }
        // Generate event list
        List<Event> returnList = new ArrayList<>(10);
        // Start by adding up to two previous events
        // Find start index and do not go below 0
        int start = 0;
        for (int i = index; i >= 0 && i >= index - 2; i--) {
            start = i;
        }
        // Add the events between the calculated start index and the running event's index
        for (int i = start; i < index; i++) {
            returnList.add(tmpList.get(i));
        }
        // Add current event
        returnList.add(tmpList.get(index));
        // Add up to 7 following events, maximum time difference is one day ahead
        Calendar oneDayAhead = (Calendar) now.clone();
        oneDayAhead.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = index + 1; i < tmpList.size() - 1 && i < index + 8; i++) {
            if (tmpList.get(i).getStartDate().compareTo(oneDayAhead) >= 0) {
                break;
            }
            returnList.add(tmpList.get(i));
        }

        return returnList;
    }

    /**
     * Determines if there is a reminder set for an event
     * @param e The event to be checked
     * @return true if there is a reminder set, false else
     */
    private boolean hasReminder(Event e) {
        return reminderList != null && reminderList.contains(e.getId());
    }

    /**
     * Determines the appropriate resource id for the reminder icon
     * @param event The event to get the reminder icon for
     * @return Resource id
     */
    private int getReminderResource(Event event) {
        if (event.isCurrentlyRunning() || isOver(event)) {
            return Color.TRANSPARENT;
        }
        if (hasReminder(event)) {
            return R.drawable.ic_alarm_on_black_36dp;
        }
        return R.drawable.ic_alarm_add_grey600_36dp;
    }

    /**
     * Determines if an event is over
     * @param event Event to be checked
     * @return true if the event has ended, false else
     */
    private boolean isOver(Event event) {
        Calendar now = Calendar.getInstance();
        return now.compareTo(event.getEndDate()) == 1;
    }

    /**
     * Tries to find the currently running event
     * @param eventGroups The grouped events the search will be based on
     * @return The currently running event if one is found, null else
     */
    private Event findCurrentEvent(SerializableSparseArray<EventGroup> eventGroups) {
        EventGroup curGroup;
        for (int i = 0; i < eventGroups.size(); i++) {
            curGroup = eventGroups.get(i);
            for (Event e : curGroup.getEvents()) {
                if (e.isCurrentlyRunning()) {
                    return e;
                }
            }
        }
        return null;
    }

}