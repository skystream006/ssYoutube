---
name: playback
description: Use for NewPipe extraction and audio-language selection, anonymous native transport, mpv playback and Media3 controls, gestures, live positioning, retry/lifecycle races, trusted YouTube routing, Shorts transitions, or native watch-history synchronization.
---

# Playback

Open only the reference matching the failing boundary, then inspect its named methods before changing behavior:

- [Extraction](references/extraction.md): NewPipe localization, stream/audio selection, cancellation, HTTPS policy, and anonymous request headers.
- [Player](references/player.md): libmpv commands and handles, Media3 controls, gestures, live timelines, retries, and lifecycle state.
- [Web bridge](references/web-bridge.md): validated page routing, web-media suppression, Shorts, and page-scoped watch history.

Keep browsing credentials separate from anonymous extraction and media playback. Media3 supplies controls, not an ExoPlayer engine. Preserve one native handle per selected video across presentation changes; reject stale asynchronous work.

All test classes named in these references live under `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube/`.

Escalate only when the change crosses boundaries: [android-shell](../android-shell/SKILL.md) for activity layout, preferences, and incoming links; [delivery](../delivery/SKILL.md) for dependency or packaged-native-library changes; [diagnostics](../diagnostics/SKILL.md) for safe failure evidence. Consult [validation](../repo-router/references/validation.md) when selecting checks and [shared contracts](../repo-router/references/shared-contracts.md) when changing cross-feature ownership or privacy.

Do not load music-server, adfilter internals, or updater context for ordinary playback work.
