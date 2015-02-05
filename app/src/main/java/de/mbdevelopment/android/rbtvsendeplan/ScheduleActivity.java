package de.mbdevelopment.android.rbtvsendeplan;

import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Main activity containing the schedule.
 */
public class ScheduleActivity extends ActionBarActivity implements DataFragment.Callbacks,
        ExpandableListView.OnChildClickListener, AdapterView.OnItemLongClickListener,
        ExpandableEventListAdapter.ReminderCallbacks {

    /**
     * Intent key for the messenger extra
     */
    public static final String EXTRA_MESSENGER = "messenger";

    /**
     * URL to the RocketBeans.TV Twitch channel
     */
    private static final String TWITCH_URL = "http://twitch.tv/rocketbeanstv";

    /**
     * The fragment used to retrieve and store data
     */
    private DataFragment dataFragment;

    /**
     * The expandable list used to display the data
     */
    private ExpandableListView listView;

    /**
     * Connection to the reminder service
     */
    private ReminderConnection reminderConnection = new ReminderConnection();

    /**
     * Reference to the reminder service
     */
    private ReminderService reminderService = null;

    /**
     * Buffers reminder toggle events until the service has been bound
     */
    private List<Event> reminderToggleBuffer = new ArrayList<>();

    /**
     * Handler used to communicate with the reminder service
     */
    private final Handler messageHandler = new MessageHandler();

    /**
     * Preferences
     */
    private SharedPreferences preferences;

    /**
     * Flag used to signal changed calendar data
     */
    private boolean dataChanged = false;

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
    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == ReminderService.FLAG_DATA_CHANGED) {
                updateListView();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        // TODO workaround
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setBackgroundDrawable(
                new ColorDrawable(getResources().getColor(R.color.action_bar_background)));

        // Get Preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Data Fragment
        FragmentManager fm = getFragmentManager();
        dataFragment = (DataFragment) fm.findFragmentByTag(DataFragment.TAG);
        if (dataFragment == null) {
            dataFragment = new DataFragment();
            fm.beginTransaction().add(dataFragment, DataFragment.TAG).commit();
        }

        // Check for cached data
        if (dataFragment.getEventGroups() == null) {
            loadCalendarData(true);
        } else {
            onDataLoaded(dataFragment.getEventGroups());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start service to run autonomously and pass a messenger to receive messages from it
        Intent serviceIntent = new Intent(this, ReminderService.class);
        serviceIntent.putExtra(EXTRA_MESSENGER, new Messenger(messageHandler));
        getApplicationContext().startService(serviceIntent);

        // Bin to the service to use the IBinder interface
        Intent bindIntent = new Intent(this, ReminderService.class);
        getApplicationContext().bindService(bindIntent, new ReminderConnection(), BIND_AUTO_CREATE);
    }

    @Override
    public void onDataLoaded(final SparseArray<EventGroup> eventGroups) {

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
                int p = ((ExpandableEventListAdapter) listView.getExpandableListAdapter())
                        .findTodayGroup();
                if (p != -1) {
                    listView.expandGroup(p);
                    listView.setSelection(p);
                }

                listView.setOnChildClickListener(ScheduleActivity.this);
                listView.setOnItemLongClickListener(ScheduleActivity.this);

                // Update reminder service data
                if (reminderService == null) {
                    dataChanged = true;
                } else {
                    reminderService.updateReminderDates(dataFragment.getEventGroups());
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
                loadCalendarData(false);
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
     * @param quiet True if the no error messages should be displayed
     */
    public void loadCalendarData(boolean quiet) {
        // Check for WiFi constraint preference
        if (preferences.getBoolean(getString(R.string.pref_wifi_key), false)) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (!wifi.isConnected()) {
                if (!quiet) {
                    Toast.makeText(this, getString(R.string.wifi_not_connected), Toast.LENGTH_LONG)
                            .show();
                }
                return;
            }
        }

        // Actually load data
        if (dataFragment != null) {
            dataFragment.loadCalendarData();
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        Event event = dataFragment.getEventGroups().get(groupPosition).getEvents()
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
            Event event = dataFragment.getEventGroups().get(groupPosition).getEvents()
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
    private void toggleReminderState(Event event) {
        // Service has not yet been bound
        if (reminderService == null) {
            reminderToggleBuffer.add(event);
            return;
        }

        // Service is available
        reminderService.toggleState(event);
        updateListView();
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
            reminderService.updateReminderDates(dataFragment.getEventGroups());
        } else if (dataChanged) {
            reminderService.updateReminderDates(dataFragment.getEventGroups());
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
    public void refreshCalendarData() {
        loadCalendarData(true);
    }
}
