package de.mbdevelopment.android.rbtvsendeplan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.widget.RemoteViews;

public class OneDayScheduleWidgetProvider extends AppWidgetProvider {

    public static final int UPDATE_INTENT_ID = 0;

    /**
     * Use this action in intents to this provider to force a widget update
     */
    public static final String UPDATE_ACTION = "de.mbdevelopment.android.rbtvsendeplan.WIDGET_UPDATE_ACTION";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        // Catch the manual update action
        if (intent.getAction().equals(UPDATE_ACTION)) {
            AppWidgetManager awm = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            for (int appWidgetId : appWidgetIds) {
                awm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.one_day_schedule_widget_list);
            }
        }

        // Let the default implementations handle the rest
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Update all widgets
        for (int appWidgetId : appWidgetIds) {
            // Set up the intent that starts the OneDayScheduleWidgetService, which will provide the
            // views for this collection.
            Intent intent = new Intent(context, OneDayScheduleWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            // Instantiate the RemoteViews object for the widget layout.
            RemoteViews remoteViews = new RemoteViews(context.getPackageName()
                    , R.layout.one_day_schedule_widget);
            // Set up the RemoteViews object to use a RemoteViews adapter.
            // This adapter connects to a RemoteViewsService through the specified intent.
            // This is how the data gets populated.
            remoteViews.setRemoteAdapter(R.id.one_day_schedule_widget_list, intent);

            // The empty view is displayed when the collection has no items.
            remoteViews.setEmptyView(R.id.one_day_schedule_widget_list
                    , R.id.one_day_schedule_widget_empty_view);

            // Set intent template to launch the activity when a list entry is clicked
            Intent activityIntent = new Intent(context, ScheduleActivity.class);
            activityIntent.setData(Uri.parse(activityIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent pendingActivityIntent = PendingIntent.getActivity(context, 0
                    , activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setPendingIntentTemplate(R.id.one_day_schedule_widget_list
                    , pendingActivityIntent);

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    /**
     * Notifies all widgets of changed data
     */
    public static void notifyWidgets(Context context) {
        int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, OneDayScheduleWidgetProvider.class));
        Intent widgetIntent = new Intent(context, OneDayScheduleWidgetProvider.class);
        widgetIntent.setAction(UPDATE_ACTION);
        widgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        PendingIntent pendingWidgetIntent = PendingIntent.getBroadcast(context, UPDATE_INTENT_ID,
                widgetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Schedule the update with two seconds delay to bundle multiple data changes to avoid
        // updating the widget too often in a short time frame.
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 2000,
                pendingWidgetIntent);
    }
}
