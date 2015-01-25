package tv.rocketbeans.android.rbtvsendeplan;

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.Toast;

/**
 * Main activity containing the schedule.
 */
public class ScheduleActivity extends ActionBarActivity implements DataFragment.Callbacks,
        ExpandableListView.OnChildClickListener {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        // Data Fragment
        FragmentManager fm = getFragmentManager();
        dataFragment = (DataFragment) fm.findFragmentByTag(DataFragment.TAG);
        if (dataFragment == null) {
            dataFragment = new DataFragment();
            fm.beginTransaction().add(dataFragment, DataFragment.TAG).commit();
        }

        // Check for cached data
        if (dataFragment.getEventGroups() == null) { // TODO also reload after certain time since last time
            loadCalendarData();
        } else {
            onDataLoaded(dataFragment.getEventGroups());
        }
    }

    @Override
    public void onDataLoaded(SparseArray<EventGroup> eventGroups) {
        if (listView == null) {
            listView = (ExpandableListView) findViewById(R.id.listView);
        }

        listView.setAdapter(new ExpandableEventListAdapter(this, eventGroups));

        // Try to set the view to the expanded group containing events on the current day
        int p = ((ExpandableEventListAdapter) listView.getExpandableListAdapter()).findTodayGroup();
        if (p != -1) {
            listView.expandGroup(p);
            listView.setSelection(p);
        }

        listView.setOnChildClickListener(this);
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
                loadCalendarData();
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
     */
    private void loadCalendarData() {
        // Check for WiFi constraint preference
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean(getString(R.string.pref_wifi_key), false)) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (!wifi.isConnected()) {
                Toast.makeText(this, getString(R.string.wifi_not_connected), Toast.LENGTH_LONG)
                        .show();
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
        Uri twitch = Uri.parse(TWITCH_URL);
        Intent intent = new Intent(Intent.ACTION_VIEW, twitch);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
        return true;
    }
}
