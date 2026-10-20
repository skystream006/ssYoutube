# Web routing and watch history

## Source anchors

- [PlaybackRequest.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/PlaybackRequest.java): `fromUrl`, `trustedUri`, `queryParameters`, `timestampMs`, `isYouTubePage`, `isShortsPage`.
- [NativePlaybackScript.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativePlaybackScript.java): `ORIGIN_RULES`, `SCRIPT`, `forPreference`; JavaScript `update`, `silence`, `restore`.
- [NativeWatchHistoryScript.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativeWatchHistoryScript.java): `TRUSTED_PAGE`, `SCRIPT`, `update`; JavaScript `tracking`, `endpoint`, `account`, `valid`, `drain`.
- [NativeWatchHistoryState.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativeWatchHistoryState.java): `sample`, `onContinuityToken`, `reset`, `SAMPLE_MS`, `REPORT_MS`.
- [MainActivity.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java): `installPlaybackRouting`, `isTrustedPlaybackMessage`, `routePage`, `updateWatchHistory`, `closeNativePlayback`, `setMpvEnabled`; `YouTubeWebViewClient.applyPageScripts`.

## Routing invariants

`PlaybackRequest` accepts exact supported YouTube/shortlink hosts and valid eleven-character video IDs from watch/live/embed/share paths, not Shorts or playlist embeds. Its link parser accepts HTTP/HTTPS default ports; the JavaScript bridge is stricter: four explicit HTTPS YouTube origins, never Google sign-in. Reject ambiguous authorities and duplicate `v`/`t`/`start` parameters. Timestamp precedence is `t`, then `start`, then fragment; malformed or over-seven-day values become zero.

`isTrustedPlaybackMessage` requires the main frame, matching source/current/reported origins, and matching video plus timestamp; nonvideo messages must match the exact current URL. Messages notify about the current page, not clicked targets. `startUrl`, `canonicalPlaybackUrl`, and `YouTubeWebViewClient.handleUrl` normalize incoming links without losing timestamps.

`routePage` closes native playback for Shorts, non-YouTube pages, or disabled mpv; nonvideo YouTube browsing minimizes the existing player. `closedVideoForPage` prevents reopening a dismissed video until leaving its page. Duplicate route keys preserve presentation and playback intent.

`updateNativePlaybackScript` installs document-start suppression when supported; `applyPageScripts` also runs through `onPageStarted`, `onPageFinished`, and `doUpdateVisitedHistory`. Keep installation idempotent and SPA-aware. Outside main-frame Shorts, suppress web media/previews and collapse only player placeholders, not comments/watch content. Restore saved media flags and remove suppression styling on Shorts. `setMpvEnabled` closes pending/native playback, replaces script registration, and reloads all frames; an empty script alone cannot undo old hooks.

## History invariants

`updateWatchHistory` samples native progress each second; `sample` reports observed ranges about every ten seconds and flushes existing ranges on pause/close/video change. Continuity tokens split seeks, playback-state changes, and speed changes; paused, buffering, jumped, or unobserved time is not watched time.

Java sends only validated ID, session number, and measured progress. Tracking URLs/tokens and account context remain page-scoped JavaScript state, never Java persistence, extraction/media inputs, or logs. `tracking` requires matching, nonstale player metadata; `endpoint` validates HTTPS hosts, endpoint paths, and video identity. Requests use the WebView session with redirects rejected.

Same-document miniplayer browsing needs previously validated metadata and unchanged, known account context. Reloads lose that context; account changes invalidate it. Preserve bounded pending reports and revalidation before outgoing final reports. History/resume synchronization is best-effort, especially live/DVR; do not promise server acknowledgment.

## Checks and escalation

Use [shared validation](../../repo-router/references/validation.md):

| Change | Existing tests |
| --- | --- |
| URLs, timestamps, main-frame/origin validation | `PlaybackRequestTest`, `MainActivityPageScriptTest` |
| Suppression, Shorts, preference gating | `NativePlaybackScriptTest` |
| Cadence, seeks, pauses, outgoing ranges | `NativeWatchHistoryStateTest` |
| Metadata/account isolation, serialized reports | `NativeWatchHistoryScriptTest` |

On device, exercise incoming/SPA watch↔Shorts navigation, forged iframe messages, close/revisit, mpv toggling, and miniplayer browsing. Check signed-in history across pause, account switch, reload, and network failure; script-string tests cannot verify YouTube endpoints.

Use [android-shell](../../android-shell/SKILL.md) for broader navigation changes, [player](player.md) for progress continuity, and [shared contracts](../../repo-router/references/shared-contracts.md) for trust-boundary changes. [README Features](/home/runner/work/ssYoutube/ssYoutube/README.md#features): **Shorts in the WebView**, **Browsing WebView**, **Native watch history**.
