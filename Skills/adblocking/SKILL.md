---
name: adblocking
description: >-
  Handles optional ssYouTube WebView request blocking, cosmetic rules, YouTube
  video and Shorts ad filtering, service-worker interception, and filter
  enable/disable lifecycle. Use for ads or content regressions caused by
  filtering, not native stream extraction.
---

# Optional web adblocking

- Start with the affected rule, bundled script, or blocker method in
  [filtering](references/filtering.md); it maps each layer to its tests.
- Preserve mpv gating, browser-owned networking, content data, and guarded skipping.
- Consult [shell](../android-shell/SKILL.md) for toggle/page wiring and
  [playback](../playback/SKILL.md) only when shared page/origin predicates change.
- Normally ignore native extraction/decoding, music credentials, updater code,
  release workflows, and unrelated UI sections.
- Use [validation commands](../repo-router/references/validation.md) for mapped
  JVM tests and executable Node fixtures; do not treat a skipped Node wrapper as
  coverage. Device-check affected page types and enable/disable transitions.
