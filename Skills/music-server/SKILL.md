---
name: music-server
description: "Use when changing or debugging Music Server browser/passkey login, S256 PKCE callbacks, encrypted session persistence, or Send playlist/Send media job selection and submission."
---

# Music-server integration

Work on the Android client for the external ssYTDLP_Server service, not server implementation, YouTube sign-in, or media extraction.

Choose the smallest reference:

- [Authentication](references/authentication.md): HTTPS origins, browser authorization, callback validation, lifecycle restoration, and encrypted credentials.
- [Jobs](references/jobs.md): playlist versus media selection, Shorts/miniplayer behavior, Preferences controls, and submission outcomes.

Preserve origin-bound S256 PKCE, external-browser authorization, API 21 support, and backup-excluded credentials. Never share WebView cookies, follow API redirects, weaken certificate verification, or automatically retry an ambiguous job submission. Forgetting a login is local, not server revocation.

Inspect the named source methods and tests before editing. Keep coordination in `MusicServer`, protocol decisions in `MusicServerProtocol`, and blocking transport/storage on the worker.

Open [android-shell](../android-shell/SKILL.md) only for Preferences presentation or activity-intent integration changes. Consult [shared contracts](../repo-router/references/shared-contracts.md) when crossing trust/lifecycle boundaries and [validation](../repo-router/references/validation.md) when selecting commands. Do not load native extraction, updater, or adblocking implementation for routine music work.
