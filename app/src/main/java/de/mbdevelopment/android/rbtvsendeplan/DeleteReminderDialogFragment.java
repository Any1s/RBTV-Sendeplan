package de.mbdevelopment.android.rbtvsendeplan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Dialog to confirm the deletion of a recurring reminder
 */
public class DeleteReminderDialogFragment extends DialogFragment {

    /**
     * Fragment tag
     */
    public static final String TAG = "delete_reminder_dialog_fragment";

    /**
     * Argument key for the event
     */
    public static final String ARG_EVENT = "arg_event";

    /**
     * Callbacks to be used on option selection
     */
    private SelectionListener callbacks;

    /**
     * Callback methods that have to be implemented by the attaching {@link android.app.Activity}
     */
    public interface SelectionListener {
        /**
         * Is called if the deletion of a recurring reminder has been confirmed by the user
         * @param event The selected event holding an recurringEventId
         */
        public void onDeletionConfirmed(Event event);

        /**
         * Is called if the deletion of a recurring reminder has been cancelled by the user
         */
        public void onDeletionCancelled();
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
        builder.setMessage(R.string.reminder_remove_dialog_message)
                .setPositiveButton(R.string.reminder_remove_dialog_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (callbacks != null) callbacks.onDeletionConfirmed(event);
                    }
                })
                .setNegativeButton(R.string.reminder_remove_dialog_negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (callbacks != null) callbacks.onDeletionCancelled();
                    }
                });

        // Create dialog and return it
        return builder.create();
    }
}
