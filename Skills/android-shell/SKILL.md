---
name: android-shell
description: >-
  Handles Android browsing UI, WebView navigation and sign-in behavior,
  deep links, preferences, themes, page customization, accessibility,
  and activity integration. Use for user-interface or browsing changes.
---

# Start at the visible behavior

- Inspect the affected method in
  `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java`,
  preference key, or resource ID; do not read the whole activity.
- Choose [browsing](references/browsing.md) for navigation/page scripts or
  [preferences](references/preferences.md) for controls/resources. These map
  architecture anchors, conventions, tests, and device checks.
- Preserve browser sessions, comment protection, and lifecycle cleanup.
- Consult [playback](../playback/SKILL.md) for routing/player contracts. For other
  feature controls, use [shared ownership](../repo-router/references/shared-contracts.md)
  to select only the affected neighbor.
- Normally ignore native extraction/decoding, music credential storage, updater
  networking, ad-filter internals, release workflows, and unrelated panels.
- Run the mapped JVM/resource tests with
  [validation](../repo-router/references/validation.md); add Android build/lint
  and affected device scenarios for UI/platform changes.
