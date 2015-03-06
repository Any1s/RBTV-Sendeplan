package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Main activity containing the schedule.
 */
public class ScheduleActivity extends Activity implements ExpandableListView.OnChildClickListener,
        AdapterView.OnItemLongClickListener, ExpandableEventListAdapter.ReminderCallbacks,
        AddReminderDialogFragment.SelectionListener, DeleteReminderDialogFragment.SelectionListener,
        Observer {

    /**
     * Intent key for the messenger extra
     */
    public static final String EXTRA_MESSENGER = "messenger";

    /**
     * URL to the RocketBeans.TV Twitch channel
     */
    public static final String TWITCH_URL = "http://twitch.tv/rocketbeanstv";

    /**
     * The expandable list used to display the data
     */
    private ExpandableListView listView;

    /**
     * Reference to the reminder service
     */
    private ReminderService reminderService = null;

    /**
     * Buffers reminder toggle events until the service has been bound
     */
    private final List<Event> reminderToggleBuffer = new ArrayList<>();

    /**
     * Handler used to communicate with the reminder service
     */
    private Handler messageHandler;

    /**
     * Preferences
     */
    private SharedPreferences preferences;

    /**
     * Flag used to signal changed calendar data
     */
    private boolean dataChanged = false;

    /**
     * Data source
     */
    private DataHolder dataHolder;

    /**
     * Broadcast receiver for status messages from services
     */
    private BroadcastReceiver broadcastReceiver;

    /**
     * Connection to the reminder service
     */
    private class ReminderConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ReminderService.ServiceBinder binder = (ReminderService.ServiceBinder) service;
            reminderService = binder.getReminderService();
            onBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            reminderService = null;
        }
    }

    /**
     * Message handler class used to communicate with the reminder service
     */
    private static class MessageHandler extends Handler {
        private final WeakReference<ScheduleActivity> mTarget;

        public MessageHandler (ScheduleActivity target) {
            mTarget = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            ScheduleActivity target = mTarget.get();
            if (target != null && msg.arg1 == ReminderService.FLAG_DATA_CHANGED) {
                target.updateListView();
                OneDayScheduleWidgetProvider.notifyWidgets(target.getApplicationContext());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        // Set up handler for this instance
        messageHandler = new MessageHandler(this);

        // Get Preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Broadcast receiver
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int type = intent.getIntExtra(DataService.EXTRA_STATUS, 0);
                switch (type) {
                    case DataService.STATUS_LOADING_STARTED:
                        findViewById(R.id.download_indicator).setVisibility(View.VISIBLE);
                        break;
                    case DataService.STATUS_LOADING_FINISHED:
                        findViewById(R.id.download_indicator).setVisibility(View.GONE);
                        break;
                }
            }
        };

        // Data source
        dataHolder = DataHolder.getInstance();
        // Observe data source
        dataHolder.addObserver(this);

        if (!preferences.getBoolean(getString(R.string.pref_version_upgraded), false)) {
            // Version update
            loadCalendarData(true);
        } else if (dataHolder.getEventGroups() == null) {
            // Check for cached data
            loadCalendarData(false);
        } else {
            // Do not notify
            onDataLoaded(dataHolder.getEventGroups());
        }
    }

    @Override
    protected void onDestroy() {
        dataHolder.deleteObserver(this);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                new IntentFilter(DataService.BROADCAST_STATUS_UPDATE));

        // Check if loading indicator is still needed and hide otherwise
        if (!preferences.getBoolean(getString(R.string.pref_is_loading), false)) {
            findViewById(R.id.download_indicator).setVisibility(View.GONE);
        }

        // Start service to run autonomously and pass a messenger to receive messages from it
        Intent serviceIntent = new Intent(this, ReminderService.class);
        serviceIntent.putExtra(EXTRA_MESSENGER, new Messenger(messageHandler));
        getApplicationContext().startService(serviceIntent);

        // Bin to the service to use the IBinder interface
        Intent bindIntent = new Intent(this, ReminderService.class);
        getApplicationContext().bindService(bindIntent, new ReminderConnection(), BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        super.onStop();
    }

    /**
     * Updates activity components with new data
     * @param eventGroups The data that has been loaded
     */
    private void onDataLoaded(final SparseArray<EventGroup> eventGroups)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // ListView initialization
                if (listView == null) {
                    listView = (ExpandableListView) findViewById(R.id.listView);
                }

                listView.setAdapter(
                        new ExpandableEventListAdapter(ScheduleActivity.this, eventGroups));

                // Try to set the view to the expanded group containing events on the current day
                int[] p = ((ExpandableEventListAdapter) listView.getExpandableListAdapter())
                        .findCurrentEvent();
                if (p[0] != -1) {
                    listView.expandGroup(p[0]);
                    if (p[1] != -1) {
                        listView.setSelectedChild(p[0], p[1], true);
                    } else {
                        listView.setSelection(p[0]);
                    }
                }

                listView.setOnChildClickListener(ScheduleActivity.this);
                listView.setOnItemLongClickListener(ScheduleActivity.this);

                // Update reminder service data
                if (reminderService == null) {
                    dataChanged = true;
                } else {
                    reminderService.updateReminderDates(dataHolder.getEventGroups());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_schedule, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_reload:
                loadCalendarData(true);
                break;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Loads calendar data if all constraints are met
     * @param force True if downloading new data should be forced, false else
     */
    private void loadCalendarData(boolean force) {
        Intent dataIntent = new Intent(getApplicationContext(), DataService.class);
        if (force) {
            dataIntent.putExtra(DataService.EXTRA_FORCE_DOWNLOAD, true);
        } else {
            dataIntent.putExtra(DataService.EXTRA_LOAD_DATA, true);
        }
        startService(dataIntent);
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        Event event = dataHolder.getEventGroups().get(groupPosition).getEvents()
                .get(childPosition);
        if (event.isCurrentlyRunning()) {
            openTwitchChannel();
            return true;
        }

        return false;
    }

    /**
     * Attempts to open the rocketbeanstv twitch channel url
     */
    private void openTwitchChannel() {
        Uri twitch = Uri.parse(TWITCH_URL);
        Intent intent = new Intent(Intent.ACTION_VIEW, twitch);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position,
                                   long id) {
        /*
        Long clicked items are either the running event, in which case we open twitch, or a future
        event for which we will toggle it's reminder state.
         */
        long packedPosition =
                ((ExpandableListView) adapterView).getExpandableListPosition(position);
        if (ExpandableListView.getPackedPositionType(packedPosition) ==
                ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            int groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition);
            int childPosition = ExpandableListView.getPackedPositionChild(packedPosition);
            Event event = dataHolder.getEventGroups().get(groupPosition).getEvents()
                    .get(childPosition);
            // The currently running event is selectable, so it has to be filtered here as well as
            // reminders that would be before the current time
            int offsetMinutes = Integer.parseInt(preferences.getString(
                    getString(R.string.pref_reminder_offset_key),
                    getString(R.string.pref_reminder_offset_default)));
            Calendar now = Calendar.getInstance();
            Calendar startOffset = (Calendar) event.getStartDate().clone();
            startOffset.add(Calendar.MINUTE, -1 * offsetMinutes);
            if(!event.isCurrentlyRunning()) {
                if (startOffset.compareTo(now) == 1) {
                    toggleReminderState(event);
                } else {
                    Toast.makeText(this, getString(R.string.error_reminder_before_now),
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                openTwitchChannel();
            }
            return true;
        }
        return false;
    }

    /**
     * Changes the reminder state of an event from "remind" to "do not remind" or vice versa
     * @param event The event to get it's state toggled
     */
    private void toggleReminderState(final Event event) {
        // Service has not yet been bound
        if (reminderService == null) {
            // Schedule only single time events to be toggled, because they do not need a dialog to
            // be shown. Recurring events are simply ignored and the user has to retry when the
            // service is bound. This situation should almost never occur in the real world so we
            // will the allow this slight hit on the usability.
            if (!event.isRecurring()) reminderToggleBuffer.add(event);
            return;
        }

        // Service is available
        if (event.isRecurring()) {
            // Recurring events can be toggled instance-wise or as a whole. Present the user with a
            // choice. If the current state is "no reminder" the user can choose between a reminder
            // for this instance only and reminders for all instances. If a reminder is set for all
            // instances, all will be removed. If the reminder is set only instance-wise, only the
            // one instance is removed.
            if (reminderService.hasRecurringReminder(event)) {
                DeleteReminderDialogFragment dialogFragment = new DeleteReminderDialogFragment();
                Bundle args = new Bundle();
                args.putSerializable(DeleteReminderDialogFragment.ARG_EVENT, event);
                dialogFragment.setArguments(args);
                dialogFragment.show(getFragmentManager(), DeleteReminderDialogFragment.TAG);
            } else if (reminderService.hasReminder(event)) {
                // Toggle only this instance
                reminderService.toggleState(event);
                updateListView();
                OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
            } else {
                AddReminderDialogFragment dialogFragment = new AddReminderDialogFragment();
                Bundle args = new Bundle();
                args.putSerializable(AddReminderDialogFragment.ARG_EVENT, event);
                dialogFragment.setArguments(args);
                dialogFragment.show(getFragmentManager(), AddReminderDialogFragment.TAG);
            }
        } else {
            // Single time events can be toggled directly
            reminderService.toggleState(event);
            updateListView();
            OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
        }
    }

    @Override
    public void onSingleSelected(Event event) {
        reminderService.toggleState(event);

        updateListView();
        OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
    }

    @Override
    public void onAllSelected(Event event) {
        // Compile list of all available events
        SerializableSparseArray<EventGroup> groupList = dataHolder.getEventGroups();
        List<Event> eventList = new ArrayList<>();
        for (int i = 0; i < groupList.size(); i++) {
            eventList.addAll(groupList.get(i).getEvents());
        }

        // Add reminders
        reminderService.addRecurringReminder(event, eventList);

        updateListView();
        OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
    }

    @Override
    public void onDeletionConfirmed(Event event) {
        reminderService.deleteRecurringReminder(event);

        updateListView();
        OneDayScheduleWidgetProvider.notifyWidgets(getApplicationContext());
    }

    @Override
    public void onDeletionCancelled() {
        // Do nothing...
    }

    /**
     * Is called after a service has been bound
     */
    private void onBind() {
        // If there are buffered reminder state toggle events, handle them now
        if (!reminderToggleBuffer.isEmpty()) {
            for (Event e : reminderToggleBuffer) {
                reminderService.toggleState(e);
            }
            // Update if data has changed
            reminderService.updateReminderDates(dataHolder.getEventGroups());
        } else if (dataChanged) {
            reminderService.updateReminderDates(dataHolder.getEventGroups());
            dataChanged = false;
        }
        updateListView();
    }

    @Override
    public boolean hasReminder(Event event) {
        if (reminderService != null) {
            return reminderService.hasReminder(event);
        } else {
            dataChanged = true;
        }
        return false;
    }

    /**
     * Updates the list view data to the current version
     */
    private void updateListView() {
        if (listView != null) {
            ((ExpandableEventListAdapter) listView.getExpandableListAdapter())
                    .notifyDataSetInvalidated();
        }
    }

    @Override
    public void update(Observable observable, Object data) {
        if (observable instanceof DataHolder) {
            onDataLoaded(((DataHolder) observable).getEventGroups());
        }
    }
}
