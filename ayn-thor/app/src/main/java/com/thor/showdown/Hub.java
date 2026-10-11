package com.thor.showdown;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Connects the top-screen client, the bottom-screen panel and the gamepad.
 * All methods must be called on the main thread.
 */
final class Hub {
	private static final String TAG = "ThorHub";

	enum Target { WEB, PANEL }

	interface Host {
		void showIme(boolean show);
		void showMenu();
		void onTargetChanged(Target target);
		void onSystemBackUnhandled();
		void onClientReady();
	}

	private static Hub instance;

	static Hub get() {
		if (instance == null) instance = new Hub();
		return instance;
	}

	final Handler handler = new Handler(Looper.getMainLooper());
	private WebView main;
	private WebView panel;
	private Host host;
	private Prefs prefs;
	private PadInput pad;
	private Target target = Target.WEB;
	private String lastState;
	private boolean systemBackPending;

	void attachMain(WebView webView, Host host, Prefs prefs) {
		this.main = webView;
		this.host = host;
		this.prefs = prefs;
		this.pad = new PadInput(this, prefs);
	}

	void detachMain(WebView webView) {
		if (main != webView) return;
		main = null;
		host = null;
		if (pad != null) pad.reset();
	}

	PadInput pad() {
		return pad;
	}

	void attachPanel(WebView webView) {
		panel = webView;
		pushState();
		evalPanel("Panel.setActive(" + (target == Target.PANEL) + ")");
	}

	void detachPanel(WebView webView) {
		if (panel != webView) return;
		panel = null;
		if (target == Target.PANEL) setTarget(Target.WEB);
	}

	boolean hasPanel() {
		return panel != null;
	}

	Target target() {
		return target;
	}

	void setTarget(Target next) {
		if (next == Target.PANEL && panel == null) next = Target.WEB;
		boolean changed = next != target;
		target = next;
		evalMain("window.ThorPad && ThorPad.setActive(" + (next == Target.WEB) + ")");
		evalPanel("Panel.setActive(" + (next == Target.PANEL) + ")");
		if (changed && host != null) host.onTargetChanged(next);
	}

	void toggleTarget() {
		setTarget(target == Target.WEB ? Target.PANEL : Target.WEB);
	}

	/* ---------- input ---------- */

	void button(String name) {
		switch (name) {
		case "start":
			if (host != null) host.showMenu();
			return;
		case "select":
			toggleTarget();
			return;
		case "l3":
		case "r3":
			toggleOverlay();
			return;
		}
		if (target == Target.PANEL && panel != null) {
			evalPanel("Panel.input(" + JSONObject.quote(name) + ")");
		} else {
			evalMain("window.ThorPad && ThorPad.input(" + JSONObject.quote(name) + ")");
		}
	}

	/** System back (gesture or nav button) behaves like B, but can fall through to the menu. */
	void systemBack() {
		systemBackPending = true;
		handler.postDelayed(() -> systemBackPending = false, 400);
		button("b");
	}

	/** Shows or hides the log/controls overlay over a full-screen battle on the top screen. */
	void toggleOverlay() {
		evalMain("window.ThorPad && ThorPad.toggleOverlay()");
	}

	void scroll(float dx, float dy) {
		evalMain("window.ThorPad && ThorPad.scroll(" + Math.round(dx) + "," + Math.round(dy) + ")");
	}

	/* ---------- messages ---------- */

	void onMainMessage(String json) {
		JSONObject msg;
		try {
			msg = new JSONObject(json);
		} catch (JSONException e) {
			return;
		}
		switch (msg.optString("t")) {
		case "state":
			lastState = json;
			pushState();
			break;
		case "preview": {
			JSONObject data = msg.optJSONObject("data");
			if (data != null) evalPanel("window.Panel && Panel.preview(" + data + ")");
			break;
		}
		case "ime":
			if (host != null) host.showIme(msg.optBoolean("show"));
			break;
		case "ready":
			lastState = null;
			evalMain("ThorPad.setActive(" + (target == Target.WEB) + ")");
			if (host != null) host.onClientReady();
			break;
		case "backUnhandled":
			if (systemBackPending && host != null) {
				systemBackPending = false;
				host.onSystemBackUnhandled();
			}
			break;
		}
	}

	void onPanelMessage(String json) {
		JSONObject msg;
		try {
			msg = new JSONObject(json);
		} catch (JSONException e) {
			return;
		}
		String room = JSONObject.quote(msg.optString("room"));
		switch (msg.optString("t")) {
		case "panelReady":
			pushState();
			evalPanel("Panel.setActive(" + (target == Target.PANEL) + ")");
			break;
		case "choose":
			evalMain("ThorPS.choose(" + room + "," + JSONObject.quote(msg.optString("choice")) + ","
				+ msg.optInt("rqid") + ")");
			break;
		case "undo":
			evalMain("ThorPS.undo(" + room + ")");
			break;
		case "reset":
			evalMain("ThorPS.reset(" + room + ")");
			break;
		case "cmd":
			evalMain("ThorPS.cmd(" + room + "," + JSONObject.quote(msg.optString("text")) + ")");
			break;
		case "focus":
			evalMain("ThorPS.focus(" + room + ")");
			break;
		case "leave":
			evalMain("ThorPS.leave(" + room + ")");
			break;
		case "release":
			setTarget(Target.WEB);
			break;
		case "wantFocus":
			if (prefs != null && prefs.autoPanel()) setTarget(Target.PANEL);
			break;
		case "menu":
			if (host != null) host.showMenu();
			break;
		case "overlay":
			toggleOverlay();
			break;
		}
	}

	private void pushState() {
		if (lastState == null) {
			evalMain("window.ThorPS && ThorPS.refresh()");
			return;
		}
		evalPanel("Panel.update(" + lastState + ")");
	}

	void evalMain(String js) {
		if (main != null) main.evaluateJavascript(js, null);
	}

	void evalPanel(String js) {
		if (panel != null) panel.evaluateJavascript("window.Panel && " + js, null);
	}

	static void log(String text) {
		Log.d(TAG, text);
	}
}
