# Native routing, web suppression, and history

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Resolve against the actual absolute checkout root if different.

## Choose one slice

| Slice | Source anchors |
| --- | --- |
| Link parsing/timestamps/Shorts | `PlaybackRequest.fromUrl`, `isYouTubePage`, `isShortsPage` |
| Trusted web-to-native notification | `MainActivity.installPlaybackRouting`, `isTrustedPlaybackMessage`, `playbackOrigin`, `routePage` |
| Web-player suppression and SPA notifications | `NativePlaybackScript.SCRIPT`, `ORIGIN_RULES`, `forPreference` |
| mpv toggle transition | `MainActivity.updateNativePlaybackScript`, `setMpvEnabled`, `closeNativePlayback` |
| Watch-range sampling | `NativeWatchHistoryState.sample`, `onContinuityToken`; `MainActivity.updateWatchHistory` |
| Page-scoped reporting | `NativeWatchHistoryScript.SCRIPT`, `update` |

## Routing and suppression

- Only validated YouTube main-frame messages may route native playback. Validate
  current and reported pages/origins; broad browsing scope is not bridge permission.
- Shorts remain on the web player and close native playback. Other browsing can
  retain the current video as a miniplayer.
- Duplicate SPA notifications must not restart or unpause playback; explicit close
  suppresses the current video until the route changes appropriately.
- Document-start support is capability-checked; page callbacks provide fallback
  injection. Keep script installation idempotent.
- Toggling mpv stops native work, updates document-start registration, and reloads
  the page. Removing a ScriptHandler alone does not undo existing media overrides.
- `ORIGIN_RULES` and page predicates have shell/adblocking consumers. Check those
  consumers when changing their acceptance contract.

## History privacy and semantics

- `NativeWatchHistoryState` samples actual watched ranges; seeks, buffering, and
  paused time are not watched time. Reports are periodic with best-effort final flush.
- Tracking URLs are obtained only from metadata matching the current video's page.
  Keep URLs/tokens in page memory, bound to video, native session, and account.
- Recheck account/context identity before queued delivery. SPA navigation, account
  changes, reloads, stale metadata, and outgoing sessions must not cross contexts.
- Use the WebView's signed-in requests for history; do not transfer browser cookies
  or tracking URLs into extraction, native transport, logs, or persistent storage.
- History is best-effort against undocumented endpoints, especially for live/DVR.
  Do not promise successful synchronization from unit assertions alone.

## Validation

| Changed slice | Tests |
| --- | --- |
| Parsing | `PlaybackRequestTest` |
| Bridge acceptance/canonicalization/close suppression | Relevant `MainActivityPageScriptTest` cases |
| Web suppression/toggle script | `NativePlaybackScriptTest`; preferences tests if gating changes |
| History ranges and continuity | `NativeWatchHistoryStateTest` |
| Page context, origin, and account checks | `NativeWatchHistoryScriptTest` |

Use [validation commands](../../repo-router/references/validation.md).
Device-check affected watch/Shorts/SPA/incoming-link routes, sign-in pages,
mpv toggle and reload, and close/miniplayer transitions. For history changes,
check pause/seek/video switch/account switch/reload without inspecting or
persisting private tracking values. Consult shell for integration and adblocking
only when shared page rules or effective enablement change.
