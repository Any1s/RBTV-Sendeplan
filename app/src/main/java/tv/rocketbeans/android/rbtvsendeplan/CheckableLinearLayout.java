package tv.rocketbeans.android.rbtvsendeplan;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.LinearLayout;

public class CheckableLinearLayout extends LinearLayout implements Checkable {

	private static final int[] CHECKED_STATE_SET = {
		android.R.attr.state_checked
	};
	
	private boolean checked;
	
	public CheckableLinearLayout(Context context) {
		super(context);
	}

	public CheckableLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CheckableLinearLayout(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	public boolean performClick() {
		toggle();
		return super.performClick();
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return onTouchEvent(ev);
	}

	@Override
	public boolean isChecked() {
		return checked;
	}

	@Override
	public void setChecked(boolean checked) {
        if (this.checked != checked) {
			this.checked = checked;
			refreshDrawableState();
			setCheckedRecursive(this, checked);
		}
	}
	
	/**
	 * Traverses all children of this layout recursively to set their checked
	 * state
	 * @param parent - parent View to those Views to be checked
	 * @param checked - to be checked or not to be checked, that is the question
	 */
	private void setCheckedRecursive(ViewGroup parent, boolean checked) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View view = parent.getChildAt(i);
			if (view instanceof Checkable) {
				((Checkable) view).setChecked(checked);
			}
			
			if (view instanceof ViewGroup) {
				setCheckedRecursive((ViewGroup) view, checked);
			}
		}
	}

	@Override
	public void toggle() {
        setChecked(!checked);
	}
	
	@Override
	protected int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		
		Drawable drawable = getBackground();
		if (drawable != null) {
			int[] myDrawableState = getDrawableState();
			drawable.setState(myDrawableState);
			invalidate();
		}
	}
	
	static class SavedState extends BaseSavedState {
		boolean checked;
		
		SavedState(Parcelable superState) {
			super(superState);
		}
		
		private SavedState(Parcel in) {
			super(in);
			checked = (Boolean) in.readValue(null);
		}
		
		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeValue(checked);
		}
		
		@Override
		public String toString() {
			return "CheckableLinearLayout.SavedState{"
					+ Integer.toHexString(System.identityHashCode(this))
					+ " checked=" + checked + "}";
		}
		
		public static final Parcelable.Creator<SavedState> CREATOR 
				= new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}
			
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
	
	@Override
	public Parcelable onSaveInstanceState() {
		// Let ancestor class save its state
		Parcelable superState = super.onSaveInstanceState();
		SavedState savedState = new SavedState(superState);
		
		savedState.checked = isChecked();
		return savedState;
	}
	
	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;
		
		super.onRestoreInstanceState(savedState.getSuperState());
		setChecked(savedState.checked);
		requestLayout();
	}

}
