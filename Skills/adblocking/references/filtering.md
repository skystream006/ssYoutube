# Web filtering layers

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
JVM tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Use the actual absolute checkout root if different.

## Source and test map

| Layer | Source | Starting tests |
| --- | --- | --- |
| Network/exception/cosmetic rule decisions | `AdBlockEngine.read`, `shouldBlock`, `cosmeticCss`, `isSupportedPage` | `AdBlockEngineTest` |
| Cosmetic stylesheet generation | `AdBlockingScript.create` | `AdBlockingScriptTest` |
| WebView/service-worker/script lifecycle | `WebAdBlocker.setEnabled`, `intercept`, `onPage`, `installScript`, `updateServiceWorker`, `destroy` | Related engine/script tests plus device checks |
| Effective preference and reload | `Preferences.isAdBlockingEnabled`; `MainActivity.updateAdBlocking`, `setAdBlockingEnabled`, `setMpvEnabled` | Relevant `PreferencesTest`, `PreferencesLayoutTest` cases |
| Video/player-response filtering | Bundled JavaScript below | Node suite below; `YouTubeVideoAdBlockingTest` is its JVM wrapper |

Resources and executable tests:
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/raw/adblocking_rules.txt`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/raw/youtube_video_adblock.js`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/test/js/youtube_video_adblock.test.cjs`.

## Rules and behavior

- Rules are bundled `action|domain|value` entries, not ABP/uBO/EasyList syntax.
  `block`/`allow` use literal path prefixes and dot-boundary domain matching;
  allow rules win. `hide` uses scoped CSS selectors. Preserve resource bounds.
- Rules/scripts load off the UI thread when needed. No remote filter lists or
  executable scripts are downloaded.
- Effective filtering is `!mpvEnabled && savedAdblockingChoice`. Enabling mpv
  disables all filtering, including Shorts, without erasing that saved choice.
- Filter only subrequests of supported HTTPS YouTube documents. Main-frame
  navigation and Google sign-in remain outside filtering. Return null for allowed
  requests so WebView retains cookies, TLS, and transport; never proxy or replay.
- Preserve media, captions, live metadata, comments, thumbnails, history, account
  flows, and app-logo handling. Avoid broad Googlevideo rules.
- Video filtering removes known ad scheduling and explicitly marked ad entries;
  it must preserve content payloads and browser response contracts.
- Fallback may click only visible, enabled skip controls in explicitly ad-playing
  players. Never seek, accelerate, mute, or start media to bypass stale ad UI.
- Remove installed hooks/timers when disabled. Keep the page reload that clears
  already-filtered data/styles. Service-worker ownership must not outlive the
  owning blocker; old WebView providers may use late page-callback injection.

## Validation and escalation

Use [validation commands](../../repo-router/references/validation.md).
For JavaScript changes execute the actual bundled-script Node fixtures. They cover
initial/SPA responses, playlists, live metadata, Shorts, JSON/XHR/Response behavior,
origin guards, cleanup, and guarded skipping; no npm dependencies are needed.

Device-check the affected mobile/desktop/Shorts page, SPA transition and reload.
Verify normal playback/captions/comments/sign-in/history/logo remain functional.
Toggle adblocking off, toggle mpv on, and restore mpv off to verify teardown and
saved-choice restoration. Fixtures cannot establish live-site compatibility.

`PlaybackRequest.isYouTubePage` and `NativePlaybackScript.ORIGIN_RULES` are
playback-owned consumers' contracts, not reasons to read the whole player.
Consult shell only for affected activity wiring. Keep transport/authentication
and release code out of scope.
