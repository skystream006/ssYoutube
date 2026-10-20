# Browsing, navigation, and page scripts

## Locate the change

| Source | Search anchors |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java` | `configureWebView`, `startUrl`, `onNewIntent`, `YouTubeWebViewClient.handleUrl`, `applyPageScripts`, `navigate` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/SiteScope.java` | `normalizeInAppUrl`, `isInAppUrl` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NavigationHistory.java` | `backSteps`, `forwardSteps`, `sameEntry` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/Preferences.java` | `userAgent`, `homeUrl`, `siteModeUrl`, `isVideoPage` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml` | `MainActivity`, `singleTop`, YouTube `VIEW` intent filters |

For page appearance, search `relatedVisibilityScript`, `headerVisibilityScript`,
`COMMENTS_SELECTOR`, `PLAYABLES_CLEANUP_SCRIPT`, `POSTS_CLEANUP_SCRIPT`,
`SUBSCRIBER_COUNT_SCRIPT`, or `APP_LOGO_SCRIPT` inside MainActivity. Logo serving
uses `isAppLogoRequest`, `appLogoBytes`, and `scheduleAppLogoReinjection`; image
resources are under `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable`.
Read images only for an image-related request.

## Behavior to preserve

- The WebView retains sign-in cookies and DOM storage. Cookie flushing occurs on
  pause/page completion. JavaScript is enabled, but file/content access and
  automatic JavaScript windows are disabled.
- `SiteScope` limits navigation and normalizes supported HTTP links to HTTPS.
  It is not interchangeable with the stricter playback bridge's origin checks.
  Do not broaden one allowlist merely to satisfy a different domain.
- Only main-frame-aware routing may normalize by calling `loadUrl`; the legacy
  URL callback cannot distinguish frames. Incoming links are canonicalized
  without losing timestamps or playlist context.
- YouTube is a single-page application. Scripts must tolerate repeated injection
  and late DOM changes. Playables/Posts cleanup and subscriber decoration must
  preserve comment renderers/engagement panels.
- Header visibility uses a removable stylesheet and site-mode-specific spacing.
  Toggling it off must restore YouTube's layout, not leave destructive inline edits.
- Browser back/forward skips duplicate normalized entries; do not replace this
  with one-step navigation that can appear stuck.

## Consumers and expansion

Playback owns `PlaybackRequest` and trusted native routing: consult
[web bridge](../../playback/references/web-bridge.md) only if those contracts
change. Music owns `MusicServerProtocol.preservePlaylist`, consumed by `startUrl`
and `handleUrl`; consult [jobs](../../music-server/references/jobs.md) for URL
selection changes. Adblocking owns request filtering, not unrelated page cleanup.
Use [preferences](preferences.md) only when state/control wiring changes.
Leave native transport, update networking, and workflows unloaded otherwise.

## Targeted validation

Tests are classes under
`/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.

| Changed behavior | Tests |
| --- | --- |
| Navigation allowlist/normalization | `SiteScopeTest` |
| Duplicate history handling | `NavigationHistoryTest` |
| Site mode/home/watch-page decisions | `PreferencesTest` |
| Cleanup, header/related styles, logo injection | Matching `MainActivityPageScriptTest` methods |
| Native link canonicalization/trust | Playback-related `MainActivityPageScriptTest` methods plus `PlaybackRequestTest` |

Use [common commands](../../repo-router/references/validation.md). Script-text
assertions do not prove real DOM behavior. Device-check mobile/desktop navigation,
SPA transitions, back/forward, comments, theme/logo changes, sign-in, and incoming
links as relevant. Do not run unrelated music/release tests for a cosmetic script.

Documentation: the **YouTube link handling**, **Persistent sign-in**, **No
Playables**, **No Posts shelf**, and **Subscriber counts** entries in
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#features).
