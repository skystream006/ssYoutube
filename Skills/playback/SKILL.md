---
name: playback
description: >-
  Handles ssYouTube NewPipe extraction, stream and audio-language selection,
  embedded mpv playback, controls, gestures, live/retry lifecycle, native
  routing, fullscreen/miniplayer behavior, and YouTube watch-history sync.
  Use for playback changes, not optional web ad filtering.
---

# Native playback

- Start with the named playback class, not all of `MainActivity`.
- Choose [extraction](references/extraction.md),
  [player](references/player.md), or [web bridge](references/web-bridge.md);
  read only the subsection needed for the request.
- Preserve anonymous extraction, request/handle identity, and page-scoped history.
  Detailed constraints and test maps live in the chosen reference.
- Consult [shell](../android-shell/SKILL.md) for preference/presentation contracts,
  [delivery](../delivery/SKILL.md) for native dependencies, or
  [diagnostics](../diagnostics/SKILL.md) for logging changes.
- Normally ignore music integration, ad-filter implementation, updater internals,
  release workflows, and unrelated preference sections.
- Run the mapped tests using
  [validation commands](../repo-router/references/validation.md), then the relevant
  native-player device scenarios. JVM checks do not prove native rendering.
