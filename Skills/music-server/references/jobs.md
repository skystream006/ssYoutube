# Playlist and media job submission

Java directory: `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
Tests: `/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Resolve against the actual absolute checkout root if different.

## Source map

| Concern | Source anchors |
| --- | --- |
| Playlist validation/preservation | `MusicServerProtocol.playlistUrl`, `preservePlaylist` |
| Single video/Shorts/miniplayer fallback | `MusicServerProtocol.mediaUrl`; `MainActivity.currentMusicMediaUrl` |
| Busy/session/status coordination | `MusicServer.send`, `refresh`, `status`, `publish` |
| HTTP contract and bounds | `MusicServerClient.createJob`, `post`, response parsing |
| Controls and selected page | Music observers/listeners in `MainActivity.showPreferences`; incoming-link normalization in `startUrl`, `YouTubeWebViewClient.handleUrl` |

Relevant UI:
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/layout/dialog_preferences.xml`:
  `music_server_panel`, `music_send_playlist`, `music_send_media`, `music_status`.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/values/strings.xml`:
  music status and action labels.
- `/home/runner/work/ssYoutube/ssYoutube/app/src/main/res/drawable/bg_music_server.xml`:
  music row background.

## Contract

- This client submits video jobs to the saved origin's `POST /api/jobs`, expecting
  HTTP 202, with `url` and `downloadType` set by `createJob`. It is not a local job
  runner or a server implementation.
- “Send playlist” requires a validated nonempty list parameter. Preserve playlist
  parameters when shell navigation canonicalizes a supported incoming video link.
- “Send media” strips playlist context and selects the current video, including
  Shorts; eligible browsing pages can fall back to the active native miniplayer.
  Keep the protocol helper authoritative for supported URL/path combinations.
- Disable repeated submissions while busy. Expired sessions require login; map
  unauthorized/forbidden/duplicate/rate-limited statuses without exposing response
  bodies or credentials.
- Do not automatically retry job creation: a failed response may follow an
  accepted job. Preserve the user-facing uncertainty rather than creating duplicates.
- Keep bounded HTTPS, no redirect/cookie sharing, and sanitized failures. Read
  [authentication](authentication.md) only when changing those shared client rules
  or session semantics.

## Validation

Start with relevant `MusicServerProtocolTest` URL-selection/preservation cases and
`MusicServerClientTest` payload/status/network cases. Add `PreferencesLayoutTest`
only for control/resource changes.

Use [validation commands](../../repo-router/references/validation.md).
Device-check playlist versus single video, Shorts, miniplayer fallback, disabled
actions with no selection, repeated taps, failure messaging, and expired login.
For UI changes include light/dark, narrow widths, and large fonts.

Consult shell for link normalization and controls. Using a native player's video
ID does not require loading extractor/mpv internals. Server-side API changes require
separate confirmed contract information; do not edit an external repository here.

## Documentation

The **Music server** entry in
[README features](/home/runner/work/ssYoutube/ssYoutube/README.md#features)
describes playlist/media selection, miniplayer fallback, and the warning to check
the server's job list before retrying an uncertain submission.
