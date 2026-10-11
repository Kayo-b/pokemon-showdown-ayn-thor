package com.thor.showdown;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** User settings, stored in SharedPreferences. */
final class Prefs {
	static final String DEFAULT_SERVER = "https://play.pokemonshowdown.com/";
	static final String OFFICIAL_ORIGIN = "https://play.pokemonshowdown.com";

	/** Presentation on the second display, falling back to a separate activity there. */
	static final String SCREEN_AUTO = "auto";
	/** Always launch a separate activity on the second display. */
	static final String SCREEN_ACTIVITY = "activity";
	/** Panel docked under the client on the main screen. */
	static final String SCREEN_DOCKED = "docked";
	static final String SCREEN_OFF = "off";

	private final SharedPreferences sp;

	Prefs(Context context) {
		sp = context.getSharedPreferences("thor", Context.MODE_PRIVATE);
	}

	String serverUrl() {
		return sp.getString("server", DEFAULT_SERVER);
	}

	void setServerUrl(String url) {
		sp.edit().putString("server", url).apply();
	}

	/** Origin (scheme://host[:port]) of the configured server. */
	String serverOrigin() {
		Uri uri = Uri.parse(serverUrl());
		if (uri.getScheme() == null || uri.getHost() == null) return OFFICIAL_ORIGIN;
		String origin = uri.getScheme() + "://" + uri.getHost();
		if (uri.getPort() != -1) origin += ":" + uri.getPort();
		return origin;
	}

	boolean nintendoLayout() {
		return sp.getBoolean("nintendo", false);
	}

	void setNintendoLayout(boolean on) {
		sp.edit().putBoolean("nintendo", on).apply();
	}

	/** CSS width the client is laid out at; 0 means the device's own width. */
	int clientWidth() {
		return sp.getInt("width", 1280);
	}

	void setClientWidth(int width) {
		sp.edit().putInt("width", width).apply();
	}

	String screenMode() {
		return sp.getString("screen", SCREEN_AUTO);
	}

	void setScreenMode(String mode) {
		sp.edit().putString("screen", mode).apply();
	}

	/** Battle view on the top screen: arena only, arena plus log, or the client's normal layout. */
	static final String VIEW_FULL = "full";
	static final String VIEW_LOG = "log";
	static final String VIEW_CLASSIC = "classic";

	String battleView() {
		return sp.getString("battleView", VIEW_FULL);
	}

	void setBattleView(String view) {
		sp.edit().putString("battleView", view).apply();
	}

	boolean blockAds() {
		return sp.getBoolean("blockAds", true);
	}

	void setBlockAds(boolean on) {
		sp.edit().putBoolean("blockAds", on).apply();
	}

	boolean autoPanel() {
		return sp.getBoolean("autoPanel", true);
	}

	void setAutoPanel(boolean on) {
		sp.edit().putBoolean("autoPanel", on).apply();
	}
}
