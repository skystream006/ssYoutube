# Browsing, links, and page customization

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Class names identify files in those directories; resolve against the actual
checkout root if different.

## Find the smallest entry point

| Request | Source symbols |
| --- | --- |
| In-app URL scope / Google sign-in | `SiteScope.normalizeInAppUrl`, `MainActivity.configureWebView`, `YouTubeWebViewClient.handleUrl` |
| Incoming/shared YouTube links | `MainActivity.onNewIntent`, `startUrl`, `canonicalPlaybackUrl`; `PlaybackRequest.fromUrl`; `MusicServerProtocol.preservePlaylist` |
| Back/forward skips duplicate SPA entries | `NavigationHistory.backSteps`, `forwardSteps`; `MainActivity.navigate`, `historyUrls` |
| Header/related visibility | `MainActivity.headerVisibilityScript`, `relatedVisibilityScript`, `applyHeaderVisibility`, `applyRelatedVisibility` |
| Playables, posts, subscriber counts | `COMMENTS_SELECTOR`, `COMMENTS_HELPER_SCRIPT`, `PLAYABLES_CLEANUP_SCRIPT`, `POSTS_CLEANUP_SCRIPT`, `SUBSCRIBER_COUNT_SCRIPT` |
| Branding / reinjection | `APP_LOGO_SCRIPT`, `isAppLogoRequest`, `appLogoBytes`, `scheduleAppLogoReinjection`, `appLogoResponse` |
| Reload / SPA integration | `YouTubeWebViewClient.onPageStarted`, `onPageFinished`, `doUpdateVisitedHistory`, `applyPageScripts` |

Registration lives in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`
(`MainActivity` launcher and YouTube intent filters). Branding assets live in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable` and density-specific
drawable/mipmap directories; do not open binary assets for unrelated behavior.

## Architecture and invariants

- One browsing WebView retains sign-in through accepted cookies and DOM storage;
  pause/page completion flush cookies. Do not replace WebView networking with
  native requests or loosen file/content access restrictions.
- `SiteScope` governs broad browsing/sign-in scope. Native playback and injected
  scripts have stricter page/origin checks; these policies are not interchangeable.
- Only trusted main-document routing may select native playback. The legacy URL
  callback cannot identify frames and must not normalize subframes with `loadUrl`.
- Preserve timestamp and playlist information through link canonicalization.
  Playlist preservation is owned by music; playback URL parsing is owned by playback.
- YouTube is a single-page application: navigation/history/page callbacks and
  idempotent reinjection matter, not just the first document load.
- Cleanup/decorating scripts exclude comments and engagement panels; broad DOM
  selectors can otherwise remove comment content.
- Subscriber lookups remain limited and queued. Logo interception is distinct
  from adblocking and serves the selected theme's local image.

## Targeted validation and escalation

| Changed behavior | Tests to start with |
| --- | --- |
| URL scope | `SiteScopeTest` |
| Navigation history | `NavigationHistoryTest` |
| Theme/site-mode URLs or page predicates | `PreferencesTest` |
| Cleanup, header/related, logo, trusted routing | Relevant `MainActivityPageScriptTest` methods |
| Playback link acceptance/canonicalization | `PlaybackRequestTest` plus routing cases in `MainActivityPageScriptTest` |
| Playlist preservation | Relevant `MusicServerProtocolTest` cases |

Use [validation commands](../../repo-router/references/validation.md).
Device-check the affected mobile/desktop pages, SPA transitions, reloads, incoming
links, and back/forward behavior. For selector/scope changes, verify comments and
sign-in still work. JVM string assertions do not execute the live site's DOM.

Consult [playback](../../playback/SKILL.md) for bridge acceptance changes and
[music-server](../../music-server/SKILL.md) for changed playlist semantics.
Do not load their extraction, credentials, or transport references for a visual
header change.

## Documentation

[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features):
**YouTube link handling**, **Persistent sign-in**, **No Playables**, **No Posts
shelf**, and **Subscriber counts** describe the user-facing contracts.
