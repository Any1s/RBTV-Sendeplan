package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Dialog to choose the reminder type
 */
public class AddReminderDialogFragment extends DialogFragment {

    /**
     * Fragment tag
     */
    public static final String TAG = "add_reminder_dialog_fragment";

    /**
     * Argument key for the event
     */
    public static final String ARG_EVENT = "arg_event";

    /**
     * Callbacks to be used on item selection
     */
    private SelectionListener callbacks;

    /**
     * Callback methods that have to be implemented by the attaching {@link android.app.Activity}
     */
    public interface SelectionListener {
        /**
         * Is called if the option to create only one reminder is selected
         * @param event The event for which the reminder is to be set
         */
        public void onSingleSelected(Event event);

        /**
         * Is called if the option to create reminders for all instances of the event is selected
         * @param event The event for which the reminders are to be set
         */
        public void onAllSelected(Event event);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            callbacks = (SelectionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + "must implement SelectionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get parameter
        final Event event = (Event) getArguments().get(ARG_EVENT);
        // Construct dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.reminder_add_dialog_title)
                .setItems(R.array.reminder_add_options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        if (callbacks != null) callbacks.onSingleSelected(event);
                        break;
                    case 1:
                        if (callbacks != null) callbacks.onAllSelected(event);
                        break;
                }
            }
        });

        // Create dialog and return it
        return builder.create();
    }
}
