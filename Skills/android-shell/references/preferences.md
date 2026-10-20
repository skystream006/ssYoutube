# Preferences, resources, and activity integration

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Resolve paths against the actual absolute checkout root if different.

## Entry points

- `MainActivity.showPreferences`: controls, listeners, LiveData observers,
  advanced-panel expansion, and dismissal cleanup. Search by resource ID before
  reading this large method.
- `Preferences`: stable theme/site-mode/language values, URL/user-agent decisions,
  and effective adblocking predicate; this class has no Android dependencies.
- `MainActivity.onCreate`, `onSaveInstanceState`, `onPause`, `onResume`,
  `onStart`, `onStop`, `onDestroy`: feature construction and lifecycle wiring.
- `openPreferencePanel`, `closePreferencePanel`, `applyTheme`,
  `openSupportedLinkSettings`: panel, appearance, and OS settings integration.
- `updatePlayerLayout`, `setPlayerFullscreen`: shell placement of the existing
  player, WebView, settings button, and statistics overlay.

## Resource map

| Absolute path | Responsibility |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/activity_main.xml` | Root container, browsing WebView, stats overlay, persistent settings button |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml` | Navigation, language/appearance, feature controls, update progress, music row, advanced controls |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml` | Labels, status messages, ordered spinner arrays |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/themes.xml` | App theme |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/colors.xml` | Theme/overlay colors |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/dimens.xml` | Player and shell dimensions |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/anim` | Preference-panel animation |

## Conventions

- `ssyoutube_prefs` stores choices via activity keys; `Preferences` defines pure
  value/URL rules, not a second persistence mechanism.
- Spinner array order must match value mappings, especially language codes.
- Settings stay reachable above the WebView and native fullscreen/miniplayer.
  Preserve accessible labels, touch targets, narrow layouts, and large-font behavior.
- Keep one WebView and one NativePlayerView; resizing does not reconstruct them.
  Destroying the activity releases extraction/player resources and script handlers.
- Remove preference-dialog observers on dismissal. Feature ViewModels own their
  state; UI listeners must not duplicate authentication or updater logic.
- Feature toggles are not interchangeable: native playback, adblocking,
  logging, and stats have different stop/reload/lifecycle contracts.

## Test selection

- Value/default/order/URL changes: `PreferencesTest`.
- Resource IDs, visibility, order, accessible labels, or feature rows:
  relevant `PreferencesLayoutTest` methods.
- Page-facing preference scripts: relevant `MainActivityPageScriptTest` methods.
- Add the owning domain's tests only when changing its behavior, not for spacing.

Use [validation commands](../../repo-router/references/validation.md).
Device-check light/dark/system themes, narrow widths, large fonts, persisted
choices, dialog reopen, and relevant recreation/fullscreen/miniplayer interactions.
XML tests do not establish runtime observer or lifecycle correctness.

Consult [shared ownership](../../repo-router/references/shared-contracts.md)
before changing playback language/routing, adblocking gating, music login, update
events, or diagnostics. Leave unrelated listeners and feature internals unloaded.

## Documentation

[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features):
**Preferences** and **Player presentation** describe control visibility,
persistence, language behavior, and fullscreen/miniplayer settings access.
