# In-app updates

## Locate the change

| Source | Search anchors |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/AppUpdater.java` | `checkOnStartup`, `check`, `dispatch`, `install`, `isCompatible`, `onInstallPermissionResult`, `publishProgress`, `onCleared` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/GitHubUpdateClient.java` | `LATEST_URL`, `fetchLatest`, `parseRelease`, `download`, `openDownload`, `ConnectionFactory` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/UpdateChecks.java` | `releaseVersion`, `validateAsset`, `assetUri`, `redirectUri`, `copy`, `verifyDigest` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java` | Updater creation, `updatesResumed`, `showPreferences`, `updateObserver`, dialog dismissal |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES`, non-exported `FileProvider` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml` | `app_updates` cache path |

UI anchors are `check_updates_button`, `update_progress_panel`,
`update_download_status`, `update_download_progress`, and `update_download_bytes`
in `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`;
matching `updates_` strings are in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml`.

## Behavior and invariants

- Startup checks notify but do not download. A manual Preferences check can
  download a newer stable release from this repository's GitHub Releases.
- `AppUpdater` is an activity-retained AndroidViewModel. Progress is persistent
  LiveData, distinct from consumable installer/toast events; dialog dismissal
  removes its progress observer. `dispatch` only opens UI while resumed.
- Validate uploaded APK metadata, repository asset path, bounded size, allowed
  HTTPS redirects, response lengths, and any provided SHA-256 digest. The digest
  is optional upstream; do not describe it as always available.
- Downloads go to private update cache via a temporary partial file. Rename
  publishes a complete download; failures clean partials. Progress counts written
  bytes and uses release size even when response length is absent.
- Before installation, require the installed package name/signers, expected
  version name, and a strictly higher versionCode. Revalidate after returning
  from unknown-source permission settings, including process recreation.
- Android installation needs user confirmation. Grant read access only to the
  intended APK, not all cache/private files. Cancel outstanding work when cleared.

## Consumers and expansion

Consult [shell](../../android-shell/SKILL.md) for progress/presentation or observer
wiring; [diagnostics](../../diagnostics/SKILL.md) for new failure details.
Load [build/release](build-release.md) only for version, package, signing, release,
or packaging contracts. Do not load music authentication, page scripts, ad
filtering, or playback for a transport/progress fix.

## Targeted validation

Classes under
`/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`:

| Changed behavior | Tests |
| --- | --- |
| Version, URL, asset, size, digest, copy/cancellation rules | `UpdateChecksTest` |
| Download/progress, redirects, partial cleanup, response bounds | `GitHubUpdateClientTest` |
| Preference update row/resources | Affected `PreferencesLayoutTest` methods |

Use [common commands](../../repo-router/references/validation.md). JVM client
fixtures do not exercise all Android JSON parsing, PackageManager/signature
checks, LiveData lifecycle, or installer flows. Device-check startup versus manual
checks, network failure/retry, progress after reopening/recreation, permission
denial/return, incompatible APK rejection, and installer confirmation as relevant.
Do not publish a release merely to validate client logic.

Documentation: **In-app updates** in
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#features).
