---
name: adblocking
description: "Use when changing or debugging optional WebView adblocking, bundled network/cosmetic rules, YouTube video-ad JavaScript, service-worker interception, or mpv-dependent adblocking controls."
---

# Optional WebView adblocking

Start with [filtering](references/filtering.md) for source anchors, rule syntax, script lifecycle, and focused tests. Distinguish network matching, cosmetic styles, and video-ad scheduling hooks before editing; they have different consumers and failure modes.

Filtering is active only when mpv is off and the saved opt-in is on. Preserve supported HTTPS YouTube document scope and main-frame exclusions; this is not an HTTPS-only requirement for every intercepted subresource. Allowed requests return `null` to normal WebView networking, never a proxy or replay.

Keep rules/scripts bundled, API 21-compatible, and narrowly scoped. Preserve sign-in, content streams, history, captions, comments, and thumbnails. The fallback may click only a visible enabled skip control in an explicitly ad-playing player; disabling reloads the page to clear modifications.

Open [android-shell](../android-shell/SKILL.md) only for preference/lifecycle wiring, and [playback](../playback/SKILL.md) only for shared page/origin predicates. Consult [shared contracts](../repo-router/references/shared-contracts.md) or [validation](../repo-router/references/validation.md) on demand. Do not load native extraction, updater, or music-server code by default.
