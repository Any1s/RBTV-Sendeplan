package de.mbdevelopment.android.rbtvsendeplan;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

/**
 * {@link android.preference.DialogPreference} that lets the user choose a number.
 */
public class NumberPickerPreference extends DialogPreference {

    /**
     * The current number
     */
    private int currentValue;

    /**
     * the default number
     */
    private int defaultValue;

    /**
     * The number picker of this DialogPreference
     */
    private NumberPicker numberPicker;

    /**
     * Internal saved state
     */
    private static class SavedState extends BaseSavedState {
        /**
         * Current number in the picker
         */
        int value;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            // Get current number
            value = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            // Write number
            dest.writeInt(value);
        }

        /**
         * Standard creator object using an instance of this class
         */
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        defaultValue = getContext().getResources()
                .getInteger(R.integer.pref_reminder_offset_default);

        setDialogLayoutResource(R.layout.numberpicker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);

        setDialogIcon(null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        // Configure NumberPicker
        numberPicker = (NumberPicker) view.findViewById(R.id.pref_reminder_offset_picker);
        numberPicker.setMaxValue(getContext().getResources()
                .getInteger(R.integer.pref_reminder_offset_max));
        numberPicker.setMinValue(getContext().getResources()
                .getInteger(R.integer.pref_reminder_offset_min));
        numberPicker.setValue(currentValue);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            currentValue = numberPicker.getValue();
            persistString(String.valueOf(currentValue));
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore existing state or return default value if there is none
            currentValue = Integer.parseInt(this.getPersistedString(
                    String.valueOf(this.defaultValue)));
        } else {
            // Set default state from the XML attribute
            currentValue = Integer.parseInt((String) defaultValue);
            persistString(String.valueOf(currentValue));
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        // Check whether this Preference is persistent (continually saved)
        if (isPersistent()) {
            // No need to save instance state since it's persistent, use superclass state
            return superState;
        }

        // Create instance of custom BaseSavedState
        final SavedState savedState = new SavedState(superState);
        // Set the state's value with the class member that holds current setting value
        savedState.value = currentValue;
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // Check whether we saved the state in onSaveInstanceState
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save the state, so call superclass
            super.onRestoreInstanceState(state);
            return;
        }

        // Cast state to custom BaseSavedState and pass to superclass
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        // Set this Preference's widget to reflect the restored state
        numberPicker.setValue(savedState.value);
    }

    public int getEntry() {
        return currentValue;
    }
}
