# In-app update discovery and installation

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Resolve against the actual absolute checkout root if different.

## Source map

| Concern | Source anchors |
| --- | --- |
| Lifecycle, checking, progress/events | `AppUpdater.checkOnStartup`, `check`, `downloadProgress`, `dispatch`, `publishProgress`, `complete`, `onCleared` |
| Installer permission/resume and APK identity | `AppUpdater.install`, `onInstallPermissionResult`, `isCompatible`, `sameSigners` |
| Latest release / download | `GitHubUpdateClient.fetchLatest`, `parseRelease`, `download`, `openDownload` |
| Version, asset, redirect, length/digest rules | `UpdateChecks.compareVersions`, `releaseVersion`, `validateAsset`, `assetUri`, `redirectUri`, `copy`, `verifyDigest` |
| UI integration | `MainActivity.showPreferences` update controls/observer; lifecycle dispatch and `onActivityResult` |

Relevant configuration/resources:
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`:
  installation permission and FileProvider.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml`:
  `app_updates` cache path.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`:
  `check_updates_button`, `update_progress_panel`, download status/progress/bytes.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml`:
  update status messages.

## Invariants

- Fetch the latest stable GitHub Release from this repository. Startup announces
  availability without downloading; manual checking can download and open installation.
- Restrict initial APK URLs to this repository's release assets and redirects to
  accepted HTTPS GitHub hosts. Preserve metadata/body bounds, expected length,
  optional digest verification, interruption checks, and complete-file publication.
- Cache downloads privately; never expose partial APKs as complete downloads.
- Candidate package, expected version name, greater versionCode, and signing
  certificates must match requirements. Revalidate after installer permission
  round trips, including process recreation.
- Keep progress in LiveData separate from one-shot toast/installer events.
  Preferences removes its observer on dismissal; reopening displays current progress.
- Background work must not launch UI while the activity is not resumed. Android
  installation always requires platform/user confirmation.
- FileProvider sharing grants only the needed read access to the update file.
  Do not broaden the provider to arbitrary app storage.

## Validation

- Version/asset/redirect/copy/digest rules: `UpdateChecksTest`.
- HTTP/download/progress behavior: `GitHubUpdateClientTest`.
- Preference progress/control changes: relevant `PreferencesLayoutTest` methods.

Use [validation commands](../../repo-router/references/validation.md).
There is no direct JVM test of PackageManager signatures or the installer UI.
Device-check newer/current/no-release responses, failure/retry, progress through
dialog reopen/activity recreation, unknown-source permission return, and rejected
incompatible APKs for affected changes.

Consult shell for UI wiring, diagnostics for safe error detail, and
[build/release](build-release.md) only when versioning/signing/publication contracts
change. Ignore music authentication, playback streams, and ad filters.
