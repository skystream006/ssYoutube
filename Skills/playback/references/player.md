# Native player, controls, and lifecycle

## Source anchors

- [NativePlayerView.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativePlayerView.java): `play`, `startExtraction`, `isCurrent`, `cancelExtraction`, `retry`, `capturePosition`, `performGesture`, `SurfacePlayerView`.
- [MpvPlayer.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MpvPlayer.java): `setStream`, `handlePrepare`, `getState`, `poll`, `Observer.event`, `surfaceChanged`, `closeNativePlayer`.
- [MpvPlaybackRequest.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MpvPlaybackRequest.java): constructor, `load`, `seconds`, `milliseconds`.
- [NativePlaybackState.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativePlaybackState.java): `select`, `shouldExtract`, `shouldPlay`, `useLiveDefaultPosition`, `capturePosition`, `restore`.
- [NativePlayerGestures.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativePlayerGestures.java): `down`/`up`/`confirmTap`, `cancel`, `swipe`, `tapZone`, `seekPosition`.

## Invariants

libmpv owns playback; `MpvPlayer` adapts Media3 `SimpleBasePlayer` state for `PlayerView` controls, not an ExoPlayer engine. Each selected video owns a fresh mpv handle; replacement, retry, or closing destroys the old handle. Fullscreen/miniplayer transitions retain it and resize the surface. `Observer.event` posts to the main thread and checks source identity before touching state; never call JNI or wait for the UI thread inside native callbacks.

`startExtraction` captures language per request. Its generation/video/lifecycle checks reject late results; cancellation disconnects transport, cancels the future, and purges queued work. Preserve the single-worker, bounded queue. See [extraction](extraction.md) only when selection or transport changes.

Initialize before `MpvPlaybackRequest.load`. Append explicit audio with `change-list audio-files append`, then `loadfile … replace`, passing each URL as one argument. CLI suffixes such as `audio-files-append` are not libmpv options. Audio-configuration failure must stop before loading silent video.

Keep cookies, user config/scripts, external extraction, automatic sidecar discovery, and URL-bearing mpv logs disabled. Keep TLS verification and the bundled CA preparation in `prepareCertificates`; protocol restrictions are not Java per-redirect host validation.

Fresh untimestamped live playback passes `C.TIME_UNSET`, omitting mpv's `start`. Do not capture placeholder zero as a known position before a resolved timeline. Explicit timestamps and restored/captured positions, including known zero, survive retry. `getState` exposes a placeholder until native position is known and only enables seeking when seekable.

`onPause` captures resolved position, cancels extraction, and pauses without erasing user intent. `onResume` resumes prepared state or eligible extraction; failed playback requires explicit retry. Duplicate page selections must not restart or unpause. `saveState`/`restoreState` retain identifiers, positions, and intent flags, never expiring stream URLs; `release` shuts down work. Preserve audio-focus/noisy-output handling.

Failures do not automatically fall back to web playback; switching requires disabling mpv in Preferences.

Single taps toggle controls; double taps seek ten seconds in outer thirds or toggle play centrally. Miniplayer taps expand. Vertical swipes enter/exit fullscreen, minimize, expand, or dismiss according to mode. Controls/scrubber retain touch priority; cancellation, multiple pointers, resize, and mode changes clear gesture tracking. Seek only on a resolved, seekable video.

## Checks and consumers

Use [shared validation](../../repo-router/references/validation.md):

| Change | Existing tests |
| --- | --- |
| Commands, audio ordering, privacy, start/language options | `MpvPlaybackRequestTest` |
| Duplicate selection, pause, live retry/restoration | `NativePlaybackStateTest` |
| Tap/swipe decisions, cancellation, seek bounds | `NativePlayerGesturesTest` |

On device, replace videos during buffering; retry failed live startup; pause/resume; unplug headphones; seek/scrub; exercise TalkBack, fullscreen/miniplayer, and fold/unfold. Pure tests do not execute JNI or Android touch dispatch.

Consumer: [MainActivity.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java), `updatePlayerLayout`/`setPlayerFullscreen`; escalate layout changes to [android-shell](../../android-shell/SKILL.md). Resource anchors: [player_strings.xml](/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/player_strings.xml) `native_player_retry`/`native_player_seek_unavailable`; [dimens.xml](/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/dimens.xml) `miniplayer_width`/`miniplayer_margin`.

[README Features](/home/runner/work/ssYoutube/ssYoutube/README.md#features): **Native video playback**, **Player presentation**, **Foldables**.
