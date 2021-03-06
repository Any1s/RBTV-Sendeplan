package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.res.ResourcesCompat;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/**
 * Adapter to provide data for an {@link android.widget.ExpandableListView}.
 */
class ExpandableEventListAdapter extends BaseExpandableListAdapter {
    private final SparseArray<EventGroup> eventGroups;
    private final LayoutInflater inflater;
    private final Activity activity;
    private final ReminderCallbacks callbacks;
    private final Context context;
    private final Drawable emptyDrawable = new ColorDrawable(Color.TRANSPARENT);
    private final Typeface typeFace;
    private final Typeface typeFaceLightItalic;
    private final Typeface typeFaceBold;
    private final int rowPaddingLeft;
    private final int rowPaddingRight;
    private final int rowPaddingTop;
    private final int rowPaddingBottom;

    /**
     * View Holder for the list entries, using the Holder Pattern.
     */
    private class EventHolder {
        public TextView dateView;
        public ImageView typeView;
        public TextView nameView;
        public ImageView reminderView;
    }

    /**
     * View Holder for the group entries, using the Holder Pattern.
     */
    private class GroupHolder {
        public TextView groupView;
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
        typeFace = Typeface.createFromAsset(activity.getApplicationContext().getAssets(),
                "fonts/RobotoCondensed-Light.ttf");
        typeFaceLightItalic = Typeface.createFromAsset(activity.getApplicationContext().getAssets(),
                "fonts/RobotoCondensed-LightItalic.ttf");
        typeFaceBold = Typeface.createFromAsset(activity.getApplicationContext().getAssets(),
                "fonts/RobotoCondensed-Bold.ttf");
        
        this.eventGroups = eventGroups;
        inflater = activity.getLayoutInflater();
        this.activity = activity;
        callbacks = (ReminderCallbacks) activity;
        context = activity.getApplicationContext();

        Resources resources = activity.getResources();
        rowPaddingLeft = resources.getDimensionPixelSize(R.dimen.event_list_padding_left);
        rowPaddingRight = resources.getDimensionPixelSize(R.dimen.event_list_padding_right);
        rowPaddingTop = resources.getDimensionPixelSize(R.dimen.event_list_padding_top);
        rowPaddingBottom = resources.getDimensionPixelSize(R.dimen.event_list_padding_bottom);
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
            eventHolder.typeView = (ImageView) rowView.findViewById(R.id.event_type);
            eventHolder.nameView = (TextView) rowView.findViewById(R.id.event_name);
            eventHolder.reminderView = (ImageView) rowView.findViewById(R.id.event_reminder);
            rowView.setTag(eventHolder);
        }

        EventHolder eventHolder = (EventHolder) rowView.getTag();

        Event event = eventGroups.get(groupPosition).getEvents().get(childPosition);
        Resources resources = rowView.getResources();

        // Commit background
        rowView.setBackgroundDrawable(getBackgroundDrawable(event, resources));
        // Needed for Android < 5.0 to reset padding
        rowView.setPadding(rowPaddingLeft, rowPaddingTop, rowPaddingRight, rowPaddingBottom);

        // Indicate the currently running show with a text
        SpannableString runningIndicator = null;
        if (event.isCurrentlyRunning()) {
            runningIndicator = new SpannableString("   " +
                    resources.getString(R.string.running_indicator));
            runningIndicator.setSpan(new ForegroundColorSpan(
                            resources.getColor(R.color.running_indicator)),
                    3, runningIndicator.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            runningIndicator.setSpan(new StyleSpan(Typeface.BOLD), 0, runningIndicator.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Set data
        eventHolder.dateView.setTypeface(typeFaceBold);
        eventHolder.dateView.setText(formatEventDate(event.getStartDate()));
        eventHolder.typeView.setImageDrawable(getTypeDrawable(event, resources));
        eventHolder.nameView.setTypeface(typeFace);
        eventHolder.nameView.setText(event.getTitle());
        if (runningIndicator != null) {
            eventHolder.nameView.setTypeface(typeFaceLightItalic);
            eventHolder.nameView.append(runningIndicator);
        }
        eventHolder.reminderView.setImageDrawable(getReminderDrawable(event));

        return rowView;
    }

    /**
     * Determines the color of an reminder icon for an event based on set reminders and it's date
     * @param event The event corresponding to the icon in question
     * @return The color id
     */
    private Drawable getReminderDrawable(Event event) {
        if (event.isCurrentlyRunning() || isOver(event)) {
            return emptyDrawable;
        }
        if (callbacks.hasReminder(event)) {
            return ResourcesCompat.getDrawable(activity.getResources(),
                    R.drawable.ic_alarm_on_black_36dp, null);
        }
        return ResourcesCompat.getDrawable(activity.getResources(),
                R.drawable.ic_alarm_add_grey600_36dp, null);
    }

    /**
     * Fetches the appropriate background drawable for the event based on it's type and state.
     * @param event The drawable will be determined based on this event's type and state
     * @param resources Resources to be used to fetch the drawable
     * @return Drawable for the event's background.
     */
    private Drawable getBackgroundDrawable(Event event, Resources resources) {
        if (event == null || resources == null) return null;
        boolean runningNow = event.isCurrentlyRunning();
        if (event.getType().equals(Event.Type.NEW)) {
            if (runningNow) {
                return ResourcesCompat.getDrawable(resources, R.drawable.new_now_background, null);
            } else {
                StateListDrawable stateList = new StateListDrawable();
                stateList.addState(new int[]{android.R.attr.state_pressed},
                        ResourcesCompat.getDrawable(resources, R.color.new_background_selected,
                                null));
                stateList.addState(new int[]{}, ResourcesCompat.getDrawable(resources,
                        R.color.new_background, null));
                return stateList;
            }
        } else if (event.getType().equals(Event.Type.LIVE)) {
            if (runningNow) {
                return ResourcesCompat.getDrawable(resources, R.drawable.live_now_background, null);
            } else {
                StateListDrawable stateList = new StateListDrawable();
                stateList.addState(new int[]{android.R.attr.state_pressed},
                        ResourcesCompat.getDrawable(resources, R.color.live_background_selected,
                                null));
                stateList.addState(new int[]{}, ResourcesCompat.getDrawable(resources,
                        R.color.live_background, null));
                return stateList;
            }
        } else {
            if (runningNow) {
                return ResourcesCompat.getDrawable(resources, R.drawable.rerun_now_background,
                        null);
            } else {
                StateListDrawable stateList = new StateListDrawable();
                stateList.addState(new int[]{android.R.attr.state_pressed},
                        ResourcesCompat.getDrawable(resources, R.color.rerun_background_selected,
                                null));
                stateList.addState(new int[]{}, ResourcesCompat.getDrawable(resources,
                        R.color.rerun_background, null));
                return stateList;
            }
        }
    }

    /**
     * Fetches the appropriate drawable for an event's type.
     * @param event The drawable will be determined based on this event's type
     * @param resources Resources to be used to fetch the drawable
     * @return Drawable for the event's type.
     */
    private Drawable getTypeDrawable(Event event, Resources resources) {
        if (event == null || resources == null) return null;
        if (event.getType().equals(Event.Type.NEW)) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_new, null);
        } else if (event.getType().equals(Event.Type.LIVE)) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_live, null);
        } else {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_rerun, null);
        }
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
        return DateUtils.formatDateTime(context, date.getTimeInMillis(),
                DateUtils.FORMAT_SHOW_TIME);
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
        View rowView = convertView;

        // View Holder pattern
        if (rowView == null) {
            rowView = inflater.inflate(R.layout.event_group_row, parent, false);
            GroupHolder groupHolder = new GroupHolder();
            groupHolder.groupView = (TextView) rowView.findViewById(R.id.event_group);
            rowView.setTag(groupHolder);
        }

        GroupHolder groupHolder = (GroupHolder) rowView.getTag();
        // Set data
        rowView.setBackgroundColor(isExpanded
                ? parent.getResources().getColor(R.color.event_list_group_expanded_background)
                : parent.getResources().getColor(R.color.event_list_group_collapsed_background));
        groupHolder.groupView.setTextColor(isExpanded
                ? parent.getResources().getColor(R.color.event_list_group_text_selected)
                : parent.getResources().getColor(R.color.event_list_group_text));
        groupHolder.groupView.setText(formatGroupDate(eventGroups.get(groupPosition).getDate()));
        groupHolder.groupView.setTypeface(typeFaceBold);

        return rowView;
    }

    /**
     * Formats the group date display string
     * @param date Calendar to be formatted
     * @return Formatted date
     */
    private String formatGroupDate(Calendar date) {
        return DateUtils.formatDateTime(context, date.getTimeInMillis(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY |
                        DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_WEEKDAY);
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
     * Tries to find the currently running event or one close to the current time
     * @return An array of two elements. The first element is the group position or -1 if none is
     * found. The second element s the child position or -1 if none is fond.
     */
    public int[] findCurrentEvent() {
        int[] pos = {-1, -1};
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < eventGroups.size(); i++) {
            if (Utils.isSameDay(eventGroups.get(i).getDate(), today)) {
                pos[0] = i;
                break;
            }
        }

        if (pos[0] != -1) {
            // Day has been found. Look for a running event and return it's position. If no running
            // event is found, return the last event before the current time
            List<Event> currentGroup = eventGroups.get(pos[0]).getEvents();
            Event currentEvent;
            for (int i = 0; i < currentGroup.size(); i++) {
                currentEvent = currentGroup.get(i);
                if (currentEvent.getStartDate().compareTo(today) == 1) {
                    // No running event found since we have reached a future event
                    pos[1] = i - 1;
                    break;
                }
                if ((currentEvent.getStartDate().compareTo(today) == -1
                        || currentEvent.getStartDate().compareTo(today) == 0)
                        && currentEvent.getEndDate().compareTo(today) == 1) {
                    // Running event found
                    pos[1] = i;
                    break;
                }
            }
        }
        return pos;
    }
}
