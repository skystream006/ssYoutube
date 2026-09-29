package com.skystream.ssyoutube;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A validated video target, independent of Android and the playback implementation. */
final class PlaybackRequest {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern SECONDS = Pattern.compile("[0-9]+");
    private static final Pattern DURATION =
            Pattern.compile("(?:([0-9]+)h)?(?:([0-9]+)m)?(?:([0-9]+)s)?");
    private static final long MAX_START_SECONDS = 7L * 24 * 60 * 60;

    final String videoId;
    final long startPositionMs;

    private PlaybackRequest(String videoId, long startPositionMs) {
        this.videoId = videoId;
        this.startPositionMs = startPositionMs;
    }

    static PlaybackRequest fromUrl(String url) {
        URI uri = trustedUri(url);
        if (uri == null) {
            return null;
        }
        Map<String, String> query = queryParameters(uri.getRawQuery());
        if (query == null) {
            return null;
        }

        String path = uri.getRawPath();
        String videoId;
        if (isShortlinkHost(uri.getHost())) {
            videoId = pathId(path, "/");
        } else if ("/watch".equals(path)) {
            videoId = query.get("v");
        } else if (path.startsWith("/shorts/")) {
            videoId = pathId(path, "/shorts/");
        } else if (path.startsWith("/live/")) {
            videoId = pathId(path, "/live/");
        } else if (path.startsWith("/embed/")) {
            videoId = pathId(path, "/embed/");
        } else {
            return null;
        }
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            return null;
        }

        long position;
        if (query.containsKey("t")) {
            position = timestampMs(query.get("t"), true);
        } else if (query.containsKey("start")) {
            position = timestampMs(query.get("start"), false);
        } else {
            String fragment = decodeQueryComponent(uri.getRawFragment());
            position = timestampMs(fragment != null && fragment.startsWith("t=")
                    ? fragment.substring(2) : fragment, true);
        }
        return new PlaybackRequest(videoId, position);
    }

    String watchUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /**
     * Checks the browser origin, not whether the path names a video. Shortlink hosts are excluded:
     * they are accepted only by {@link #fromUrl(String)} as direct video targets.
     */
    static boolean isYouTubePage(String url) {
        URI uri = trustedUri(url);
        return uri != null && !isShortlinkHost(uri.getHost());
    }

    private static URI trustedUri(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url).parseServerAuthority();
            String scheme = uri.getScheme();
            boolean https = "https".equalsIgnoreCase(scheme);
            if ((!https && !"http".equalsIgnoreCase(scheme))
                    || uri.isOpaque() || uri.getRawUserInfo() != null || uri.getHost() == null) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.US);
            if (!host.equals("youtube.com") && !host.equals("www.youtube.com")
                    && !host.equals("m.youtube.com") && !host.equals("music.youtube.com")
                    && !isShortlinkHost(host)) {
                return null;
            }
            int port = uri.getPort();
            if ((port != -1 && port != (https ? 443 : 80))
                    || (port == -1 && !uri.getRawAuthority().equalsIgnoreCase(host))) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isShortlinkHost(String host) {
        return "youtu.be".equalsIgnoreCase(host) || "www.youtu.be".equalsIgnoreCase(host);
    }

    private static String pathId(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        String id = path.substring(prefix.length());
        return id.endsWith("/") ? id.substring(0, id.length() - 1) : id;
    }

    private static Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null) {
            return parameters;
        }
        for (String parameter : rawQuery.split("&")) {
            int separator = parameter.indexOf('=');
            String key = decodeQueryComponent(separator < 0
                    ? parameter : parameter.substring(0, separator));
            String value = decodeQueryComponent(separator < 0
                    ? "" : parameter.substring(separator + 1));
            if (key == null || value == null) {
                return null;
            }
            if ("v".equals(key) || "t".equals(key) || "start".equals(key)) {
                // Do not let different consumers disagree about duplicate video or time parameters.
                if (parameters.containsKey(key)) {
                    return null;
                }
                parameters.put(key, value);
            }
        }
        return parameters;
    }

    private static String decodeQueryComponent(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return null;
        }
    }

    /** Query t takes precedence over start and the fragment; invalid or over-seven-day times use 0. */
    private static long timestampMs(String value, boolean allowUnits) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            if (SECONDS.matcher(value).matches()) {
                long seconds = Long.parseLong(value);
                return seconds <= MAX_START_SECONDS ? seconds * 1000 : 0;
            }
            if (!allowUnits) {
                return 0;
            }
            Matcher matcher = DURATION.matcher(value);
            if (!matcher.matches()) {
                return 0;
            }
            long seconds = 0;
            int[] units = {3600, 60, 1};
            for (int i = 0; i < units.length; i++) {
                String component = matcher.group(i + 1);
                if (component != null) {
                    long amount = Long.parseLong(component);
                    if (amount > (MAX_START_SECONDS - seconds) / units[i]) {
                        return 0;
                    }
                    seconds += amount * units[i];
                }
            }
            return seconds * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
