# Scoped rules and video-ad filtering

## Source and consumers

- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/WebAdBlocker.java`: `setEnabled`, `intercept`, `onPage`, `installScript`, `updateServiceWorker`, `destroy`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/AdBlockEngine.java`: immutable `read`, `shouldBlock`, `cosmeticCss`, `isSupportedPage`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/AdBlockingScript.java`: `create`; stylesheet `ssyoutube-adblocking`, idempotent `__ssyoutubeAdBlocking`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/raw/adblocking_rules.txt`: `R.raw.adblocking_rules`, versioned local rules.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/raw/youtube_video_adblock.js`: `R.raw.youtube_video_adblock`; `prune`, `playerApi`, `update`, `__ssyoutubeVideoAdBlocking.stop`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java`: `updateAdBlocking`, `setAdBlockingEnabled`, `setMpvEnabled`, `showPreferences`; `YouTubeWebViewClient` request/page/history callbacks.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/Preferences.java`: `isAdBlockingEnabled` requires `!mpvEnabled && adBlockingEnabled`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/PlaybackRequest.java`: `isYouTubePage`; `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativePlaybackScript.java`: `ORIGIN_RULES`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`: `mpv_toggle`, `adblocking_settings`, `adblocking_toggle`.

## Preserve these boundaries

Saved `adblocking_enabled` defaults false. Enabling mpv hides the entire settings row and stops filtering, including Shorts, without erasing the saved choice. Both setting changes reload. Resources load off-thread on first enable; failed loading leaves browsing working. Document-start/service-worker hooks are feature-gated; page callbacks provide late injection, which can miss already-scheduled ads. `destroy` removes owned hooks/work.

Supported documents are HTTPS `youtube.com`, `www.youtube.com`, `m.youtube.com`, and `music.youtube.com`, default port only. Main-frame navigation, sign-in pages, and other documents bypass filtering. `intercept` checks the current document, then uses `Referer` when supplied for scoped matching. Do not confuse document scope with request parsing: `AdBlockEngine.webUri` accepts HTTP or HTTPS subresources. Allowed requests return `null`; blocked ones receive empty HTTP 200, `Cache-Control: no-store`. Never proxy/replay requests, transfer cookies, change TLS, or block Googlevideo broadly. App-logo interception precedes adblocking.

Rules are `action|domain|value`, not ABP/uBO syntax. `block`/`allow` use parsed hosts with dot-boundary subdomains and literal case-sensitive path prefixes; allow wins regardless of order. `hide` supplies scoped CSS selectors. Preserve bounded parsing, rejection of unsafe syntax, and no downloaded lists/scripts. Never log/persist browsing URLs or cookies. Cosmetics repair their stylesheet after DOM/SPA changes without player/network hooks.

Video JavaScript removes only known scheduling keys (`playerAds`, `adPlacements`, `adSlots`, `adBreakHeartbeatParams`) and explicitly ad-marked Shorts entries. Keep traversal bounded to player envelopes; preserve streams, captions, live metadata, history, JSON errors/revivers, and response metadata. Response/XHR hooks scope `playerApi` to supported player/next/reel endpoints. The fallback clicks only visible enabled skip buttons within `.ad-showing`/`.ad-interrupting` players: never seek, accelerate, mute, or start content. `stop` restores owned hooks/timers; reload clears already-mutated data and cosmetics. No anti-adblock bypass or sponsorship removal is promised.

## Validation and escalation

Tests live in `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`: `AdBlockEngineTest` (notably `neverFiltersMainFrameNavigation`, `preservesPlaybackSignInHistoryThumbnailsCommentsAndTheLogo`), `AdBlockingScriptTest`, `PreferencesTest.adblockingRequiresWebPlaybackAndTheSavedOptIn`, `PreferencesLayoutTest.adblockingHasAHiddenPanelBelowMpvOutsideAdvancedSettings`, and `YouTubeVideoAdBlockingTest.videoAdScriptPreservesContentAndBrowserContracts`. The latter executes `/home/runner/work/ssYoutube/ssYoutube/app/src/test/js/youtube_video_adblock.test.cjs`; Node 18+ is required, otherwise JUnit skips it. Fixtures cover SPA/Shorts, untouched browser behavior, hook removal, and guarded skipping. Commands: [shared validation](../../repo-router/references/validation.md).

Device-check mobile/desktop/Shorts, pre-/mid-roll, playlist autoplay, SPA navigation, restart, mpv on/off restoration, and disable/reload. Confirm sign-in, comments, thumbnails, logo, history, captions, seeking/speed, and live playback. Interception cannot observe every cached/media request or redirect.

Escalate UI/lifecycle changes to [android-shell](../../android-shell/SKILL.md), shared predicates only to [playback](../../playback/SKILL.md). User contracts: [bundled rules](/home/runner/work/ssYoutube/ssYoutube/README.md#bundled-adblocking-rules), [features](/home/runner/work/ssYoutube/ssYoutube/README.md#features), [device checks](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test).
