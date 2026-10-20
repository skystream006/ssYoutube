# Logging and resource statistics

## Locate the change

| Source | Search anchors |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/Logger.java` | `get`, `setEnabled`, `log`, `read`, `share`, `clear`, `submit`, uncaught-exception handler |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/LogFormat.java` | `safeUrl`, `sanitize`, `entry` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/LogStore.java` | `MAX_BYTES`, `append`, `snapshot`, `read`, `clear` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/StatsMonitor.java` | `setEnabled`, `onStart`, `onStop`, `onDestroy`, `isActive`, `storageBytes` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/StatsValues.java` | `RateTracker.sample`, `bytesPerSecond`, `storageBytes`, sentinel constants |

Integration lives in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java`:
`showLogs`, `shareLog`, `logActivityUrl`, `setStatsForNerdsEnabled`, lifecycle
callbacks, and relevant `showPreferences` controls. `NativePlayerView.showError`
is a diagnostic caller, not a reason to load all playback internals.

UI anchors: `stats_overlay` in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/activity_main.xml`;
`logging_toggle`, `view_logs_button`, `share_log_button`, `clear_log_button`, and
`stats_for_nerds_toggle` in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`.
The export boundary is `shared_logs` in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml`.

## Logging invariants

- Logging starts disabled. Both logcat and private files receive `LogFormat`
  output. URLs retain only sanitized web origins; exception rendering retains
  types/frames, not messages. Sanitization is not a general secret detector:
  never supply credentials, tokens, or sensitive free text to the logger.
- Files use no-backup storage, bounded rotation at 512 KiB per file and one backup.
  Ordinary writes use a bounded executor queue; overload drops entries rather
  than interrupting playback. Crash writes preserve the previous platform handler.
- Disabling logging prevents new submissions but retains files and permits queued
  writes to finish. Clear removes stored/exported logs; it cannot recall copies
  another app already received.
- Sharing snapshots previous then current logs into a fresh temporary export.
  Keep read-only, narrow FileProvider grants; never expand to credentials or the
  updater cache. Preserve serialization between writes, rotation, reads, and clear.

## Statistics invariants

Sample only while enabled and started; generation checks reject stale UI updates.
Memory/network refresh roughly every second; storage traversal is off the main
thread roughly every 30 seconds. `MEASURING` and `UNAVAILABLE` are different states.
Avoid counting overlapping storage roots twice or following leaf symbolic links;
failed/interrupted traversal invalidates the result. Metrics describe app-process
memory/app-UID traffic, not guaranteed totals for separate WebView renderers.
The overlay remains touch-through and stops work on lifecycle teardown.

## Consumers and targeted validation

Consult [shell](../../android-shell/SKILL.md) only for controls/lifecycle/export
wiring and the owning domain for new instrumentation. Do not load authentication,
filtering, media transport, or release code for a formatter fix.

Tests under
`/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`:
`LogFormatTest` for redaction/formatting; `LogStoreTest` for rotation/read/export/
clear; `StatsValuesTest` for rates/safe storage traversal. Add relevant
`PreferencesLayoutTest` cases for control changes. Use
[common commands](../../repo-router/references/validation.md).

Device-check opt-in behavior, log sharing/clearing, dismissal during asynchronous
reads, overlay visibility/touch-through, unavailable metrics, and pause/restart
as affected. Use synthetic diagnostics, not real account or playback secrets.
Documentation: **Stats for nerds** and **Debug logging** in
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#features).
