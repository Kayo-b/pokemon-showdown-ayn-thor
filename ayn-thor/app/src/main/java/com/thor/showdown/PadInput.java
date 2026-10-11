package com.thor.showdown;

import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Turns gamepad keys and stick motion into named buttons for {@link Hub}.
 *
 * Buttons: up/down/left/right, a, b, x, y, l1, r1, l2, r2, start, select.
 * The left stick and hat repeat like a held d-pad; the right stick scrolls.
 */
final class PadInput {
	private static final String TAG = "ThorInput";
	private static final float STICK_DEADZONE = 0.5f;
	private static final float SCROLL_DEADZONE = 0.18f;
	private static final float TRIGGER_THRESHOLD = 0.6f;
	private static final long REPEAT_DELAY = 380;
	private static final long REPEAT_INTERVAL = 120;
	private static final long SCROLL_FRAME = 32;

	private final Hub hub;
	private final Prefs prefs;

	private String stickDir;
	private boolean dpadKeysSeen;
	private boolean triggerKeysSeen;
	private boolean l2Down, r2Down;
	private float scrollX, scrollY;
	private boolean scrolling;

	PadInput(Hub hub, Prefs prefs) {
		this.hub = hub;
		this.prefs = prefs;
	}

	void reset() {
		stickDir = null;
		scrollX = scrollY = 0;
		hub.handler.removeCallbacks(repeat);
		hub.handler.removeCallbacks(scrollTick);
		scrolling = false;
	}

	/** Returns true if the key was consumed. */
	boolean onKey(KeyEvent event) {
		int code = event.getKeyCode();
		if (event.getRepeatCount() == 0) {
			Log.d(TAG, "key " + KeyEvent.keyCodeToString(code) + (event.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up")
				+ " src=0x" + Integer.toHexString(event.getSource()) + " dev=" + deviceName(event.getDevice()));
		}
		if (code == KeyEvent.KEYCODE_BACK) {
			// Gamepad B on some devices arrives as BACK; touch/nav back goes through here too.
			if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
				if (isFromGamepad(event)) hub.button("b");
				else hub.systemBack();
			}
			return true;
		}
		String button = map(code);
		if (button == null) return false;
		boolean direction = isDirection(button);
		if (direction) dpadKeysSeen = true;
		if (button.equals("l2") || button.equals("r2")) triggerKeysSeen = true;
		if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getRepeatCount() == 0 || direction)) {
			hub.button(button);
		}
		return true;
	}

	/** Returns true if the motion event was consumed. */
	boolean onMotion(MotionEvent event) {
		if (event.getAction() != MotionEvent.ACTION_MOVE) return false;
		int source = event.getSource();
		if ((source & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
			&& (source & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
			return false;
		}
		logAxes(event);

		// Left stick, or the hat if this d-pad doesn't report key events.
		String dir = direction(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), STICK_DEADZONE);
		if (dir == null && !dpadKeysSeen) {
			dir = direction(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.5f);
		}
		if (dir == null ? stickDir != null : !dir.equals(stickDir)) {
			stickDir = dir;
			hub.handler.removeCallbacks(repeat);
			if (dir != null) {
				hub.button(dir);
				hub.handler.postDelayed(repeat, REPEAT_DELAY);
			}
		}

		// Right stick scrolls.
		float rx = event.getAxisValue(MotionEvent.AXIS_Z);
		float ry = event.getAxisValue(MotionEvent.AXIS_RZ);
		if (Math.abs(rx) < 0.01f && Math.abs(ry) < 0.01f) {
			rx = event.getAxisValue(MotionEvent.AXIS_RX);
			ry = event.getAxisValue(MotionEvent.AXIS_RY);
		}
		scrollX = Math.abs(rx) > SCROLL_DEADZONE ? rx : 0;
		scrollY = Math.abs(ry) > SCROLL_DEADZONE ? ry : 0;
		if ((scrollX != 0 || scrollY != 0) && !scrolling) {
			scrolling = true;
			hub.handler.post(scrollTick);
		}

		// Analog triggers, unless they also send key events.
		if (!triggerKeysSeen) {
			float lt = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE));
			float rt = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS));
			if (lt > TRIGGER_THRESHOLD && !l2Down) {
				l2Down = true;
				hub.button("l2");
			} else if (lt < TRIGGER_THRESHOLD / 2) {
				l2Down = false;
			}
			if (rt > TRIGGER_THRESHOLD && !r2Down) {
				r2Down = true;
				hub.button("r2");
			} else if (rt < TRIGGER_THRESHOLD / 2) {
				r2Down = false;
			}
		}
		return true;
	}

	private final Runnable repeat = new Runnable() {
		@Override
		public void run() {
			if (stickDir == null) return;
			hub.button(stickDir);
			hub.handler.postDelayed(this, REPEAT_INTERVAL);
		}
	};

	private final Runnable scrollTick = new Runnable() {
		@Override
		public void run() {
			if (scrollX == 0 && scrollY == 0) {
				scrolling = false;
				return;
			}
			hub.scroll(curve(scrollX), curve(scrollY));
			hub.handler.postDelayed(this, SCROLL_FRAME);
		}
	};

	/** Gentle near the center, fast at the edge. */
	private static float curve(float v) {
		return Math.signum(v) * v * v * 42f;
	}

	private static String direction(float x, float y, float deadzone) {
		if (Math.abs(x) < deadzone && Math.abs(y) < deadzone) return null;
		if (Math.abs(x) > Math.abs(y)) return x > 0 ? "right" : "left";
		return y > 0 ? "down" : "up";
	}

	private static boolean isDirection(String button) {
		return button.equals("up") || button.equals("down") || button.equals("left") || button.equals("right");
	}

	private String lastAxes = "";

	/** Logs which axes are pushed, only when that changes. */
	private void logAxes(MotionEvent event) {
		InputDevice dev = event.getDevice();
		if (dev == null) return;
		StringBuilder sb = new StringBuilder();
		for (InputDevice.MotionRange range : dev.getMotionRanges()) {
			float v = event.getAxisValue(range.getAxis());
			if (Math.abs(v) > 0.5f) sb.append(MotionEvent.axisToString(range.getAxis())).append(v > 0 ? "+ " : "- ");
		}
		String axes = sb.toString();
		if (!axes.equals(lastAxes)) {
			lastAxes = axes;
			Log.d(TAG, "axes [" + axes.trim() + "] dev=" + deviceName(dev));
		}
	}

	private static String deviceName(InputDevice dev) {
		return dev == null ? "?" : dev.getName();
	}

	private static boolean isFromGamepad(KeyEvent event) {
		int source = event.getSource();
		return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
			|| (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
	}

	private String map(int code) {
		boolean swap = prefs.nintendoLayout();
		switch (code) {
		case KeyEvent.KEYCODE_DPAD_UP: return "up";
		case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
		case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
		case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
		case KeyEvent.KEYCODE_DPAD_CENTER: return "a";
		case KeyEvent.KEYCODE_BUTTON_A: return swap ? "b" : "a";
		case KeyEvent.KEYCODE_BUTTON_B: return swap ? "a" : "b";
		case KeyEvent.KEYCODE_BUTTON_X: return swap ? "y" : "x";
		case KeyEvent.KEYCODE_BUTTON_Y: return swap ? "x" : "y";
		case KeyEvent.KEYCODE_BUTTON_L1: return "l1";
		case KeyEvent.KEYCODE_BUTTON_R1: return "r1";
		case KeyEvent.KEYCODE_BUTTON_L2: return "l2";
		case KeyEvent.KEYCODE_BUTTON_R2: return "r2";
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_MENU:
			return "start";
		case KeyEvent.KEYCODE_BUTTON_SELECT: return "select";
		case KeyEvent.KEYCODE_BUTTON_THUMBL: return "l3";
		case KeyEvent.KEYCODE_BUTTON_THUMBR: return "r3";
		default: return null;
		}
	}
}
