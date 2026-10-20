# Browser login and credential persistence

## Source and consumers

- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServer.java`: `login`, `acceptCallback`, `refresh`, `forget`, `takeBrowserUrl`, `onCleared`; lifecycle-aware owner, single worker, main-thread `Status` publication.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerProtocol.java`: `normalizeOrigin`, `newLogin`, `authorizationUrl`, `callbackCode`, `Pending`, `Session`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerClient.java`: `exchange`, `post`, `requireNoCookieHandler`, `parseResponse`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerStore.java`: `session`, `pending`, overloaded `save`, `read`, `write`, `wrappingCipher`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerCallbackActivity.java`: `onCreate` forwards browser returns with `CLEAR_TOP | SINGLE_TOP`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java`: `showMusicServerLogin`, `openMusicServerBrowser`, `handleMusicServerCallback`, `showPreferences`; callback handling also runs from `onCreate`/`onNewIntent`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`: `music_login`, `music_forget`, and inline `music_status` consume login/busy/pending state.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/AndroidManifest.xml`: exported, browsable, no-history callback activity; scheme-only `com.ssytdlp.app` filter; cleartext remains disabled.
- `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle`: `minSdk 21`, core-library desugaring for `Instant`.

## Contracts to preserve

Accept an HTTPS origin only: no credentials, query, fragment, or non-root path. Normalize host/default port while preserving a configured nondefault port. The server is external; approved accounts, passkeys, and certificates trusted by Android/browser are deployment prerequisites, not features implemented here.

`newLogin` generates independent 32-byte verifier/state values. Persist pending login before launching external `ACTION_VIEW` authorization at `/app-login`, sending only S256 challenge, state, and `com.ssytdlp.app:/oauth/callback`. The private-use scheme is shared, not a verified app link. `callbackCode` requires the exact path/scheme, no authority/fragment, exactly one code/state, matching state, and a pending login younger than ten minutes; future-dated state fails.

Bind exchange to the stored pending origin, never callback input. `exchange` posts `code`, `codeVerifier`, `redirectUri` to `/api/auth/app/token`, requires HTTP 200 and an unexpired session with `tokenType` equal to `Bearer`. API transport is HTTPS-only, nonredirecting, nonretrying, and cookie-free; refuse ambient `CookieHandler` rather than changing global state. Keep 15-second connection/read timeouts, elapsed-time checks, 32-KiB request/128-KiB response bounds, and strict UTF-8/JSON validation. Preserve sanitized failures without credentials, response bodies, or unsafe exception causes.

`acceptCallback` queues a cold-start callback during restoration, then consumes pending state before exchange. `handleMusicServerCallback` clears the launch intent so authorization codes are not retained. `forget` clears local pending/session files, not browser login or server authorization.

`music-login`/`music-session` are `AtomicFile`s under `getNoBackupFilesDir()`, encrypted with AES-GCM and fresh wrapped keys. API 23+ uses Keystore RSA-OAEP; API 21/22 uses legacy PKCS#1 wrapping, still readable after upgrade. Restoration failures discard credentials. Keep the local Base64url implementation/API compatibility; do not introduce API-26-only encoding or plaintext preferences.

## Validation and escalation

Tests live in `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`. Target `MusicServerProtocolTest` (`authorizationUrlContainsOnlyChallengeStateAndExactRedirect`, `rejectsMissingUnknownDuplicateAndMalformedCallbackParameters`, `rejectsUnsolicitedExpiredAndFutureDatedLogins`), `MusicServerClientTest` (`exchangesOnlyAtPendingOriginWithExactPayloadAndNoAuthentication`, `refusesAmbientCookieHandlersWithoutChangingGlobalState`), and `MusicServerManifestTest`. Use [shared validation](../../repo-router/references/validation.md) for commands.

Device-check browser cancellation/retry, unavailable browser, warm/cold returns, process recreation, restart persistence, expiry/revocation, and API 21/modern Keystore behavior. JVM transport tests substitute a JSON codec; real Android JSON, Keystore, and passkeys need device coverage. Follow [jobs](jobs.md) only for submission/UI selection; escalate Preferences/intent changes to [android-shell](../../android-shell/SKILL.md).

User promises: [README Music server feature](/home/runner/work/ssYoutube/ssYoutube/README.md#features) and [device/compatibility checks](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test).
