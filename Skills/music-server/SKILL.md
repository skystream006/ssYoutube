---
name: music-server
description: >-
  Handles ssYouTube's external music-server login, HTTPS origins, PKCE browser
  callbacks, encrypted sessions, and playlist/media job submission. Use for
  music authentication, persistence, or remote-job integration changes, not
  Google sign-in inside the browsing WebView.
---

# Music-server integration

- Choose [authentication](references/authentication.md) for login/session work or
  [jobs](references/jobs.md) for URL selection and submission.
- Start with the matching `MusicServer` class/method and its named tests.
- Preserve origin-bound authorization, cookie isolation, encrypted storage, and
  non-retrying job-submission semantics.
- Consult [shell](../android-shell/SKILL.md) only for callback, current-media, or
  preference UI changes. The external server is an API contract, not local source.
- Normally ignore native extraction, adblocking, GitHub update/release code, and
  unrelated diagnostics implementation.
- Run the mapped protocol/client/manifest tests using
  [validation commands](../repo-router/references/validation.md); add device checks
  for browser authorization, Keystore, and recreation behavior.
