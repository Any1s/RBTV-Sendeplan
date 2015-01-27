package tv.rocketbeans.android.rbtvsendeplan;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Adapter to provide data for an {@link android.widget.ExpandableListView}.
 */
public class ExpandableEventListAdapter extends BaseExpandableListAdapter {
    private final SparseArray<EventGroup> eventGroups;
    private LayoutInflater inflater;
    private Activity activity;
    private ReminderCallbacks callbacks;
    private final Typeface iconFont;

    /**
     * View Holder for the list entries, using the Holder Pattern.
     */
    private class EventHolder {
        public TextView dateView;
        public TextView typeView;
        public TextView nameView;
        public TextView reminderView;
    }

    public interface ReminderCallbacks {
        public boolean hasReminder(Event event);
    }

    /**
     * Instantiates new adapter.
     * @param activity This Activity's {@link android.view.LayoutInflater} will be used
     * @param eventGroups List of grouped events
     */
    public ExpandableEventListAdapter(Activity activity, SparseArray<EventGroup> eventGroups) {
        this.eventGroups = eventGroups;
        inflater = activity.getLayoutInflater();
        iconFont = Typeface.createFromAsset(activity.getAssets(), "androidicons.ttf");
        this.activity = activity;
        callbacks = (ReminderCallbacks) activity;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        View rowView = convertView;

        // View holder pattern
        if (rowView == null) {
            rowView = inflater.inflate(R.layout.event_row, parent, false);
            EventHolder eventHolder = new EventHolder();
            eventHolder.dateView = (TextView) rowView.findViewById(R.id.event_date);
            eventHolder.typeView = (TextView) rowView.findViewById(R.id.event_type);
            eventHolder.nameView = (TextView) rowView.findViewById(R.id.event_name);
            eventHolder.reminderView = (TextView) rowView.findViewById(R.id.event_reminder);
            eventHolder.reminderView.setTypeface(iconFont);
            rowView.setTag(eventHolder);
        }

        EventHolder eventHolder = (EventHolder) rowView.getTag();

        Event event = eventGroups.get(groupPosition).getEvents().get(childPosition);
        String indicator = "";
        boolean currentlyRunning = event.isCurrentlyRunning();

        // Type colors for the whole row
        Resources resources = rowView.getResources();
        StateListDrawable stateList = new StateListDrawable();
        if (event.getType().equals(Event.Type.NEW)) {
            stateList.addState(new int[] {android.R.attr.state_pressed},
                    resources.getDrawable(currentlyRunning ? R.color.running_background_selected
                            : R.color.new_background_selected));
            stateList.addState(new int[]{}, resources.getDrawable(R.color.new_background));
            indicator = "[N]";
        } else if (event.getType().equals(Event.Type.LIVE)) {
            stateList.addState(new int[] {android.R.attr.state_pressed},
                    resources.getDrawable(currentlyRunning ? R.color.running_background_selected
                            : R.color.live_background_selected));
            stateList.addState(new int[]{}, resources.getDrawable(R.color.live_background));
            indicator = "[L]";
        } else {
            stateList.addState(new int[] {android.R.attr.state_pressed},
                    resources.getDrawable(currentlyRunning ? R.color.running_background_selected
                            : R.color.default_background_selected));
            stateList.addState(new int[]{}, resources.getDrawable(R.color.default_background));
        }

        // Commit background colors
        rowView.setBackgroundDrawable(stateList);

        // Indicate the currently running
        if (currentlyRunning) {
            eventHolder.nameView.setBackgroundColor(rowView.getResources()
                    .getColor(R.color.running_background));
        } else {
            // Make sure holder objects previously used for a running entry are reset
            eventHolder.nameView.setBackgroundColor(Color.TRANSPARENT);
        }

        // Set data
        eventHolder.dateView.setText(formatEventDate(event.getStartDate()));
        eventHolder.typeView.setText(indicator);
        eventHolder.nameView.setText(event.getTitle());
        eventHolder.reminderView.setTextColor(getIconColor(event));

        return rowView;
    }

    /**
     * Determines the color of an reminder icon for an event based on set reminders and it's date
     * @param event The event corresponding to the icon in question
     * @return The color id
     */
    private int getIconColor(Event event) {
        if (event.isCurrentlyRunning() || isOver(event)) {
            return activity.getResources().getColor(R.color.event_list_reminder_icon_hidden);
        }
        if (callbacks.hasReminder(event)) {
            return activity.getResources().getColor(R.color.event_list_reminder_icon_enabled);
        }
        return activity.getResources().getColor(R.color.event_list_reminder_icon_disabled);
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
     * Formats a calendar date to "HH:mm".
     * @param date Calendar to be formatted
     * @return Formatted string
     */
    private String formatEventDate(Calendar date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(date.getTime());
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return !isOver(eventGroups.get(groupPosition).getEvents().get(childPosition));
    }

    @Override
    public int getGroupCount() {
        return eventGroups.size();
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                             ViewGroup parent) {
        CheckableLinearLayout rowView = (CheckableLinearLayout) inflater
                .inflate(R.layout.event_group_row, parent, false);
        rowView.setBackgroundColor(isExpanded
                ? parent.getResources().getColor(R.color.event_list_group_expanded_background)
                : parent.getResources().getColor(R.color.event_list_group_collapsed_background));
        TextView indicatorView = (TextView) rowView.findViewById(R.id.event_group_indicator);
        TextView groupView = (TextView) rowView.findViewById(R.id.event_group);
        indicatorView.setText(isExpanded ? "\u25BC" : "\u25B6");
        groupView.setText(formatGroupDate(eventGroups.get(groupPosition).getDate()));

        return rowView;
    }

    /**
     * Formats the group date display string
     * @param date Calendar to be formatted
     * @return Formatted date
     */
    private String formatGroupDate(Calendar date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd.MM.yyyy");
        return sdf.format(date.getTime());
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupPosition * 100 + childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return eventGroups.get(groupPosition).getEvents().get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return eventGroups.get(groupPosition);
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return eventGroups.get(groupPosition).getEvents().size();
    }

    /**
     * Tries to find the day corresponding to the current day
     * @return The group id if a corresponding group is found, -1 else
     */
    public int findTodayGroup() {
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < eventGroups.size(); i++) {
            if (Utils.isSameDay(eventGroups.get(i).getDate(), today)) {
                return i;
            }
        }
        return -1;
    }
}
