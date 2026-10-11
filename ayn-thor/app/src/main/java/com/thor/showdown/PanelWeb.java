package com.thor.showdown;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Creates the WebView that renders the battle panel (assets/panel.html). */
final class PanelWeb {
	private PanelWeb() {}

	@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
	static WebView create(Context context) {
		WebView web = new WebView(context);
		web.setBackgroundColor(Color.rgb(0x0f, 0x15, 0x22));
		WebSettings s = web.getSettings();
		s.setJavaScriptEnabled(true);
		s.setDomStorageEnabled(true);
		s.setTextZoom(100);
		s.setSupportZoom(false);
		// Only ever loads the bundled page; the bridge is never exposed to remote content.
		web.addJavascriptInterface(new Bridge(), "PanelBridge");
		web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
		web.loadUrl("file:///android_asset/panel.html");
		return web;
	}

	static void destroy(WebView web) {
		if (web == null) return;
		Hub.get().detachPanel(web);
		web.destroy();
	}

	private static final class Bridge {
		@JavascriptInterface
		public void post(String json) {
			Hub hub = Hub.get();
			hub.handler.post(() -> hub.onPanelMessage(json));
		}
	}
}
