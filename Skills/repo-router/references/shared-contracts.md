# Shared contracts and cross-domain work

Use only when a change crosses an ownership boundary. Calling an unchanged helper
does not require loading its owner's entire Skill.

## Shared surfaces

Production classes below are symbols in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Search the named method before reading a range of a large file.

| Surface | Authority and integration anchors |
| --- | --- |
| `MainActivity` | Shell owns activity structure and browser lifecycle. Playback owns contracts used by `routePage`, `installPlaybackRouting`, `updateWatchHistory`, `updatePlayerLayout`. Consult only the affected feature section. |
| `Preferences` | Shell owns representation, language codes, site mode, and defaults. Playback consumes language normalization; adblocking consumes `isAdBlockingEnabled`. |
| `PlaybackRequest`, `NativePlaybackScript.ORIGIN_RULES` | Playback owns supported page/link predicates and origin rules. Shell and adblocking consume them; changing their scope needs consumer tests. |
| `MusicServerProtocol.preservePlaylist` | Music owns URL selection/preservation semantics; shell calls it from `startUrl` and `YouTubeWebViewClient.handleUrl`. |
| `LogFormat` / `Logger` | Diagnostics owns formatting/export contracts; callers must not add credentials or sensitive data to messages. |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml` | Shell owns application/main activity; music owns callback registration; delivery owns installer permission. Provider changes must preserve both consumers. |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml` | Delivery owns `app_updates`; diagnostics owns `shared_logs`. Never broaden to all private files. |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml` and `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml` | Shell owns layout/accessibility; feature owners own their control semantics. Identify resource IDs rather than loading unrelated panels. |
| `/home/runner/work/ssYoutube/ssYoutube/README.md` | Update the affected feature/build section only. Source and checked-in workflows take precedence over stale prose. |

## Consultation edges

| Primary owner | Consult when the contract changes |
| --- | --- |
| [Shell](../../android-shell/SKILL.md) | Playback routing/placement; adblocking toggles; music callbacks/jobs; update UI; diagnostic controls |
| [Playback](../../playback/SKILL.md) | Shell language/preferences/presentation; diagnostics logging; delivery native dependencies |
| [Adblocking](../../adblocking/SKILL.md) | Shell effective enablement/reload; playback shared page/origin predicates |
| [Music](../../music-server/SKILL.md) | Shell callback routing, current-media presentation, preference panel |
| [Delivery](../../delivery/SKILL.md) | Shell update controls; playback native dependency changes; diagnostics logging |
| [Diagnostics](../../diagnostics/SKILL.md) | Shell lifecycle/overlay/export wiring; the instrumented caller's behavior |

## Trust boundaries

- Browsing may remain signed in. Native extraction and music-server networking
  do not receive browser cookies. Playback and music references define their
  different transport policies; do not create a shared permissive HTTP policy.
- Native history reporting runs inside the matching YouTube document. Its
  credentials/tracking URLs must not move into extraction, native playback,
  persisted state, or diagnostic messages.
- WebView filtering is separate from native media/extraction and account pages.
  Changing the mpv setting also changes effective adblocking; keep its saved
  choice distinct from whether filtering is currently active.

## Cross-domain procedure

1. Identify the behavior owner, changed contract, and direct consumers.
2. Read that owner's relevant reference and seed symbols.
3. Load an adjacent Skill only when implementation or validation crosses its
   contract; ownership of a shared file is not permission to refactor it broadly.
4. Change producer and consumers together, preserving unrelated behavior.
5. Run the deduplicated union of affected tests using [validation](validation.md);
   include device checks for lifecycle, networking, or platform behavior.
6. Update the owning reference if source paths, anchors, or contracts moved.

Example: updater failure detail → delivery's failure model → diagnostics'
sanitization → shell's preference row → relevant client, formatting, and layout
tests. Each owner remains authoritative; unrelated domains stay unloaded.
