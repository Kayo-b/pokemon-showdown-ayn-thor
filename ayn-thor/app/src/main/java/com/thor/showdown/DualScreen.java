package com.thor.showdown;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Decides where the battle panel lives: on the second display (the AYN Thor's
 * bottom screen) when there is one, otherwise docked under the client.
 */
final class DualScreen implements DisplayManager.DisplayListener {
	private final Activity activity;
	private final Prefs prefs;
	private final ViewGroup dock;
	private final DisplayManager displays;

	private PanelPresentation presentation;
	private WebView dockedWeb;
	private int panelDisplayId = -1;
	private String where = "none";

	DualScreen(Activity activity, Prefs prefs, ViewGroup dock) {
		this.activity = activity;
		this.prefs = prefs;
		this.dock = dock;
		this.displays = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
	}

	void start() {
		displays.registerDisplayListener(this, Hub.get().handler);
		apply();
	}

	void stop() {
		displays.unregisterDisplayListener(this);
		teardown();
	}

	/** Human-readable description of where the panel currently is. */
	String where() {
		return where;
	}

	void apply() {
		teardown();
		String mode = prefs.screenMode();
		if (Prefs.SCREEN_OFF.equals(mode)) {
			where = "off";
			return;
		}
		if (!Prefs.SCREEN_DOCKED.equals(mode)) {
			Display second = findSecondDisplay();
			if (second != null) {
				panelDisplayId = second.getDisplayId();
				if (Prefs.SCREEN_ACTIVITY.equals(mode) || !showPresentation(second)) {
					launchActivity(second);
				}
				return;
			}
		}
		showDocked();
	}

	private Display findSecondDisplay() {
		for (Display d : displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
			if (d.getDisplayId() != Display.DEFAULT_DISPLAY) return d;
		}
		for (Display d : displays.getDisplays()) {
			if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.getDisplayId() != activity.getWindowManager().getDefaultDisplay().getDisplayId()) {
				return d;
			}
		}
		return null;
	}

	private boolean showPresentation(Display display) {
		try {
			presentation = new PanelPresentation(activity, display);
			presentation.show();
			where = "second screen (" + display.getName() + ")";
			return true;
		} catch (WindowManager.InvalidDisplayException | IllegalArgumentException e) {
			Hub.log("presentation refused on display " + display.getDisplayId() + ": " + e);
			presentation = null;
			return false;
		}
	}

	private void launchActivity(Display display) {
		try {
			Intent intent = new Intent(activity, PanelActivity.class)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
			ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(display.getDisplayId());
			activity.startActivity(intent, options.toBundle());
			where = "second screen window (" + display.getName() + ")";
		} catch (RuntimeException e) {
			Hub.log("could not launch panel on display " + display.getDisplayId() + ": " + e);
			showDocked();
		}
	}

	private void showDocked() {
		dockedWeb = PanelWeb.create(activity);
		dock.addView(dockedWeb, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		dock.setVisibility(View.VISIBLE);
		Hub.get().attachPanel(dockedWeb);
		where = "docked on main screen";
	}

	private void teardown() {
		if (presentation != null) {
			presentation.dismiss();
			presentation = null;
		}
		PanelActivity.close();
		if (dockedWeb != null) {
			dock.removeView(dockedWeb);
			PanelWeb.destroy(dockedWeb);
			dockedWeb = null;
		}
		dock.setVisibility(View.GONE);
		panelDisplayId = -1;
		where = "none";
	}

	@Override
	public void onDisplayAdded(int displayId) {
		if (panelDisplayId == -1 && !Prefs.SCREEN_OFF.equals(prefs.screenMode())
			&& !Prefs.SCREEN_DOCKED.equals(prefs.screenMode())) {
			apply();
		}
	}

	@Override
	public void onDisplayRemoved(int displayId) {
		if (displayId == panelDisplayId) apply();
	}

	@Override
	public void onDisplayChanged(int displayId) {}
}
