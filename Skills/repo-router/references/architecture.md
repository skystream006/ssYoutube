# Architecture and routing

Read only for orientation or ambiguous ownership. For a clear feature request,
go directly to its Skill and one relevant reference.

## Discovery and path convention

The canonical Skill collection is
`/home/runner/work/ssYoutube/ssYoutube/Skills`. Each immediate child has one
`SKILL.md` with `name` and `description` metadata; detailed knowledge is under its
`references` directory. Source paths in these references use this checkout's
absolute root, `/home/runner/work/ssYoutube/ssYoutube`. In another checkout,
substitute its absolute root; Skill-to-reference links remain relative.

Root `Skills` is **not automatically discovered by GitHub Copilot** under its
[documented project locations](https://docs.github.com/en/copilot/concepts/customizations/agent-skills).
Those locations include `.github/skills`, `.claude/skills`, and `.agents/skills`,
with support depending on the host. This collection does not install a loader
or claim automatic activation. Before automated use, configure a host that
supports this custom location or explicitly register the collection using that
host's supported mechanism. If unavailable, explicitly direct the agent to the
chosen `SKILL.md` (or router for ambiguity).

The loader should index only names/descriptions initially, then load the selected
body, then a relevant reference section. Confirm discovery by checking that the
host lists the seven Skills and routes the examples below without loading every
reference. Do not duplicate full reference trees into multiple locations.

## Application map

One Android Gradle module, `:app`, is declared in
`/home/runner/work/ssYoutube/ssYoutube/settings.gradle`. Production Java lives in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`;
the package is flat, so feature boundaries are **symbols**, not Java subfolders.
Resources live in `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res`.

```text
Android launcher / YouTube link → MainActivity
  ├─ WebView → YouTube browsing / Google sign-in
  │    ├─ page customization scripts
  │    └─ optional WebAdBlocker
  ├─ trusted playback routing → NativePlayerView
  │    ├─ NativeStreamExtractor → NewPipeExtractor → ExtractorDownloader
  │    └─ MpvPlayer → libmpv (Media3 provides controls/adapter, not decoding)
  ├─ native watch-history samples → matching YouTube document → web requests
  ├─ MusicServer → external browser / remote music API → MusicServerStore
  ├─ AppUpdater → GitHub Releases → Android installer
  └─ Logger / StatsMonitor
Browser authorization return → MusicServerCallbackActivity → MainActivity
```

| Domain | Entry symbols and resources | Selective knowledge |
| --- | --- | --- |
| [android-shell](../../android-shell/SKILL.md) | `MainActivity`, `Preferences`, `NavigationHistory`, `SiteScope`; activity/preferences layouts | Browsing or preferences |
| [playback](../../playback/SKILL.md) | `NativePlayerView`, `NativeStreamExtractor`, `MpvPlayer`, `PlaybackRequest`; native playback/history scripts | Extraction, player, or web bridge |
| [adblocking](../../adblocking/SKILL.md) | `WebAdBlocker`, `AdBlockEngine`, `AdBlockingScript`; bundled rules/video-ad script | Filtering |
| [music-server](../../music-server/SKILL.md) | `MusicServer`, `MusicServerProtocol`, `MusicServerClient`, `MusicServerStore`, callback activity | Authentication or jobs |
| [delivery](../../delivery/SKILL.md) | `AppUpdater`, `GitHubUpdateClient`, `UpdateChecks`; Gradle and release workflows | Build/release or updates |
| [diagnostics](../../diagnostics/SKILL.md) | `Logger`, `LogFormat`, `LogStore`, `StatsMonitor`, `StatsValues` | Diagnostics |

Persistence stays with its feature: SharedPreferences/browser storage in shell,
encrypted credentials in music, files in diagnostics, and APK cache in delivery.
Executors handle asynchronous work; there is no separate local backend, database
schema/migration system, or independently deployed worker. Do not invent Skills
for those layers. The music server is a separate repository and API contract.

Runtime configuration comes from preferences, a user-entered HTTPS server origin,
and Android/WebView capabilities. Build requirements and commands live only in
[validation](validation.md) and [build/release](../../delivery/references/build-release.md).
See [shared contracts](shared-contracts.md) for dependency/ownership decisions.
The feature narrative is in
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#features).

## Routing acceptance examples

| Request | First load and seed | Expand only if | Smallest initial validation |
| --- | --- | --- | --- |
| Hide header only on watch pages | Shell → browsing; `headerVisibilityScript`, `applyHeaderVisibility` | Preference semantics change → preferences reference | Affected `MainActivityPageScriptTest` cases; `PreferencesTest` if needed |
| Missing audio with separate video/audio | Playback → player; `MpvPlaybackRequest.load` | Wrong selected stream → extraction; library incompatibility → delivery | `MpvPlaybackRequestTest`; selection tests if changed; adaptive-playback device check |
| Adblocking breaks captions after SPA navigation | Adblocking → filtering; bundled script | Reinjection/toggle wiring is responsible → shell | Node fixtures, affected ad-script tests, caption/SPA device check |
| Change music-server address and sign in again | Music → authentication; `login`, `forget`, `normalizeOrigin` | Address/control flow changes → shell | Protocol/client tests; affected layout tests; restart/cancellation checks |
| Privacy-safe updater failure details in Preferences | Delivery → updates; `AppUpdater.check` | Redaction → diagnostics; presentation → shell | Update tests, `LogFormatTest`, affected layout tests, failure/recreation device check |

The final example flows from updater failure model to diagnostic sanitization to
preference presentation. It does not require playback, music, or adblocking context.

## Outside normal context

Exclude generated outputs/caches, IDE state, local SDK configuration, keystore
contents, credentials, browser profiles, downloaded APKs, private logs, unrelated
agent configuration, and third-party implementation sources. Read binary images
only for appearance work. Release publication/deletion needs explicit task
authorization; a request to inspect delivery does not authorize remote operations.

Maintain this index when ownership changes. Keep exact source/test anchors in the
owning reference rather than copying the whole map into each Skill.
