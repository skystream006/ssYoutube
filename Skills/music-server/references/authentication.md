# Browser login and encrypted sessions

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Use the actual absolute checkout root if different.

## Entry points and flow

| Concern | Source anchors |
| --- | --- |
| Origin/PKCE/callback/expiry rules | `MusicServerProtocol.normalizeOrigin`, `newLogin`, `authorizationUrl`, `callbackCode`, `requirePending`, `Pending`, `Session` |
| Login, restoration, cancellation | `MusicServer` constructor, `login`, `acceptCallback`, `forget`, `refresh`, `onCleared` |
| Token exchange | `MusicServerClient.exchange`, `post`, `requireNoCookieHandler` |
| Durable credentials | `MusicServerStore.session`, `pending`, `save`, `read`, `write`, `wrappingCipher` |
| Browser return | `MusicServerCallbackActivity.onCreate`; `MainActivity.handleMusicServerCallback`, `openMusicServerBrowser`, `showMusicServerLogin` |

Flow: validated HTTPS origin → persist pending state/verifier → external browser
`/app-login` → `com.ssytdlp.app:/oauth/callback` → validate pending state/age →
`POST /api/auth/app/token` → encrypted session → published UI status.

Callback registration is in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`.
The external ssYTDLP_Server implementation is not in this checkout. Do not infer
new endpoints or server-side authorization behavior from Android UI requirements.

## Invariants and configuration

- User supplies the server's HTTPS passkey origin, including a nondefault port
  when needed. Reject credentials, paths, query/fragment, and cleartext origins.
  Keep platform certificate verification; there is no certificate-bypass mode.
- Generate random state/verifier and S256 PKCE. Bind both to the saved origin;
  validate exact callback shape, state, code, and pending age before exchange.
- The private-use callback scheme is shared, not a verified app link. Manifest
  routing alone does not authenticate a callback.
- Persist pending login before launching the external browser. A cold-start
  callback can arrive during restoration and must be handled by the coordinator.
  Remove authorization data from the retained launch intent after handoff.
- `MusicServer` is an AndroidViewModel with background IO and main-thread state.
  Respect busy/restoring/cleared state; dismissing Preferences must not own session
  lifetime. `forget` clears local session/pending data, not remote browser login.
- Keep WebView cookies out of token/job networking. Requests are bounded,
  interruptible, and do not follow redirects; a process-wide CookieHandler is
  rejected rather than temporarily mutated.
- `MusicServerStore` uses AtomicFile in no-backup storage with AES-GCM and a fresh
  wrapped key/IV per write. Preserve versioned reads and Android 5 compatibility;
  its legacy RSA wrapping differs from Android 6+ OAEP. Do not expose this
  app-private compatibility path as a remote decryption interface.
- Never log tokens, verifier/state, callback URLs, or raw server/parser failures.

## Validation

- Protocol/origin/PKCE/callback rules: `MusicServerProtocolTest`.
- Exchange/HTTP/response isolation: `MusicServerClientTest` (fake connections/codec).
- Callback/cleartext registration: `MusicServerManifestTest`.
- Changed preference controls: relevant `PreferencesLayoutTest` cases.

Use [validation commands](../../repo-router/references/validation.md).
No JVM test proves Android Keystore or passkey/browser interaction. Device-check
authorization, cancel/retry, expired/revoked session, callback after process/activity
recreation, restart persistence, and local forgetting on affected API levels.
Read [jobs](jobs.md) only when session changes affect submission; consult shell for
actual UI/callback wiring changes, not for a protocol-only fix.

## Documentation

The **Music server** entry in
[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features)
describes external authorization and session behavior.
[Build and test](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test)
documents Keystore compatibility and device checks.
