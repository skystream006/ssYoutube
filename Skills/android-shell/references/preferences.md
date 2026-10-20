# Preferences and Android presentation

## Locate the change

| Source | Search anchors |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java` | `PREFS_NAME`, relevant `KEY_` constant, `showPreferences`, `openPreferencePanel`, `closePreferencePanel`, `applyTheme`, `updatePlayerLayout` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/Preferences.java` | Theme constants, language mapping, user agents/home URLs, `isAdBlockingEnabled` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/activity_main.xml` | `root_container`, `webview`, `stats_overlay`, `settings_button` |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml` | `language_spinner`, `theme_spinner`, `site_mode_spinner`, `advanced_settings`, affected feature row |
| `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml` | Matching labels, content descriptions, and option arrays |

Styles/dimensions are under
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values`; panel animations
and backgrounds are under `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/anim`
and `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable`. Open only
resources referenced by the affected control.

## Conventions and state

- `MainActivity` reads/writes SharedPreferences; `Preferences` keeps reusable
  values and decisions free of Android dependencies. Preserve persisted keys
  and option ordering unless deliberately migrating their contract.
- Default playback language is English, not device locale. `language_options`
  and `Preferences` language codes must remain index-aligned. A language change
  affects future extraction/retry, not the already playing video's language.
- The settings button remains reachable above web content, fullscreen playback,
  and miniplayer. `updatePlayerLayout` owns stacking and placement; do not
  instantiate a replacement player for a presentation change.
- Keep dropdown labels, content descriptions, 48dp interactive targets, theme
  support, and narrow/large-font layouts. Advanced controls start collapsed;
  feature controls outside that panel must not accidentally move into it.
- `showPreferences` subscribes to update/music state and removes those observers
  on dismissal. Activity destruction dismisses the dialog before releasing views.
- Site-mode changes update the user agent/zoom, clear browser history, and load
  the corresponding page host. Header/related visibility is independently saved.

## Feature boundaries

Use [shared ownership](../../repo-router/references/shared-contracts.md) only at
these seams:

- `setMpvEnabled` stops native work, updates document-start scripts and effective
  filtering, then reloads to remove existing document hooks. Consult
  [playback](../../playback/SKILL.md), not just the preference label.
- `adblocking_settings` is hidden with mpv on, but the saved adblocking choice is
  retained. Consult [adblocking](../../adblocking/SKILL.md) for gating/reload changes.
- `music_server_panel`, `update_progress_panel`, logging controls, and
  `stats_overlay` delegate behavior to their owning Skills. A label/spacing
  change alone does not require those implementations.

Do not load all feature classes merely because their controls share this dialog.

## Targeted validation

Tests are under
`/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`:

- `PreferencesTest`: defaults, language indexing, site-mode decisions, mpv/adblock gating.
- `PreferencesLayoutTest`: matching row/control assertions; language-array alignment,
  visibility/order, accessibility properties, and diagnostic/feature controls.
- `MainActivityPageScriptTest`: only if header/related/page-script behavior changes.
- Add consumer tests from the relevant Skill when changing behavior, not just layout.

Use [common commands](../../repo-router/references/validation.md). Device-check
light/dark themes, large fonts/narrow screens, fullscreen/miniplayer settings
access, restart/recreation persistence, and dismissal/reopening for affected rows.
XML tests cannot validate rendered geometry or lifecycle observers.

Documentation: **Preferences** and **Player presentation** in
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#features).
