# ssYouTube

An Android app that uses the YouTube website for browsing and an embedded mpv player
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
  back/forward/reload/home navigation, plus **Default language** above **Appearance**,
  the theme (system/light/dark) and the site mode
  (mobile or desktop, `Preferences`), mirroring a browser's "desktop site" toggle.
  Default language starts as **English** and is saved across app restarts. New video loads,
  retries, and restored playback use it for extraction and prefer matching audio tracks
  (including regional variants); manifest playback uses the same audio-language preference.
  If that language is unavailable, playback falls back to the original or available audio.
  This does not translate videos or change the app UI; changing it does not interrupt a
  video that is already playing.
  **Enable mpv player** is on by default and saved across restarts. Turn it off to use
  YouTube's web player instead. Switching stops native playback (including the miniplayer
  and pending extraction) and reloads the current page; turn it back on to use mpv again.
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
  Android's installer. While downloading, Preferences shows an inline progress bar and
  downloaded/total KB, using the release asset size even if the HTTP response omits its
  length. Progress stays current when you reopen Preferences or recreate the activity;
  it disappears when the update finishes or fails. Android 8+ may first ask you to allow
  installs from ssYouTube; return to the app to continue. Installation always requires
  Android's confirmation.
  Downloads are checked for the expected version, a higher Android version code, matching
  package name and signing certificate. Network/release failures can be retried in Preferences.
  Only **Manual APK Release** publishes GitHub Releases for in-app updates;
  automatic main-branch and pull-request builds upload workflow artifacts, not releases.
- **Music server** – the compact, shaded row in Preferences connects to
  [ssYTDLP_Server](https://github.com/skystream006/ssYTDLP_Server).
  Tap **Login to Music Server**, enter the server's configured HTTPS passkey origin
  (including its port), and authorize with your passkey in your external browser.
  Return to ssYouTube after approval; your YouTube sign-in is not shared with the server.
  Use an existing approved server account and a certificate trusted by both Android
  and the browser. Cleartext HTTP and certificate-validation bypasses are not supported.
  Browser login uses the server's `com.ssytdlp.app:/oauth/callback` redirect, a random
  state and S256 PKCE. If Android asks which app should handle the return link,
  choose ssYouTube; the server uses a shared private-use scheme, not a verified app link.
  Pending login details and the resulting session are encrypted
  using Android Keystore and excluded from backup. Sessions survive app restarts;
  expired or revoked sessions require a new login. Retry or cancel an unfinished login
  from the same row. The × button forgets the local session, not the browser's login;
  it does not revoke the session on the server.
  After login, **Send playlist** submits the current page URL to `POST /api/jobs`
  as an audio job; this button is hidden unless the page has a nonempty `list` parameter.
  **Send media** removes playlist parameters before submitting just the current video
  (including Shorts). When browsing elsewhere with a native miniplayer, it sends that
  player's video instead. It is disabled when no media is selected.
  Both actions show success/failure inline and disable repeat submissions while busy.
  On a network failure, check the server's job list before retrying: the request may
  have arrived even when its response did not.
- **Native video playback** – with **Enable mpv player** on, watch links, live-video links, embedded-video links,
  and `youtu.be` links open in an embedded libmpv player, using the same `loadfile` network-URL
  operation as mpv-android's **Open URL**. No external player app is launched or required.
  NewPipeExtractor resolves the media directly, off the UI thread, without a third-party
  extraction proxy. A YouTube watch/share URL is a webpage, not a media stream: mpv-android's
  Open URL does not replace this extraction step. Media3 supplies the existing controls,
  not the playback engine; ExoPlayer is no longer used. The native player provides
  play/pause, seeking, fullscreen, a miniplayer, and explicit retry after extraction/playback
  failures. Timestamp links are supported. Leaving the activity pauses playback; closing
  the player cancels pending extraction, and destroying the activity releases its resources.
  The same native player is used across fullscreen and miniplayer transitions.
  Stream selection is automatic, supporting progressive video, separate video/audio tracks,
  and available HLS/DASH manifests; there is no manual quality selector.
  Separate audio is appended with mpv's `change-list audio-files append` command after
  initialization and before `loadfile`; CLI-only list suffixes are not libmpv option names.
  Each selected video gets its own mpv handle; fullscreen/miniplayer transitions retain
  that handle and only change its surface size. Closing or replacing a video destroys its
  handle, and callbacks from that handle cannot update the replacement.
- **Shorts in the WebView** – `/shorts` pages stay on YouTube's web player, including
  incoming Shorts links and single-page navigation. Entering Shorts closes any native
  playback so the two players do not play together.
- **Browsing WebView** – the site remains available for search, channels, comments,
  and account interactions. With mpv enabled, outside Shorts, web media playback and previews are disabled,
  including after single-page navigation. Web player containers, placeholders, and their
  reserved height are collapsed in mobile and desktop layouts outside Shorts, leaving the
  native player as the only video area on those pages. Only validated
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
  There is no automatic fallback to YouTube's web player for native playback; disable
  **Enable mpv player** in Preferences to switch manually. Shorts and mpv-disabled playback use
  the website's playback behavior, including its ads. Creator-embedded sponsorships
  are part of the video and are not removed.
  Extracted URLs are restricted to HTTPS YouTube/Googlevideo hosts before opening.
  libmpv/FFmpeg handles media redirects and manifest requests, rather than the former Java
  media transport: its protocol allowlist excludes directly opened local files and cleartext
  HTTP URLs, but native redirects do not apply the extractor's per-redirect HTTPS/host
  policy or response-size limits.
  TLS verification uses the library's bundled CA certificates. Browser cookies, user
  configuration/scripts, external URL extractors and mpv URL-bearing logs are disabled.
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
  NativePlayerView.java     Controls, lifecycle and extraction coordination
  MpvPlayer.java              Embedded libmpv and Media3 controls/timeline adapter
  MpvPlaybackRequest.java     Validated network-URL commands and restricted mpv options
  NativeStreamExtractor.java  NewPipeExtractor media selection
  ExtractorDownloader.java    Bounded HTTPS extraction requests without browser cookies
  PlaybackRequest.java        Validated video links and timestamps (pure Java, unit tested)
  NativePlaybackScript.java   Web media suppression and same-page navigation notifications
  NativeNetworkPolicy.java    Extractor HTTPS/redirect and anonymous-cookie restrictions
  NativePlaybackState.java    Playback intent and lifecycle state (pure Java, unit tested)
  NativePlayerGestures.java   Native tap/swipe decisions and seek bounds (pure Java, unit tested)
  NativeWatchHistoryState.java  Observed playback ranges and ten-second reporting cadence
  NativeWatchHistoryScript.java  Page-scoped, best-effort YouTube history reporting
  SiteScope.java      Which URLs stay inside the app (pure Java, unit tested)
  Preferences.java    Theme/site-mode values, user agents, home URLs (pure Java, unit tested)
  NavigationHistory.java  Browser-like back/forward step calculation (pure Java, unit tested)
  MusicServer.java    Lifecycle-aware browser login and music job coordination
  MusicServerProtocol.java  PKCE, callback validation and playlist/media URL selection
  MusicServerClient.java    Bounded HTTPS token exchange and audio job submission
  MusicServerStore.java     Keystore-protected, backup-excluded login/session storage
  MusicServerCallbackActivity.java  Routes browser returns to the existing app task
  StatsMonitor.java  Lifecycle-aware background resource sampling
  StatsValues.java   Network rates and safe storage traversal (pure Java, unit tested)
  Logger.java        Opt-in asynchronous logging, sharing and clearing
  LogFormat.java     Privacy-conscious diagnostic formatting (pure Java, unit tested)
  LogStore.java      Bounded rotation and export snapshots (pure Java, unit tested)
app/src/test/java/... JUnit coverage for URL routing, page scripts, preferences and app services
```

## App icons

`file_00000000c28481f6bff0eb7c51226dbd.png` is the source artwork for the launcher
and monochrome icons. `@mipmap/ic_launcher` supplies density-specific legacy icons,
adaptive icons on Android 8+, and a monochrome layer for Android 13+ themed icons.
The full-color launcher artwork uses a dark pink and black palette.
The adaptive foreground and monochrome artwork fill the launcher's visible area
without an extra inset. Only Android's off-screen adaptive-icon buffer remains;
the launcher still applies its circle, squircle, or other shape mask.

`@drawable/ic_notification` provides 24 dp small-notification icons at mdpi through
xxxhdpi, derived from the same artwork as white shapes on a transparent background
(not an opaque grayscale image). Use it for notification small icons rather than
the full-color launcher icon. The app does not currently post Android notifications.
The WebView's light/dark header logos are separate and unchanged.

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 34); minSdk is 21.
Dependencies resolve from Google Maven, Maven Central, and a restricted JitPack repository
for NewPipeExtractor and its nanojson dependency. Network access to these repositories
is required for a fresh build.

The upstream mpv-android app is not an importable AAR. The pinned
[`mpv-android-lib:0.1.12`](https://github.com/abdallahmehiz/mpv-android/tree/v0.1.12)
library packages its mpv-android-based JNI wrapper, libmpv/FFmpeg, CA bundle and native
dependencies for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`. These are resolved from
Maven Central at build time, not downloaded as executable code at runtime. This library
preserves API 21 support without changing the app's SDK requirements; it increases APK size.
Kotlin coroutines are explicitly included because the wrapper uses them internally.
When updating the library, update the versioned CA cache filename in `MpvPlayer` too.

```bash
./gradlew assembleDebug   # build the APK
./gradlew test            # run the JVM unit tests
./gradlew lintDebug       # Android lint
```

Device verification should cover muxed/adaptive audio and video, live HLS/DASH, timestamp
links, seek/replay, rapid video changes, pause/resume, audio-focus/headphone interruptions,
fullscreen/miniplayer resizing, activity recreation, network failure/retry and close while
loading. Verify the mpv toggle persists after restart/recreation, stops a playing or loading
miniplayer when disabled, restores web playback on mobile/desktop and after navigation,
and resumes native routing when re-enabled. Native rendering/decoding and ABI compatibility
cannot be verified by JVM tests alone.

For Music Server, device-check browser authorization, cancellation/retry, return after
activity/process recreation, persistence after restart, and expired/revoked logins.
Verify playlist versus single-media jobs, Shorts and miniplayer selection, and the
compact row in light/dark themes at narrow widths and large font sizes. Android
Keystore and browser/passkey interaction require device testing.
Credential files use AES-GCM with a fresh Keystore-wrapped key per write: RSA-OAEP
on Android 6+, or RSA PKCS#1 wrapping on Android 5, whose Keystore cannot decrypt
OAEP. The versioned legacy format remains readable after an OS upgrade. CodeQL
flags that compatibility path; it only decrypts app-private, backup-excluded
files, never remote ciphertext, and exposes no padding-error oracle.

### Third-party licensing

Controls use [AndroidX Media3](https://github.com/androidx/media) (Apache-2.0).
Playback uses [mpv-android-lib](https://github.com/abdallahmehiz/mpv-android) (MIT wrapper,
derived from [mpv-android](https://github.com/mpv-android/mpv-android)), its GPL-enabled
libmpv/FFmpeg native stack, and
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0-or-later).
The wrapper's MIT license does not relicense the native libraries. GPL obligations
apply when distributing the combined app: provide the
app's complete corresponding source, a copy of the [GPL](https://www.gnu.org/licenses/gpl-3.0.txt),
applicable license notices, and GPL-compatible
distribution terms. Check the pinned dependencies' licenses before publishing APKs;
adding a dependency does not itself grant rights to redistribute unrelated code.
No upstream extractor or mpv implementation is copied into this repository; implementations
are supplied by dependencies. Preserve their license notices and provide corresponding
source, including the native library sources/build inputs, when distributing APKs.

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
