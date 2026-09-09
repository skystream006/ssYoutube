# ssYouTube

A minimal Android app that shows the YouTube **mobile** website in a full-screen WebView,
keeps you signed in, and blocks advertising/tracking requests.

## Features

- **YouTube mobile only** – loads `https://m.youtube.com/` with a mobile user agent.
  Links to other sites are blocked from opening outside the app (`SiteScope`), while
  YouTube and the Google sign-in domains stay in-app.
- **YouTube link handling** – the app can be selected as the default handler for
  YouTube web links and opens incoming YouTube links directly in the WebView.
- **Persistent sign-in** – cookies are accepted (including third-party cookies needed by
  the Google account flow) and flushed to disk on pause, and DOM storage is enabled, so
  the session survives app restarts.
- **Preferences** – a floating settings button appears on the YouTube home page (the page
  with the bottom navigation bar). It opens a preferences dialog with icon buttons for
  back/forward/reload/home navigation, plus the theme (system/light/dark) and the site mode
  (mobile or desktop, `Preferences`), mirroring a browser's "desktop site" toggle. On
  desktop video pages, the panel also includes a related-videos toggle.
  Back and forward behave like a browser's buttons: repeated history entries for the same
  page (created by the YouTube single page app) are skipped so every press changes page
  (`NavigationHistory`).
- **Ad blocking** – requests to known ad/tracking hosts and ad endpoints are intercepted
  and answered with an empty response (`AdBlocker`), and a stylesheet is injected on every
  page load to hide inline promoted/ad renderers. The ad-hiding stylesheet and the JSON
  ad-pruning hook are registered as document start scripts (androidx.webkit), so they run
  before the page's own scripts on every load and refresh instead of racing them.
  Shopping and "Buy Now" call-to-action elements are hidden by CSS and removed
  as they appear.
- **No Playables** – the "Playables" shelves and navigation entries are removed from pages
  as they appear.
- **No Posts shelf** – the "Posts" section is removed from the home page as it appears.
- **Subscriber counts** – a page injection loads each video card channel's public subscriber
  count and displays it beside the channel avatar. Comment authors are skipped and the
  lookups are queued a few at a time so they never crowd out the page's own requests.
- **Video swipe gestures** – swiping up on a playing video enters fullscreen, swiping down
  exits it, and swiping down on a watch page shrinks the video into a miniplayer (mobile
  site mode only – the miniplayer styling targets the mobile player, so switching to the
  desktop site disables the gesture and restores an open miniplayer). The
  gestures follow the touch through `touchmove` and also complete on `touchcancel` (which
  the WebView fires when it takes the gesture over), resolve the player through the event's
  composed path so touches inside the player's shadow DOM count, scale their distance
  threshold with the viewport and fall back to the mobile fullscreen control or the
  Fullscreen API when the desktop player button is absent.
- **Foldables** – fold/unfold posture changes (`screenLayout`, `smallestScreenSize`,
  `density`) are handled by the activity instead of recreating it, so the page, its playback
  and the injected gesture handlers survive folding. The cleanup injections never touch the
  comments section, which on large screens is rendered inside an engagement panel that looks
  like the shelves they remove.

## Project layout

```
app/src/main/java/com/skystream/ssyoutube/
  MainActivity.java   WebView setup, cookie persistence, request interception
  AdBlocker.java      URL-based ad/tracker blocklist (pure Java, unit tested)
  SiteScope.java      Which URLs stay inside the app (pure Java, unit tested)
  Preferences.java    Theme/site-mode values, user agents, home URLs (pure Java, unit tested)
  NavigationHistory.java  Browser-like back/forward step calculation (pure Java, unit tested)
app/src/test/java/... JUnit tests for AdBlocker, SiteScope, Preferences and NavigationHistory
```

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 34); minSdk is 21.

```bash
gradle assembleDebug   # build the APK
gradle test            # run the JVM unit tests
```
