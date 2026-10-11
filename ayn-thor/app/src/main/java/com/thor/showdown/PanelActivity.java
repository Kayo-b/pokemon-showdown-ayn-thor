package com.thor.showdown;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebView;

/**
 * Battle panel as its own activity, launched onto the second display when
 * the system won't let a Presentation window be placed there.
 */
public final class PanelActivity extends Activity {
	static PanelActivity running;
	private WebView web;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		running = this;
		web = PanelWeb.create(this);
		setContentView(web);
		Hub.get().attachPanel(web);
	}

	@Override
	protected void onResume() {
		super.onResume();
		View decor = getWindow().getDecorView();
		WindowInsetsController c = decor.getWindowInsetsController();
		if (c != null) {
			c.hide(WindowInsets.Type.systemBars());
			c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		}
	}

	@Override
	protected void onDestroy() {
		if (running == this) running = null;
		PanelWeb.destroy(web);
		web = null;
		super.onDestroy();
	}

	static void close() {
		if (running != null) running.finish();
		running = null;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		PadInput pad = Hub.get().pad();
		if (pad != null && pad.onKey(event)) return true;
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		PadInput pad = Hub.get().pad();
		if (pad != null && pad.onMotion(event)) return true;
		return super.dispatchGenericMotionEvent(event);
	}
}
