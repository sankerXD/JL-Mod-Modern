/*
 * Copyright 2024 JL-Mod contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.microedition.lcdui.keyboard;

import android.app.Activity;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Gamepad trigger support.
 * <p>
 * On most handhelds the L2/R2 buttons are reported by the kernel as analog
 * axes (ABS_Z / ABS_RZ) rather than as key codes, so they never reach
 * {@link KeyMapper} and cannot be assigned in the key mapping screen.
 * <p>
 * This helper converts them into real {@link KeyEvent#KEYCODE_BUTTON_L2} /
 * {@link KeyEvent#KEYCODE_BUTTON_R2} key events which are dispatched through
 * the regular key path of the activity, so they can be remapped per game
 * exactly like any other button.
 */
public class TriggerAxisHandler {
	private static final float PRESS_THRESHOLD = 0.5f;
	private static final float RELEASE_THRESHOLD = 0.3f;

	private boolean leftPressed;
	private boolean rightPressed;

	/**
	 * @return {@code true} if a trigger changed state and a key event was sent
	 */
	public boolean handle(Activity activity, MotionEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK)
				!= InputDevice.SOURCE_CLASS_JOYSTICK) {
			return false;
		}
		boolean handled = update(activity, event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
				KeyEvent.KEYCODE_BUTTON_L2, true);
		handled |= update(activity, event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
				KeyEvent.KEYCODE_BUTTON_R2, false);
		return handled;
	}

	/**
	 * Sends key up for pressed triggers, e.g. when the activity goes to background.
	 */
	public void release(Activity activity) {
		if (leftPressed) {
			leftPressed = false;
			send(activity, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2);
		}
		if (rightPressed) {
			rightPressed = false;
			send(activity, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2);
		}
	}

	private boolean update(Activity activity, float value, int keyCode, boolean left) {
		boolean pressed = left ? leftPressed : rightPressed;
		if (!pressed && value > PRESS_THRESHOLD) {
			if (left) {
				leftPressed = true;
			} else {
				rightPressed = true;
			}
			send(activity, KeyEvent.ACTION_DOWN, keyCode);
			return true;
		}
		if (pressed && value < RELEASE_THRESHOLD) {
			if (left) {
				leftPressed = false;
			} else {
				rightPressed = false;
			}
			send(activity, KeyEvent.ACTION_UP, keyCode);
			return true;
		}
		return false;
	}

	private void send(Activity activity, int action, int keyCode) {
		long now = SystemClock.uptimeMillis();
		activity.dispatchKeyEvent(new KeyEvent(now, now, action, keyCode, 0));
	}
}
