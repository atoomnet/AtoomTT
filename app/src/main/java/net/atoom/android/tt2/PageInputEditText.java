package net.atoom.android.tt2;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.EditText;

public class PageInputEditText extends EditText {

	private int myInputLength;

	public PageInputEditText(Context context) {
		super(context);
	}

	public PageInputEditText(Context context, int keyCode) {
		super(context);
		switch (keyCode) {
		case KeyEvent.KEYCODE_1:
			super.setText("1");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_2:
			super.setText("2");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_3:
			super.setText("3");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_4:
			super.setText("4");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_5:
			super.setText("5");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_6:
			super.setText("6");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_7:
			super.setText("7");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_8:
			super.setText("8");
			myInputLength = 1;
			break;
		case KeyEvent.KEYCODE_9:
			super.setText("9");
			myInputLength = 1;
			break;
		default:
			myInputLength = 0;
			break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (myInputLength == 3 && keyCode != KeyEvent.KEYCODE_DEL)
			return false;

		if (myInputLength == 0 && keyCode == KeyEvent.KEYCODE_0) {
			return false;
		}

		if ((keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) || keyCode == KeyEvent.KEYCODE_DEL) {
			if (super.onKeyDown(keyCode, event)) {
				myInputLength = getText().toString().length();
				return true;
			}
		}
		return false;
	}
}
