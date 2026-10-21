# Embedded mpv, state, and controls

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Use the actual absolute checkout root if different.

## Entry points

| Concern | Source anchors |
| --- | --- |
| Controls, loading/retry, lifecycle | `NativePlayerView.play`, `startExtraction`, `retry`, `onPause`, `onResume`, `stop`, `release` |
| User intent, restoration, live default | `NativePlaybackState.select`, `restore`, `capturePosition`, `useLiveDefaultPosition` |
| Tap/swipe/seek decisions | `NativePlayerGestures`; gesture handling in `NativePlayerView` |
| mpv configuration and separate audio | `MpvPlaybackRequest` constructor and `load` |
| Media3 adapter/native handle | `MpvPlayer.setStream`, `handlePrepare`, `closeNativePlayer`, `Observer` |
| Surface/audio focus/timeline | `MpvPlayer` surface callbacks, focus handling, `getState`, `poll` |
| Activity placement | `MainActivity.updatePlayerLayout`, `setPlayerFullscreen` |

Player resources:
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/player_strings.xml`,
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/dimens.xml`, and
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable/bg_miniplayer.xml`.
Controls are also constructed programmatically in `NativePlayerView`.

## Architecture and invariants

- Media3 supplies controls and `SimpleBasePlayer` integration; libmpv is the
  playback engine, not ExoPlayer.
- A selected video owns its mpv handle. Fullscreen/miniplayer only change
  presentation/surface size; replacing/closing a video destroys its old handle.
- Remove observers before destruction. Post native callbacks to the main looper
  and reject callbacks whose captured MPV instance is no longer current.
- Apply mpv options before initialization. Append external audio using
  `change-list audio-files append` after initialization and before `loadfile`.
  Keep URLs as separate command arguments; CLI list suffixes are not option names.
- Keep browser cookies, user config/scripts, external URL extractors, and
  URL-bearing native logs disabled. Preserve TLS verification and protocol limits.
- Initial selected URLs pass `NativeNetworkPolicy`. Native redirects and nested
  media requests are handled by libmpv/FFmpeg and do not inherit Java extraction
  host checks or response bounds.
- Unknown live position uses `C.TIME_UNSET`. Capture a position only from a
  resolved, non-placeholder timeline; retain this distinction across retry and
  activity restoration.
- Saved state contains identifiers, position, and intent, not expiring stream URLs.
  Pause/close/release must preserve user intent and cancel/release the right work.

## Tests and device checks

- Command/options/audio/language changes: `MpvPlaybackRequestTest`.
- Live/default/restoration/user intent: `NativePlaybackStateTest`.
- Gestures/seek bounds: `NativePlayerGesturesTest`.
- Watch-range continuity: add `NativeWatchHistoryStateTest` only if continuity changes.
- Placement/settings overlap: relevant `PreferencesLayoutTest` and device checks.

Use [validation commands](../../repo-router/references/validation.md).
There is no direct native mpv instrumentation suite. Check affected audio/video
formats, seek/replay, live startup/retry, audio-focus/headphone interruptions,
rapid replacement, pause/resume, rotation/recreation, and fullscreen/miniplayer
transitions on a device. Native/ABI changes require broader device coverage.

Consult shell for layout/preference contracts, delivery for library/CA-cache/ABI
changes, and diagnostics for error reporting. Leave extraction selection and
web-history implementation unloaded unless the observed failure crosses there.

## Documentation

[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features):
**Native video playback** and **Player presentation** describe player behavior;
[Build and test](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test)
lists device/native compatibility scenarios.
