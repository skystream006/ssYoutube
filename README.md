# ssYouTube

An Android app that uses the YouTube website for browsing and a native Media3 player
with NewPipeExtractor for video playback. The browsing session stays signed in;
playback extraction is separate and anonymous.

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
  **Hide related videos** and **Hide header** are independent button toggles on the same
  row, always available regardless of site mode or page. **Hide header** hides the desktop
  masthead or mobile header bar and collapses its reserved vertical space. Both choices
  are saved and reapplied after navigation or reloads; toggle them off to restore the
  hidden elements and their spacing.
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
- **Native video playback** – watch links, Shorts, live-video links, embedded-video links,
  and `youtu.be` links open in a Media3 player. NewPipeExtractor resolves the media directly,
  off the UI thread, without a third-party extraction proxy. The native player provides
  play/pause, seeking, fullscreen, a miniplayer, and explicit retry after extraction/playback
  failures. Timestamp links are supported. Leaving the activity pauses playback; closing
  the player cancels pending extraction, and destroying the activity releases its resources.
  The same native player is used across fullscreen and miniplayer transitions.
  Stream selection is automatic, supporting progressive video, separate video/audio tracks,
  and available HLS/DASH manifests; there is no manual quality selector.
- **Browsing-only WebView** – the site remains available for search, channels, comments,
  and account interactions. Its media playback and previews are disabled, including after
  single-page navigation, so they cannot play alongside the native player. Web player
  containers, placeholders, and their reserved height are collapsed in mobile and desktop
  layouts, leaving the native player as the only video area. Only validated
  YouTube main-frame navigation can select native playback; sign-in pages do not receive
  the playback bridge. The request blocklist, ad-JSON pruning, ad-hiding/cleanup scripts,
  and adblock-warning bypass have been removed. Feed ads and shopping promotions may
  therefore appear; this is not a network/tracker blocker.
- **Playback limitations** – extracting a content stream avoids the web player's ad
  scheduling; it does not guarantee that YouTube will always supply a playable stream.
  YouTube changes, region restrictions, bot checks, and unavailable videos can prevent
  extraction. Signed-in cookies are never passed to the extractor: private, purchased,
  members-only, or age-restricted content may not play even when accessible in the website.
  Website playlist autoplay and web-player-specific controls are not provided by the native player.
  There is no automatic fallback to YouTube's web player. Creator-embedded sponsorships
  are part of the video and are not removed.
- **Native watch history** – while playing, native position, duration, and observed watched
  ranges are reported to YouTube approximately every 10 seconds, with a final best-effort
  update on pause, close, or video change. Seeking, buffering, and paused time are not counted as watched
  ranges. Requests use the matching video's page-provided playback/watchtime URLs and the
  WebView's signed-in session; account cookies and tracking tokens never enter the extractor
  or native media transport and are not logged or persisted by this feature.
  Same-page miniplayer browsing can continue reporting only with a previously validated
  video context and unchanged account context. Reloads, missing/stale page metadata, account
  changes, network failures, and YouTube's history settings can prevent synchronization.
  These are undocumented YouTube endpoints: saving history/resume position is best-effort,
  not guaranteed, particularly for live/DVR timelines.
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
  statistics panel is not used by the native player.
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
- **Player presentation** – native controls switch between fullscreen and a compact
  miniplayer in both mobile and desktop browsing modes. Tap the video to toggle controls;
  double-tap its left/right side to seek back/forward 10 seconds. Swipe up to enter
  fullscreen, and down to exit fullscreen or minimize a docked player. Tap or swipe up on
  the miniplayer to expand it; swipe down to dismiss it. Buttons and the seek bar remain
  available, including for accessibility. Gestures resize the same native player; there
  is no second-WebView miniplayer.
- **Foldables** – fold/unfold posture changes (`screenLayout`, `smallestScreenSize`,
  `density`) are handled by the activity instead of recreating it, so the page and native
  playback survive folding. The cleanup injections never touch the
  comments section, which on large screens is rendered inside an engagement panel that looks
  like the shelves they remove.

## Project layout

```
app/src/main/java/com/skystream/ssyoutube/
  MainActivity.java   Browsing WebView, trusted playback routing, native player presentation
  NativePlayerView.java     Media3 playback, controls, lifecycle and extraction coordination
  NativeStreamExtractor.java  NewPipeExtractor media selection
  ExtractorDownloader.java    Bounded HTTPS extraction requests without browser cookies
  PlaybackRequest.java        Validated video links and timestamps (pure Java, unit tested)
  NativePlaybackScript.java   Web media suppression and same-page navigation notifications
  NativeNetworkPolicy.java    HTTPS host/redirect and anonymous-cookie restrictions
  NativeHttpsDataSource.java  Validated native media transport
  NativePlaybackState.java    Playback intent and lifecycle state (pure Java, unit tested)
  NativePlayerGestures.java   Native tap/swipe decisions and seek bounds (pure Java, unit tested)
  NativeWatchHistoryState.java  Observed playback ranges and ten-second reporting cadence
  NativeWatchHistoryScript.java  Page-scoped, best-effort YouTube history reporting
  SiteScope.java      Which URLs stay inside the app (pure Java, unit tested)
  Preferences.java    Theme/site-mode values, user agents, home URLs (pure Java, unit tested)
  NavigationHistory.java  Browser-like back/forward step calculation (pure Java, unit tested)
  StatsMonitor.java  Lifecycle-aware background resource sampling
  StatsValues.java   Network rates and safe storage traversal (pure Java, unit tested)
  Logger.java        Opt-in asynchronous logging, sharing and clearing
  LogFormat.java     Privacy-conscious diagnostic formatting (pure Java, unit tested)
  LogStore.java      Bounded rotation and export snapshots (pure Java, unit tested)
app/src/test/java/... JUnit coverage for URL routing, page scripts, preferences and app services
```

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 34); minSdk is 21.
Dependencies resolve from Google Maven, Maven Central, and a restricted JitPack repository
for NewPipeExtractor and its nanojson dependency. Network access to these repositories
is required for a fresh build.

```bash
gradle assembleDebug   # build the APK
gradle test            # run the JVM unit tests
```

### Third-party licensing

Playback uses [AndroidX Media3](https://github.com/androidx/media) (Apache-2.0) and
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0-or-later).
NewPipeExtractor's GPL obligations apply when distributing a combined app: provide the
app's complete corresponding source, a copy of the [GPL](https://www.gnu.org/licenses/gpl-3.0.txt),
applicable license notices, and GPL-compatible
distribution terms. Check the pinned dependencies' licenses before publishing APKs;
adding a dependency does not itself grant rights to redistribute unrelated code.
No upstream extractor implementation is copied into this repository.

Merging a pull request does not publish a GitHub Release. To publish manually,
open **Actions → Manual APK Release → Run workflow** and select
**main**. This separate, manual-only workflow builds the latest `main` commit.
Versions are derived from Git history, so nothing is bumped or committed: with `N` first-parent
commits on `HEAD`, `versionCode` is `N + 1` and `versionName` is `1.0.NN` (e.g. `v1.0.01`,
zero-padded to at least two digits). Builds therefore require full history
(`git fetch --unshallow`).
It runs the tests, builds with the same signing key as the normal APK workflow,
and uploads the APK as a workflow artifact. If the version's GitHub Release does
not exist, it publishes one as the latest release for in-app updates; existing
releases and their assets are left unchanged. Runs on other branches are skipped.
