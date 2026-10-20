# Architecture and routing index

Read the matching row first. This is the single repository map, not a prerequisite
for loading a clearly matched domain Skill.

## Source roots

The current repository root is `/home/runner/work/ssYoutube/ssYoutube`. When using
another checkout, resolve these paths against that checkout's absolute root.

- Java: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
- Resources: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res`.
- JVM tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
- Manifest: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`.
- Build/dependencies: `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle` and
  `/home/runner/work/ssYoutube/ssYoutube/settings.gradle`.
- User-facing behavior/build documentation: `/home/runner/work/ssYoutube/ssYoutube/README.md`.

Class names below identify Java files in the Java root. Use symbols as search
anchors; line numbers drift. These are behavioral boundaries within one package,
not independent Gradle modules.

## Domain map

| Skill | Responsibilities and first source anchors | Resources/configuration |
| --- | --- | --- |
| [android-shell](../../android-shell/SKILL.md) | `MainActivity`, `Preferences`, `SiteScope`, `NavigationHistory`: WebView browsing, Google sign-in, deep links, settings, page customization, accessibility | Main activity/application manifest sections; layouts, themes, navigation icons, animations, branding |
| [playback](../../playback/SKILL.md) | `PlaybackRequest`, `NativePlayerView`, `NativePlaybackState`, `NativePlayerGestures`, `NativeStreamExtractor`, `ExtractorDownloader`, `NativeNetworkPolicy`, `MpvPlayer`, `MpvPlaybackRequest`, `NativePlaybackScript`, `NativeWatchHistoryState`, `NativeWatchHistoryScript` | Player strings, miniplayer dimensions/drawables; playback-related activity methods |
| [adblocking](../../adblocking/SKILL.md) | `WebAdBlocker`, `AdBlockEngine`, `AdBlockingScript`: optional web request, cosmetic, and video-ad filtering | Bundled rules and video-ad JavaScript; filter toggle wiring |
| [music-server](../../music-server/SKILL.md) | `MusicServer`, `MusicServerProtocol`, `MusicServerClient`, `MusicServerStore`, `MusicServerCallbackActivity`: external login, encrypted session, remote jobs | Callback manifest entry; music preference row, strings, background |
| [delivery](../../delivery/SKILL.md) | `AppUpdater`, `GitHubUpdateClient`, `UpdateChecks`: update client plus Gradle, dependencies, APK versions/packaging, release automation | Build/wrapper files, release workflows, installer permission, update FileProvider path and preference controls |
| [diagnostics](../../diagnostics/SKILL.md) | `Logger`, `LogFormat`, `LogStore`, `StatsMonitor`, `StatsValues`: logs, exports, resource statistics | Diagnostic controls, overlay, log FileProvider path |

## Application flow

```text
Launcher / YouTube link → MainActivity → WebView → YouTube / Google sign-in
                              ├─ optional WebAdBlocker → bundled filters/scripts
                              ├─ validated route → NativePlayerView
                              │                      ├─ NativeStreamExtractor
                              │                      │    → NewPipeExtractor
                              │                      │    → ExtractorDownloader
                              │                      └─ MpvPlayer → libmpv
                              ├─ watch ranges → matching WebView page → YouTube history
                              ├─ MusicServer → external browser / remote music API
                              │      └─ MusicServerStore → Android Keystore
                              ├─ AppUpdater → GitHub Releases → Android installer
                              └─ Logger / StatsMonitor
Browser callback → MusicServerCallbackActivity → MainActivity → MusicServer
```

Media3 supplies controls and a player adapter, not an ExoPlayer engine. Browser
authentication, anonymous extraction, music credentials, and updater networking
are distinct trust boundaries.

There is one Android `:app` module, no local API server, database/migrations,
WorkManager service, or separately deployed worker. Preferences, browser storage,
music credentials, logs, and update cache stay with their feature owners.
Executors are feature-local. Tests belong to their behavior owners, not a separate
testing Skill. Detailed constraints live only in the relevant domain reference.

## Activation and progressive loading

Register/discover `/home/runner/work/ssYoutube/ssYoutube/Skills/*/SKILL.md` through
the intended agent host's supported mechanism. Do not assume this custom root is
automatically discovered or claim installation from file creation alone.

Expose only `name` and `description` during selection. For a clear request, load
the matching domain directly. For an ambiguous request, load the router, select
the domain, then load one reference section and its source/tests. Avoid duplicating
these files into other discovery directories or injecting this map into every
prompt. Confirm registration with the host before relying on automatic invocation.

## Routing acceptance examples

| Request | Initial context | Conditional expansion | Starting validation |
| --- | --- | --- | --- |
| Hide the YouTube header only on watch pages | Shell: `headerVisibilityScript`, page predicates | Playback only if a shared page predicate's contract changes | Relevant `MainActivityPageScriptTest` / `PreferencesTest` cases |
| Fix missing audio for separate mpv audio/video streams | Playback: `MpvPlaybackRequest.load`, `MpvPlayer.handlePrepare` | Extraction reference for wrong stream selection; delivery for library compatibility | `MpvPlaybackRequestTest`; adaptive playback on device |
| Adblocking breaks captions after SPA navigation | Adblocking: response filtering and reinjection | Shell only if page/toggle wiring is responsible | Node fixtures and relevant adblocking JVM tests; captions/SPA on device |
| Change the music-server address and sign in again | Music authentication: origin, pending login, session | Shell for address input/control flow | Music protocol/client tests; device cancellation/restart checks |
| Show privacy-safe updater failure details in Preferences | Delivery: updater failure/event model | Diagnostics for sanitization, shell for presentation | Updater tests, `LogFormatTest`, relevant layout cases |

For the final example: updater contract → diagnostic formatting → UI → union of
impacted tests. No native extraction, adblocking, or music credentials are needed.

## Outside normal context

Ignore generated build outputs, caches, IDE state, local SDK configuration,
keystore contents, credentials, browsing profiles, private logs, downloaded APKs,
unrelated agent configuration, and binary images unless explicitly relevant.
Third-party library implementations and the external music-server repository
are not owned here. Release publication/deletion is an explicit operational task,
not a validation step.
