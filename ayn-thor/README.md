# Showdown Thor

Android client for [Pokémon Showdown](https://play.pokemonshowdown.com) built for the
**AYN Thor** dual-screen handheld: the full client on the top screen, a touch- and
gamepad-friendly battle panel on the bottom screen, and complete gamepad control.

## Install

Download the APK from the [latest release](https://github.com/Kayo-b/pokemon-showdown-ayn-thor/releases/latest)
(under **Assets**), open it on the Thor and allow **Install unknown apps** when asked. Or, from a PC with USB
debugging enabled:

```
adb install -r ShowdownThor-v1.0.apk
```

## Build

Requires JDK 17 (newer JDKs are not supported by this Gradle version) and the Android SDK (platform 35). Point
Gradle at the SDK with `ANDROID_HOME`, or create `local.properties` here containing `sdk.dir=/path/to/Android/Sdk`.

```
JAVA_HOME=/path/to/jdk17 ./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Screens

| Screen | Shows |
|---|---|
| Top | The real Showdown client (laid out at 1280 CSS px so battle + chat fit side by side). |
| Bottom | Battle panel: moves with type, PP, power, accuracy and type-effectiveness vs. the foe; Tera / Mega / Z / Dynamax toggle; switch grid with HP and status; doubles/triples target picker; team preview ordering; timer, forfeit, undo. |

The panel reads the client's own battle state and sends choices through it, so
the top screen always stays in sync (choosing on the panel shows "Waiting for
opponent" on top, and vice versa).

If there is no second display, the panel docks under the client (Start → Battle panel).

## Controls

| Button | Top screen | Battle panel |
|---|---|---|
| D-pad / left stick | move highlight | move highlight |
| A | press | press |
| B | back / close popup / leave text box | back a step; at the start, hand control back to top |
| X | open chat box (keyboard) | cycle Tera / Mega / Z / Dynamax |
| Y | Home tab | moves ⇄ switch menu |
| L1 / R1 | previous / next tab | previous / next battle |
| L2 / R2 | page up / down | — |
| Right stick | scroll battle log / chat | scroll battle log / chat |
| Select | move control between screens | move control between screens |
| Start | app menu | app menu |

When it's your turn, control jumps to the battle panel automatically and goes back
to the top screen once you've chosen (can be turned off in the menu). Both screens
are also fully usable by touch.

## Menu (Start)

- Battle panel location: auto (second screen, else docked) / second screen as a separate
  window / docked / off
- Button layout: Xbox (A bottom) or Nintendo (A right)
- Battle view: arena only (default; battle scene fills the top screen, controls live on the panel), arena + log, or classic client layout. Arena only falls back to arena + log when the panel is off.
  In arena-only view, an overlay with the tabs, battle log/chat and the client's battle controls
  pops up over the arena: X on the top screen (press X again for the chat box), the panel's
  "Log ☰" button, or a stick click (L3/R3). B closes it; the right stick scrolls the log.
- Block ads: on by default; the client may only load from Showdown, its battle servers, fonts, Google sign-in and your configured server.
- Client width: device, 1024–1600 px
- Server: defaults to `https://play.pokemonshowdown.com/`; other servers via
  `https://play.pokemonshowdown.com/~~host:port` or a self-hosted client URL

## How it works

- `MainActivity` hosts the client WebView and injects `assets/nav.js` +
  `assets/showdown.js` at document start (viewport override, gamepad navigation,
  battle state snapshots, choice commands).
- `DualScreen` finds the second display and shows `assets/panel.html` there as a
  `Presentation`, falling back to `PanelActivity` launched on that display, or a docked view.
- `PadInput` maps gamepad keys/axes to buttons; `Hub` routes them to whichever screen
  has control and relays messages between the client and the panel.
