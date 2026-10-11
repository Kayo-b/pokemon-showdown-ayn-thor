package com.thor.showdown;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Top screen: the Showdown client in a WebView, driven by the gamepad. */
public final class MainActivity extends Activity implements Hub.Host {
	private static final int REQUEST_FILE = 1;
	private static final int[] WIDTHS = {0, 1024, 1152, 1280, 1440, 1600};
	/** Hosts the client may load from when ads are blocked (each also covers its subdomains). */
	private static final String[] CLIENT_HOSTS = {
		"pokemonshowdown.com", "psim.us", "smogon.com",
		"fonts.googleapis.com", "fonts.gstatic.com",
		// Google sign-in for registered accounts.
		"accounts.google.com", "apis.google.com", "www.gstatic.com", "ssl.gstatic.com", "googleusercontent.com",
	};

	private Prefs prefs;
	// Read from WebView's network thread, so cached here rather than read from prefs.
	private volatile boolean blockAds;
	private volatile String serverHost;
	private WebView web;
	private FrameLayout dock;
	private TextView badge;
	private DualScreen dual;
	private ScriptHandler startScript;
	private boolean usingJsInterface;
	private ValueCallback<Uri[]> fileCallback;
	private AlertDialog menu;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = new Prefs(this);
		cacheNetworkPrefs();
		// Waiting on an opponent means long stretches without input; don't let the screen sleep mid-battle.
		getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// Debug builds expose both WebViews to chrome://inspect for on-device testing.
		if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
			WebView.setWebContentsDebuggingEnabled(true);
		}

		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setBackgroundColor(Color.BLACK);

		web = new WebView(this);
		column.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		dock = new FrameLayout(this);
		dock.setVisibility(View.GONE);
		column.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.62f));

		FrameLayout root = new FrameLayout(this);
		root.addView(column);
		badge = makeBadge();
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		lp.topMargin = dp(10);
		root.addView(badge, lp);
		setContentView(root);

		setupWebView();
		Hub.get().attachMain(web, this, prefs);

		dual = new DualScreen(this, prefs, dock);

		if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
			web.loadUrl(prefs.serverUrl());
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		dual.start();
	}

	@Override
	protected void onStop() {
		// Don't leave the panel on the bottom screen while the app is in the background.
		dual.stop();
		super.onStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		hideSystemBars();
		web.onResume();
	}

	@Override
	protected void onPause() {
		web.onPause();
		CookieManager.getInstance().flush();
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		web.saveState(outState);
	}

	@Override
	protected void onDestroy() {
		Hub.get().detachMain(web);
		web.destroy();
		super.onDestroy();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) hideSystemBars();
	}

	/* ---------- input ---------- */

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

	/* ---------- WebView ---------- */

	@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
	private void setupWebView() {
		WebSettings s = web.getSettings();
		s.setJavaScriptEnabled(true);
		s.setDomStorageEnabled(true); // teams and settings live in localStorage
		s.setDatabaseEnabled(true);
		s.setMediaPlaybackRequiresUserGesture(false);
		s.setUseWideViewPort(true);
		s.setLoadWithOverviewMode(true);
		s.setSupportZoom(false);
		s.setBuiltInZoomControls(false);
		s.setTextZoom(100);
		s.setSupportMultipleWindows(true);
		s.setJavaScriptCanOpenWindowsAutomatically(true);
		s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
		s.setUserAgentString(s.getUserAgentString() + " ShowdownThor/1.0");

		CookieManager.getInstance().setAcceptCookie(true);
		CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

		web.setBackgroundColor(Color.BLACK);
		web.setOverScrollMode(View.OVER_SCROLL_NEVER);
		web.setFocusable(true);
		web.setFocusableInTouchMode(true);

		installBridge();
		installScripts();

		web.setWebViewClient(new WebViewClient() {
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
				if (!blockAds || request.isForMainFrame() || isClientHost(request.getUrl().getHost())) return null;
				return new WebResourceResponse("text/plain", "utf-8", 204, "Blocked",
					null, new ByteArrayInputStream(new byte[0]));
			}

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
				if (!request.isForMainFrame()) return false;
				Uri uri = request.getUrl();
				if (isClientUrl(uri)) return false;
				openExternal(uri);
				return true;
			}

			@Override
			public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
				// Without document-start scripts, inject as early as we can.
				if (startScript == null && isClientUrl(Uri.parse(url))) view.evaluateJavascript(buildScript(), null);
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				if (startScript == null && isClientUrl(Uri.parse(url))) view.evaluateJavascript(buildScript(), null);
			}

			@Override
			public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
				// The renderer died (usually memory pressure); start over cleanly.
				recreate();
				return true;
			}
		});

		web.setWebChromeClient(new WebChromeClient() {
			@Override
			public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
				// Links that open a new window (replays, dex, forums) go to the browser.
				WebView sink = new WebView(MainActivity.this);
				sink.setWebViewClient(new WebViewClient() {
					@Override
					public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
						openExternal(request.getUrl());
						v.destroy();
						return true;
					}
				});
				((WebView.WebViewTransport) resultMsg.obj).setWebView(sink);
				resultMsg.sendToTarget();
				return true;
			}

			@Override
			public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
				if (fileCallback != null) fileCallback.onReceiveValue(null);
				fileCallback = callback;
				try {
					startActivityForResult(params.createIntent(), REQUEST_FILE);
				} catch (ActivityNotFoundException e) {
					fileCallback = null;
					return false;
				}
				return true;
			}

			@Override
			public boolean onConsoleMessage(ConsoleMessage message) {
				if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
					Hub.log("console: " + message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
				}
				return true;
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_FILE && fileCallback != null) {
			fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
			fileCallback = null;
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private Set<String> allowedOrigins() {
		Set<String> origins = new HashSet<>();
		origins.add(Prefs.OFFICIAL_ORIGIN);
		origins.add(prefs.serverOrigin());
		return origins;
	}

	private boolean isClientUrl(Uri uri) {
		String scheme = uri.getScheme();
		if (scheme == null || uri.getHost() == null) return false;
		String origin = scheme + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
		return allowedOrigins().contains(origin);
	}

	@SuppressLint("AddJavascriptInterface")
	private void installBridge() {
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
			WebViewCompat.removeWebMessageListener(web, "ThorBridge");
			WebViewCompat.addWebMessageListener(web, "ThorBridge", allowedOrigins(),
				(view, message, sourceOrigin, isMainFrame, reply) -> {
					String data = message.getData();
					if (isMainFrame && data != null) Hub.get().onMainMessage(data);
				});
		} else if (!usingJsInterface) {
			usingJsInterface = true;
			web.addJavascriptInterface(new Object() {
				@JavascriptInterface
				public void postMessage(String json) {
					Hub.get().handler.post(() -> Hub.get().onMainMessage(json));
				}
			}, "ThorBridge");
		}
	}

	private void installScripts() {
		if (startScript != null) {
			startScript.remove();
			startScript = null;
		}
		if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
			startScript = WebViewCompat.addDocumentStartJavaScript(web, buildScript(), allowedOrigins());
		}
	}

	private void cacheNetworkPrefs() {
		blockAds = prefs.blockAds();
		serverHost = Uri.parse(prefs.serverUrl()).getHost();
	}

	/** Called on a background thread for every request, so it only reads the cached settings. */
	private boolean isClientHost(String host) {
		if (host == null) return false;
		if (host.equals(serverHost)) return true;
		for (String allowed : CLIENT_HOSTS) {
			if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
		}
		return false;
	}

	/** Tells the client which battle view to use; the arena-only view needs the panel for controls. */
	private void pushBattleView() {
		String view = prefs.battleView();
		if (Prefs.VIEW_FULL.equals(view) && "off".equals(dual.where())) view = Prefs.VIEW_LOG;
		Hub.get().evalMain("window.ThorPad && ThorPad.setView(" + JSONObject.quote(view) + ")");
	}

	@Override
	public void onClientReady() {
		pushBattleView();
	}

	private String buildScript() {
		return "window.THOR_CONFIG = {\"width\":" + prefs.clientWidth() + "};\n"
			+ readAsset("nav.js") + "\n" + readAsset("showdown.js");
	}

	private String readAsset(String name) {
		try (InputStream in = getAssets().open(name)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			throw new IllegalStateException("missing asset " + name, e);
		}
	}

	private void openExternal(Uri uri) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, "No app can open " + uri, Toast.LENGTH_SHORT).show();
		}
	}

	private void reloadClient() {
		cacheNetworkPrefs();
		installBridge();
		installScripts();
		web.loadUrl(prefs.serverUrl());
	}

	/* ---------- Hub.Host ---------- */

	@Override
	public void showIme(boolean show) {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (show) {
			web.requestFocus();
			Hub.get().handler.postDelayed(() -> imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT), 120);
		} else {
			imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
		}
	}

	@Override
	public void onTargetChanged(Hub.Target target) {
		badge.setText(target == Hub.Target.PANEL ? "🎮  Controlling battle panel  ▼" : "🎮  Controlling top screen  ▲");
		badge.animate().cancel();
		badge.setAlpha(1f);
		badge.setVisibility(View.VISIBLE);
		badge.animate().alpha(0f).setStartDelay(1100).setDuration(400)
			.withEndAction(() -> badge.setVisibility(View.GONE)).start();
	}

	@Override
	public void onSystemBackUnhandled() {
		showMenu();
	}

	@Override
	public void showMenu() {
		if (menu != null && menu.isShowing()) {
			menu.dismiss();
			return;
		}
		String width = prefs.clientWidth() == 0 ? "device" : prefs.clientWidth() + "px";
		String[] items = {
			"Go to Home",
			"Reload client",
			"Gamepad controls: " + (Hub.get().target() == Hub.Target.PANEL ? "battle panel" : "top screen") + " (Select)",
			"Battle panel: " + screenModeLabel(prefs.screenMode()) + " — now " + dual.where(),
			"Button layout: " + (prefs.nintendoLayout() ? "Nintendo (A right)" : "Xbox (A bottom)"),
			"Battle view: " + battleViewLabel(prefs.battleView()),
			"Block ads: " + (prefs.blockAds() ? "on" : "off"),
			"Client width: " + width,
			"Auto-focus panel on your turn: " + (prefs.autoPanel() ? "on" : "off"),
			"Server: " + prefs.serverUrl(),
			"Controls help",
			"Exit",
		};
		menu = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Showdown Thor")
			.setItems(items, (d, which) -> {
				switch (which) {
				case 0: Hub.get().evalMain("window.app && app.focusRoom('')"); break;
				case 1: reloadClient(); break;
				case 2: Hub.get().toggleTarget(); break;
				case 3: pickScreenMode(); break;
				case 4: prefs.setNintendoLayout(!prefs.nintendoLayout()); showMenu(); break;
				case 5: pickBattleView(); break;
				case 6: prefs.setBlockAds(!prefs.blockAds()); reloadClient(); break;
				case 7: pickWidth(); break;
				case 8: prefs.setAutoPanel(!prefs.autoPanel()); showMenu(); break;
				case 9: editServer(); break;
				case 10: showHelp(); break;
				case 11: finishAndRemoveTask(); break;
				}
			})
			.create();
		menu.show();
	}

	private static String screenModeLabel(String mode) {
		switch (mode) {
		case Prefs.SCREEN_ACTIVITY: return "second screen (window)";
		case Prefs.SCREEN_DOCKED: return "docked";
		case Prefs.SCREEN_OFF: return "off";
		default: return "auto";
		}
	}

	private void pickScreenMode() {
		String[] modes = {Prefs.SCREEN_AUTO, Prefs.SCREEN_ACTIVITY, Prefs.SCREEN_DOCKED, Prefs.SCREEN_OFF};
		String[] labels = {
			"Auto — second screen, else docked",
			"Second screen as separate window",
			"Docked under the client (one screen)",
			"Off",
		};
		int checked = 0;
		for (int i = 0; i < modes.length; i++) if (modes[i].equals(prefs.screenMode())) checked = i;
		new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Battle panel location")
			.setSingleChoiceItems(labels, checked, (d, which) -> {
				prefs.setScreenMode(modes[which]);
				d.dismiss();
				dual.apply();
				pushBattleView();
				Toast.makeText(this, "Battle panel: " + dual.where(), Toast.LENGTH_SHORT).show();
			})
			.show();
	}

	private static String battleViewLabel(String view) {
		switch (view) {
		case Prefs.VIEW_LOG: return "arena + log";
		case Prefs.VIEW_CLASSIC: return "classic";
		default: return "arena only";
		}
	}

	private void pickBattleView() {
		String[] views = {Prefs.VIEW_FULL, Prefs.VIEW_LOG, Prefs.VIEW_CLASSIC};
		String[] labels = {
			"Arena only — full screen, controls on the panel",
			"Arena + battle log",
			"Classic — the client's normal layout",
		};
		int checked = 0;
		for (int i = 0; i < views.length; i++) if (views[i].equals(prefs.battleView())) checked = i;
		new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Battle view (top screen)")
			.setSingleChoiceItems(labels, checked, (d, which) -> {
				prefs.setBattleView(views[which]);
				d.dismiss();
				pushBattleView();
			})
			.show();
	}

	private void pickWidth() {
		String[] labels = new String[WIDTHS.length];
		int checked = 0;
		for (int i = 0; i < WIDTHS.length; i++) {
			labels[i] = WIDTHS[i] == 0 ? "Device width (largest text)" : WIDTHS[i] + " px";
			if (WIDTHS[i] == prefs.clientWidth()) checked = i;
		}
		new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Client layout width")
			.setSingleChoiceItems(labels, checked, (d, which) -> {
				prefs.setClientWidth(WIDTHS[which]);
				d.dismiss();
				reloadClient();
			})
			.show();
	}

	private void editServer() {
		EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		input.setText(prefs.serverUrl());
		input.setSelectAllOnFocus(true);
		FrameLayout wrap = new FrameLayout(this);
		wrap.setPadding(dp(20), dp(8), dp(20), 0);
		wrap.addView(input);
		new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Client URL")
			.setMessage("Official: https://play.pokemonshowdown.com/\n"
				+ "Other server: https://play.pokemonshowdown.com/~~host:port")
			.setView(wrap)
			.setPositiveButton("Save", (d, w) -> {
				String url = input.getText().toString().trim();
				if (!url.matches("(?i)^https?://.+")) url = "https://" + url;
				prefs.setServerUrl(url);
				reloadClient();
			})
			.setNeutralButton("Default", (d, w) -> {
				prefs.setServerUrl(Prefs.DEFAULT_SERVER);
				reloadClient();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showHelp() {
		new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
			.setTitle("Controls")
			.setMessage(
				"D-pad / left stick — move the highlight\n"
				+ "A — press the highlighted button\n"
				+ "B — back / close popup / leave text box\n"
				+ "X — top: open chat box · panel: cycle Tera/Mega/Z/Dynamax\n"
				+ "Y — top: Home tab · panel: moves ⇄ switch menu\n"
				+ "L1 / R1 — previous / next tab (top) or battle (panel)\n"
				+ "L2 / R2 — page up / down\n"
				+ "Right stick — scroll the battle log or chat\n"
				+ "Select — move gamepad control between the screens\n"
				+ "Start — this menu\n\n"
				+ "On your turn the gamepad jumps to the battle panel automatically "
				+ "and returns to the top screen once you've chosen.")
			.setPositiveButton("OK", null)
			.show();
	}

	/* ---------- helpers ---------- */

	private TextView makeBadge() {
		TextView t = new TextView(this);
		t.setTextColor(Color.WHITE);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		t.setPadding(dp(14), dp(6), dp(14), dp(6));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xCC1B2436);
		bg.setStroke(dp(1), 0xFFFFD23F);
		bg.setCornerRadius(dp(16));
		t.setBackground(bg);
		t.setVisibility(View.GONE);
		return t;
	}

	private void hideSystemBars() {
		WindowInsetsController c = getWindow().getDecorView().getWindowInsetsController();
		if (c != null) {
			c.hide(WindowInsets.Type.systemBars());
			c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		}
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
