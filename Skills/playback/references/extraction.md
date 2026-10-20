# Anonymous extraction and stream selection

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Class names identify files in those directories; substitute the actual absolute
checkout root when necessary.

## Find the relevant layer

| Problem | Source anchors |
| --- | --- |
| Wrong format, quality, manifest, or audio language | `NativeStreamExtractor.extract`, `selectStreams`, `bestVideo`, `bestAudio`, `manifest` |
| Localized extractor request | `NativeStreamExtractor.localizedExtractor`, `initialize` |
| Headers, redirects, request/response bounds, cancellation | `ExtractorDownloader.execute`, `applyRequestHeaders`, `Cancellation` |
| HTTPS, allowed hosts, credentials, consent | `NativeNetworkPolicy.requireHttps`, `redirect`, `anonymousConsentCookies` |
| Async result applied to wrong video/language | `NativePlayerView.startExtraction`, `isCurrent`, `cancelExtraction` |

NewPipeExtractor resolves page/video identifiers into media URLs on the extraction
executor. `NativeStreamExtractor.Result` carries video/audio URLs, MIME hints,
kind, and live status to the player. A YouTube watch URL is not an mpv media URL.
Do not introduce a proxy extraction service or an external-player dependency.

## Invariants

- Keep browser/account cookies out of extraction. Strip credential headers; only
  extractor-supplied `CONSENT`/`SOCS` cookies may survive, on the same HTTPS origin.
- Validate every extraction redirect; keep host, scheme, port, request-method,
  size, timeout, and cancellation checks. Cancellation disconnects active IO.
- Capture preferred language with the request, localize the individual extractor
  using `forceLocalization`, and pass that captured language to mpv. Do not mutate
  global NewPipe localization while concurrent or stale work can exist.
- Keep selection automatic: compatibility-aware progressive/adaptive selection
  and available manifests, with live manifest handling and language fallback.
  Current selection limits and scoring belong in source, not duplicated constants here.
- Generation/video checks prevent stale results from preparing a replacement
  player. Activity pause/close must cancel pending extraction.
- Do not persist expiring extracted URLs or reuse browser authentication to make
  private/restricted videos play.

## Tests and boundaries

| Change | Start with |
| --- | --- |
| Selection/localization | `NativeStreamExtractorTest` |
| Downloader headers | `ExtractorDownloaderTest` |
| URL/redirect/cookie rules | `NativeNetworkPolicyTest` |
| Result consumed by mpv / language propagation | `MpvPlaybackRequestTest` |
| State/cancellation intent | `NativePlaybackStateTest` plus device checks |

Use [validation commands](../../repo-router/references/validation.md).
Fake/unit tests do not verify current live YouTube extraction. Device-check the
affected progressive/adaptive/live case, preferred-language fallback, retry,
rapid video changes, and close/pause while loading.

Read [player](player.md) only if result consumption changes. Consult shell for
language-code/preference changes and delivery for NewPipe/desugaring/dependency
changes. Native libmpv media redirects are not the downloader's redirects; do not
claim this Java policy applies to all requests made inside the native engine.

## Documentation

[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features):
**Preferences**, **Native video playback**, and **Playback limitations** describe
language selection, anonymous extraction, and native transport limitations.
