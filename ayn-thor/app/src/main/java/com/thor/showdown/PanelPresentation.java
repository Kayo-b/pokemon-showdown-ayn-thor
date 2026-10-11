package com.thor.showdown;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.WebView;

/** Shows the battle panel on the second display while the app runs on the first. */
final class PanelPresentation extends Presentation {
	private WebView web;

	PanelPresentation(Context outerContext, Display display) {
		super(outerContext, display);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		web = PanelWeb.create(getContext());
		setContentView(web);
		Hub.get().attachPanel(web);
	}

	@Override
	protected void onStop() {
		PanelWeb.destroy(web);
		web = null;
		super.onStop();
	}

	// Touching the bottom screen moves input focus here; keep the gamepad working.
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
