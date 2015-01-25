package tv.rocketbeans.android.rbtvsendeplan;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
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

    /**
     * View Holder for the list entries, using the Holder Pattern.
     */
    private class EventHolder {
        public TextView dateView;
        public TextView typeView;
        public TextView nameView;
    }

    /**
     * Instantiates new adapter.
     * @param activity This Activity's {@link android.view.LayoutInflater} will be used
     * @param eventGroups List of grouped events
     */
    public ExpandableEventListAdapter(Activity activity, SparseArray<EventGroup> eventGroups) {
        this.eventGroups = eventGroups;
        inflater = activity.getLayoutInflater();
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
            rowView.setTag(eventHolder);
        }

        EventHolder eventHolder = (EventHolder) rowView.getTag();

        Event event = eventGroups.get(groupPosition).getEvents().get(childPosition);
        String indicator = "";

        // Type colors for the whole row
        Resources resources = rowView.getResources();
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_pressed},
                resources.getDrawable(R.color.event_list_selected_background));
        if (event.getType().equals(Event.Type.NEW)) {
            stateList.addState(new int[]{}, resources.getDrawable(R.color.new_background));
            indicator = "[N]";
        } else if (event.getType().equals(Event.Type.LIVE)) {
            stateList.addState(new int[]{}, resources.getDrawable(R.color.live_background));
            indicator = "[L]";
        } else {
            stateList.addState(new int[]{}, resources.getDrawable(R.color.default_background));
        }
        rowView.setBackgroundDrawable(stateList);

        // Indicate the currently running
        if (isCurrentlyRunning(event)) {
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

        return rowView;
    }

    /**
     * Determines if an event is currently running in respect to the device's time
     * @param event Event to be checked
     * @return true if the event has started and has not yet finished, false else
     */
    private boolean isCurrentlyRunning(Event event) {
        Calendar now = Calendar.getInstance();
        return now.compareTo(event.getStartDate()) == 1 && now.compareTo(event.getEndDate()) == -1;
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
        return isCurrentlyRunning(eventGroups.get(groupPosition).getEvents().get(childPosition));
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
