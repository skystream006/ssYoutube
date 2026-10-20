# Playlist and media jobs

## Source and consumers

- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerProtocol.java`: `playlistUrl`, `mediaUrl`, `preservePlaylist`, `youtubeUri`, `playlistParameter`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServerClient.java`: `createJob` validates the chosen URL/session and calls bounded `post`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MusicServer.java`: `send`, `refresh`, `forget`, `Status`; serial worker work survives activity recreation.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MainActivity.java`: `currentMusicMediaUrl`, `showPreferences`, `startUrl`, `YouTubeWebViewClient.handleUrl`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`: `music_server_panel`, `music_server_actions`, `music_login`, `music_send_playlist`, `music_send_media`, `music_forget`, `music_status`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml`: `music_job_started`, `music_job_failed`, `music_login_expired`, `music_forbidden`, `music_duplicate`, `music_rate_limited`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable/bg_music_server.xml`: compact shaded-panel background.

## Selection and submission

`playlistUrl` returns the original validated HTTPS YouTube URL unchanged only with one valid nonempty `list` parameter. Accept supported YouTube hosts/`youtu.be`, default HTTPS port, and no userinfo; reject lookalikes, ambiguous list parameters, and malformed encoding. Do not replace playlist selection with a bare video ID.

`preservePlaylist` carries validated `list` and an optional numeric `index` through canonicalization; an invalid/duplicate optional index does not discard a valid playlist. Preserve canonical timestamps, other query parameters, and fragments. Its consumers include incoming links in `startUrl` and shortlink normalization in `handleUrl`; changing it can silently remove the playlist button.

`currentMusicMediaUrl` passes the current WebView URL and `nativePlayer.getVideoId()` only when `hasVideo()`. `mediaUrl` handles watch, Shorts, live, embed, and shortlink videos. Strip `list`, `listType`, `list_type`, `playlist`, `index`, `start_radio`, `radio`, `playnext`, and `shuffle` case-insensitively while retaining unrelated raw query/fragment data. If the native video differs from the page or browsing moved elsewhere, select its canonical watch URL; an invalid native ID never overrides valid page media. No selection returns null.

`createJob` posts exactly `url` and `downloadType: "video"` to the authenticated origin's `/api/jobs`, using the session token in the `Authorization` header (scheme `Bearer`) and expecting HTTP 202. This is external-server job creation, not local download, extraction, or native playback. Never share YouTube cookies, follow API redirects, proxy requests, or automatically retry. Even a timeout or invalid response may follow an accepted job: tell users to inspect the server job list first.

`showPreferences` shows playlist only when logged in with valid playlist context; media is visible when logged in but disabled without selection. Busy status disables repeat actions. The dialog removes its observer on dismissal; keep state in the ViewModel, not dialog views. `send` maps 401 to session clearing/login expiry, 403 to permission denial, 409 to duplicate, and 429 to rate limiting; other failures stay generic and inline. Server authorization/queue behavior belongs to ssYTDLP_Server, not this repository.

## Validation and escalation

Tests live in `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`. Target `MusicServerProtocolTest` (`preservesExternalPlaylistAndIndexWhenCanonicalizingVideoLinks`, `stripsPlaylistParametersButPreservesIndependentRawQueryAndFragment`, `supportsShortsLiveEmbedAndShortlinks`, `usesNativeVideoWhileBrowsingElsewhere`), `MusicServerClientTest` (`postsVideoJobsWithOnlyBearerAuthenticationAndChosenUrl`, `propagatesHttpStatusWithoutReadingBodyFollowingRedirectOrRetrying`), and `PreferencesLayoutTest.musicServerUsesCompactShadedHorizontalControls`. Commands: [shared validation](../../repo-router/references/validation.md).

Device-check playlist-only pages, watch-with-list, Shorts, incoming shortlinks, miniplayer browsing, no selection, rapid taps, recreation while busy, expiry, and lost responses. Inspect light/dark, narrow widths, large fonts, and inline accessibility announcements. Use [authentication](authentication.md) only for session/transport failures; use [android-shell](../../android-shell/SKILL.md) for panel/intent changes.

User promises: [README Music server feature](/home/runner/work/ssYoutube/ssYoutube/README.md#features) and [device checks](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test).
