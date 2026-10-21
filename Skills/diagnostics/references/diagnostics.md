# Logs and resource statistics

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Use the actual absolute checkout root if different.

## Find the smallest slice

| Request | Source anchors | Starting tests |
| --- | --- | --- |
| URL/message/exception redaction | `LogFormat.safeUrl`, `sanitize`, `entry` | `LogFormatTest` |
| Rotation/read/export snapshot | `LogStore.append`, `read`, `snapshot`, `clear` | `LogStoreTest` |
| Opt-in/asynchronous logging and sharing | `Logger.setEnabled`, `log`, `read`, `share`, `clear`; crash handler | Format/store tests plus device checks |
| Network rates/storage traversal | `StatsValues` rate sampling and `storageBytes` | `StatsValuesTest` |
| Sampling/lifecycle/overlay | `StatsMonitor.setEnabled`, `onStart`, `onStop`, `onDestroy`, `updateSampling`, `publish` | Values tests plus device checks |
| Settings and export integration | `MainActivity.showLogs`, `shareLog`, `setStatsForNerdsEnabled`, logging helpers and preference listeners | Relevant `PreferencesLayoutTest` cases |

Resources:
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/activity_main.xml`:
  touch-through stats overlay.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`:
  logging/stats controls.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml` and
  `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/colors.xml`:
  labels, measurement states, overlay styling.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml`:
  narrow `shared_logs` cache export.

## Logging rules

- Logging is opt-in. Use `LogFormat` for both logcat/file output; retain web
  origins and exception types/frames, not URL credentials/paths/query/fragment or
  exception messages. Sanitization is not permission to log tokens or raw payloads.
- Keep background queues and file rotation bounded. Best-effort logging must not
  interrupt playback or the platform crash handler.
- Files live in no-backup storage. Sharing creates a snapshot in a narrow cache
  directory and grants temporary read access, not broad app-file access.
- Disabling logging stops new entries but keeps existing files and allows queued
  entries to finish. Clearing handles active/previous logs and exported snapshots.
  Previously shared copies cannot be revoked.

## Statistics rules

- Sample only while enabled and started; generation checks reject stale work.
  Destroy/stop cancel sampling and UI callbacks.
- Memory is app-process PSS; traffic is app-UID rates; storage spans app-owned
  roots without duplicate/symlink traversal. Separate WebView renderer usage may
  not be included.
- Keep IO off the UI thread. Preserve “Measuring” versus unsupported/unavailable
  states and rate/reset behavior; do not show invented zero measurements.
- The overlay remains noninteractive; it must not intercept player/web gestures.

## Validation and scope

Use [validation commands](../../repo-router/references/validation.md).
Run only the relevant formatting/store/values tests; add layout tests for resource
changes. Device-check enable/disable, viewing/sharing/clearing, background/foreground
transitions, overlay touch-through, and recreation when those paths change.
Use synthetic diagnostic data, not real credentials or browsing logs.

Consult shell for lifecycle/UI/export integration, and the calling domain for
new instrumentation. Do not explore extractor, music credential, or updater
implementations merely to change formatting or the stats overlay.

## Documentation

[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features):
**Stats for nerds** and **Debug logging** describe measurement limits, opt-in
logging, retention, redaction, and export behavior.
