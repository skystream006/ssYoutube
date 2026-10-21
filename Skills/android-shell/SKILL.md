---
name: android-shell
description: >-
  Handles ssYouTube Android browsing UI, WebView navigation and Google sign-in
  behavior, deep links, preferences, themes, page customization, accessibility,
  and activity integration. Use for interface or browsing changes, not native
  decoding or external music-server authentication internals.
---

# Android shell

- Start with the named `MainActivity` method, preference key, or resource ID.
  Do not read the whole activity.
- Choose [browsing](references/browsing.md) for navigation/page scripts or
  [preferences](references/preferences.md) for controls, resources, and lifecycle.
- Preserve browser-session behavior, comment protection, and cleanup. Follow the
  referenced value/resource mappings rather than inventing parallel settings.
- Consult [shared contracts](../repo-router/references/shared-contracts.md) when a
  feature contract changes; load only that adjacent domain's relevant reference.
- Normally ignore extractor/mpv internals, music credential storage, updater
  networking, ad-filter implementation, release workflows, and unrelated tests.
- Use the selected reference's test map and
  [validation commands](../repo-router/references/validation.md). Validate affected
  Android integration and device interactions, not every feature by default.
