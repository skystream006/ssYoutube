# Shared ownership and cross-domain changes

Consult this only when changing a shared surface. The domain references remain
authoritative for behavior; this file records ownership, not copies of their
implementation rules.

Java symbols refer to
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Use the actual absolute checkout root if it differs.

## Shared surfaces

| Surface | Primary ownership and consultation |
| --- | --- |
| `MainActivity` | Shell owns structure, navigation, and lifecycle wiring. Playback owns routing/player/history contracts; music, delivery, and diagnostics own their feature behavior. Read/edit named methods and relevant listeners, not the entire class. |
| `Preferences` | Shell owns stored value representation and UI mapping. Playback owns language consumption; adblocking owns effective filtering behavior. |
| `PlaybackRequest`, `NativePlaybackScript.ORIGIN_RULES` | Playback owns video/page/origin definitions. Shell and adblocking are consumers; changing acceptance rules requires their targeted tests. |
| `MusicServerProtocol.preservePlaylist` | Music owns playlist URL rules; shell uses them while normalizing incoming and clicked links. |
| Manifest | `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`: shell owns application/main activity, music owns callback validation/registration, delivery owns installer requirements. |
| Layouts and strings | `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res`: shell owns layout/accessibility; each feature owns the semantics of its controls and status text. Change only affected IDs/entries. |
| FileProvider paths | `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/xml/file_paths.xml`: delivery owns `app_updates`; diagnostics owns `shared_logs`. Preserve narrow grants. |
| Build and dependencies | Delivery owns configuration; consult the affected runtime domain for compatibility or contract changes. |
| README | `/home/runner/work/ssYoutube/ssYoutube/README.md`: update only the relevant feature/build section. Source and checked-in configuration resolve stale documentation. |

## Conditional dependency map

- Shell → playback, adblocking, music, delivery, diagnostics for feature wiring.
- Playback → shell for language preferences/presentation; diagnostics for logging;
  delivery for native library/packaging changes.
- Adblocking → shell for effective enablement; playback for origin/page predicates.
- Music → shell for callbacks, current-media selection, and preference UI.
- Delivery → shell for update controls; playback for native dependency upgrades.
- Diagnostics → shell for lifecycle/overlay/export wiring; the domain being instrumented.

These are consultation edges, not eager imports. A stable call to a helper or a
resource shared by two features is not a reason to load both entire domains.

## Cooperation procedure

1. Name the requested behavior and primary owner; locate its seed symbol/test.
2. Identify exactly which input, output, lifecycle, resource, or trust contract
   crosses into another domain.
3. Load that domain's Skill and the relevant reference section only. Record the
   contract, affected symbols, and expected tests rather than forwarding all context.
4. Change provider and consumers together, keeping unrelated shared-file sections
   untouched. Do not refactor the activity or split modules as incidental work.
5. Run the deduplicated union of affected tests, then relevant device checks.
   Escalate for changed shared predicates or evidence of broader impact.
6. If a source/entry point moves, update its owning reference and the router mapping
   in the same change; do not duplicate the knowledge into other Skills.

Examples:
- New audio-language option: shell value/label mapping → playback language
  normalization/extraction/mpv preference → preferences, layout, extractor, and
  playback-request tests. No music authentication or release workflow context.
- Updater error presentation: delivery failure contract → diagnostics redaction
  if needed → shell UI → client/format/layout tests.

## Cross-cutting guardrails

Keep browser sessions, music credentials, anonymous extraction, and update
transport separate. Do not generalize their network policies into a single client
without an explicit architectural task. Preserve API-level guards and WebView
feature checks; Android lifecycle/UI work and background IO remain distinct.
Use the existing pure-Java seams and fake network clients for targeted regression
coverage. Detailed security and lifecycle rules live with each owning domain.
