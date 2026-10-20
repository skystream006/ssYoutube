# Extraction and audio selection

## Source anchors

- [NativeStreamExtractor.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativeStreamExtractor.java): `localizedExtractor`, `extract`, `manifest`, `selectStreams`, `bestVideo`, `bestAudio`, `videoCompatibility`.
- [ExtractorDownloader.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/ExtractorDownloader.java): `begin`/`end`, `execute`, `applyRequestHeaders`, `Cancellation.cancel`/`check`/`attach`.
- [NativeNetworkPolicy.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/NativeNetworkPolicy.java): `requireHttps`, `redirect`, `isMediaUrl`, `anonymousConsentCookies`.

## Invariants

`NativePlayerView.startExtraction` captures the selected ID, language, cancellation object, and generation before submitting background work. Pass that captured language through extraction and `MpvPlayer.setStream`; changing preferences must not relocalize an in-flight request or interrupt active playback. New loads, retries, and restoration use the current preference.

`localizedExtractor` validates the eleven-character ID, initializes NewPipe once, and calls `forceLocalization` on the individual extractor. [Preferences.java](/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/Preferences.java), `normalizeLanguage`/`DEFAULT_LANGUAGE`, supplies supported values and English fallback; do not replace this with mutable global localization.

Resolve media directly, off the UI thread; a watch/share webpage is not an mpv media URL. Do not introduce an extraction proxy.

Live streams prefer HLS, then DASH; other streams try progressive selection before manifest fallback. `bestVideo` accepts trusted progressive MP4/WebM URLs with positive resolution up to 1080p. Compatibility scoring prioritizes AVC, then unknown-codec MP4, then WebM; MP4 alone does not prove AVC. Separate video needs usable audio and, when muxed video exists, higher resolution without worse compatibility.

`bestAudio` ranks matching language, including regional variants, above original-track status, MP4 preference, and bounded bitrate. Missing language falls back to original/available audio. Matching separate audio can accompany muxed video because muxed streams lack audio-language metadata. Selection remains automatic; there is no manual quality selector.

Extraction is anonymous: never introduce WebView cookies, account credentials, or a cookie jar. Header copying strips credentials, `Host`, and `Content-Length`; preserve extractor-supplied user agents. Only syntactically restricted `CONSENT`/`SOCS` cookies survive, and only on the original HTTPS origin, including effective port.

`execute` requires an active cancellation scope, supports GET/POST/HEAD, bounds requests to 1 MiB and responses to 12 MiB after decompression, and disconnects in `finally`. Preserve 15-second connect/20-second read timeouts, five-redirect limit, and validation of every extraction redirect. `requireHttps` rejects credentials, fragments, nonstandard ports, and hosts outside its purpose-specific allowlist.

Initial media URLs allow HTTPS YouTube/Googlevideo hosts. **libmpv/FFmpeg redirects and manifest requests do not receive this Java per-redirect host validation or response-size bound.** Do not claim extraction transport restrictions also govern native media transport.

## Checks and consumers

Use [shared validation](../../repo-router/references/validation.md), selecting:

| Change | Existing tests |
| --- | --- |
| Localization, codec ranking, muxed/adaptive audio | `NativeStreamExtractorTest` |
| Header preservation and credential stripping | `ExtractorDownloaderTest` |
| HTTPS, redirects, same-origin consent | `NativeNetworkPolicyTest` |
| Language forwarded to manifest/native playback | `MpvPlaybackRequestTest` |

On device, compare muxed/adaptive VOD and live manifests; change language during extraction, retry, and restore. Exercise unavailable-language fallback, network interruption, and closing/replacing pending extraction. Unit tests do not prove current YouTube availability.

Continue into [player](player.md) only for result consumption or cancellation races; consult [shared contracts](../../repo-router/references/shared-contracts.md) before changing credential boundaries. [README Features](/home/runner/work/ssYoutube/ssYoutube/README.md#features): **Preferences**, **Native video playback**, **Playback limitations**.
