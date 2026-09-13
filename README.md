# ssYouTube

A minimal Android app that shows the YouTube **mobile** website in a full-screen WebView,
keeps you signed in, and blocks advertising/tracking requests.

## Features

- **YouTube mobile only** – loads `https://m.youtube.com/` with a mobile user agent.
  Links to other sites are blocked from opening outside the app (`SiteScope`), while
  YouTube and the Google sign-in domains stay in-app.
- **YouTube link handling** – the app can be selected as the default handler for
  YouTube web links and opens incoming YouTube links directly in the WebView.
  In Preferences, tap **Set as default for YouTube links** to open Android's supported-link
  settings for ssYouTube (or app info on older devices). Enable **Open supported links**
  and select the YouTube addresses if shown. Android requires user confirmation; the app
  cannot change these defaults itself. If another app still opens the links, clear its
  link defaults first.
- **Persistent sign-in** – cookies are accepted (including third-party cookies needed by
  the Google account flow) and flushed to disk on pause, and DOM storage is enabled, so
  the session survives app restarts.
- **Preferences** – a floating settings button appears on the YouTube home page (the page
  with the bottom navigation bar). It opens a preferences dialog with icon buttons for
  back/forward/reload/home navigation, plus the theme (system/light/dark) and the site mode
  (mobile or desktop, `Preferences`), mirroring a browser's "desktop site" toggle.
  **Hide related videos** is always available in the panel, regardless of site mode or page.
  Back and forward behave like a browser's buttons: repeated history entries for the same
  page (created by the YouTube single page app) are skipped so every press changes page
  (`NavigationHistory`).
- **In-app updates** – on launch, checks this repository's latest GitHub Release and
  announces newer versions without downloading. In Preferences, **Check for updates**
  reports when the app is current, or downloads a newer APK to app-private cache and opens
  Android's installer. Android 8+ may first ask you to allow installs from ssYouTube;
  return to the app to continue. Installation always requires Android's confirmation.
  Downloads are checked for the expected version, a higher Android version code, matching
  package name and signing certificate. Network/release failures can be retried in Preferences.
  Only **Manual APK Release** publishes GitHub Releases for in-app updates;
  automatic main-branch and pull-request builds upload workflow artifacts, not releases.
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
- **Stats for nerds** – an optional, touch-through overlay in Preferences shows app-process
  memory (PSS), app-UID download/upload rates per second, and app data plus cache storage,
  matching ssMusic's diagnostics. Memory and network refresh about once a second while
  the activity is visible; storage is scanned off the UI thread about every 30 seconds,
  including internal, device-protected and external app directories without double counting.
  Measurements start with “Measuring…” and unsupported values show “Unavailable.”
  Separate WebView renderer memory/traffic may not be included. YouTube's own playback
  statistics panel remains separate.
- **Debug logging** – disabled by default and enabled in Preferences. Activity, navigation,
  settings and WebView failures are recorded to logcat and app-private rotating files
  (512 KiB each, one backup). Routine events include their caller; errors and crashes
  include stack frames without exception messages. URLs retain only their web origin,
  excluding credentials, paths, queries and fragments. Files are excluded from Android
  backup. **Share log** exports the current and previous logs through a temporary read-only
  FileProvider grant; **Clear log** deletes both logs and the export. Disabling stops new
  logging but retains existing files and lets queued entries finish. Logging is best effort:
  a bounded queue drops entries under heavy load. Review logs before sharing; copies
  already shared with other apps cannot be recalled.
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
  StatsMonitor.java  Lifecycle-aware background resource sampling
  StatsValues.java   Network rates and safe storage traversal (pure Java, unit tested)
  Logger.java        Opt-in asynchronous logging, sharing and clearing
  LogFormat.java     Privacy-conscious diagnostic formatting (pure Java, unit tested)
  LogStore.java      Bounded rotation and export snapshots (pure Java, unit tested)
app/src/test/java/... JUnit tests for AdBlocker, SiteScope, Preferences and NavigationHistory
```

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 34); minSdk is 21.

```bash
gradle assembleDebug   # build the APK
gradle test            # run the JVM unit tests
```

Merging a pull request does not publish a GitHub Release. To publish manually,
open **Actions → Manual APK Release → Run workflow** and select
**main**. This separate, manual-only workflow builds the latest `main` commit using
the existing `versionCode` and `versionName` without bumping or committing them.
It runs the tests, builds with the same signing key as the normal APK workflow,
and uploads the APK as a workflow artifact. If the version's GitHub Release does
not exist, it publishes one as the latest release for in-app updates; existing
releases and their assets are left unchanged. Runs on other branches are skipped.
