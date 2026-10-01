package com.skystream.ssyoutube;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure URL and PKCE rules; pending-login consumption belongs to the caller. */
public final class MusicServerProtocol {
    public static final String REDIRECT_URI = "com.ssytdlp.app:/oauth/callback";
    static final long LOGIN_MAX_AGE_MS = 10 * 60_000L;
    private static final int MAX_URL_LENGTH = 8192;
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9_-]{1,256}");
    private static final Pattern PLAYLIST_INDEX = Pattern.compile("[0-9]{1,9}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9_-]{43,128}");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,512}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private MusicServerProtocol() { }

    public static final class Pending {
        public final String origin;
        public final String verifier;
        public final String state;
        public final long createdAt;

        public Pending(String origin, String verifier, String state, long createdAt)
                throws IOException {
            this.origin = normalizeOrigin(origin);
            if (verifier == null || !VERIFIER.matcher(verifier).matches()
                    || state == null || !STATE.matcher(state).matches() || createdAt < 0) {
                throw new IOException("Invalid pending music server login");
            }
            this.verifier = verifier;
            this.state = state;
            this.createdAt = createdAt;
        }
    }

    public static final class Session {
        public final String origin;
        public final String token;
        public final long expiresAt;

        public Session(String origin, String token, long expiresAt) throws IOException {
            this.origin = normalizeOrigin(origin);
            if (token == null || !STATE.matcher(token).matches() || expiresAt <= 0) {
                throw new IOException("Invalid music server session");
            }
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    public static String normalizeOrigin(String value) throws IOException {
        if (value == null || value.length() > 2048 || hasControl(value)) {
            throw new IOException("Enter an HTTPS server origin");
        }
        try {
            URI uri = new URI(value.trim()).parseServerAuthority();
            String path = uri.getRawPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IOException("Enter an HTTPS server origin without a path");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            if (!uri.getRawAuthority().equalsIgnoreCase(host + port)) {
                throw new IOException("Invalid music server origin");
            }
            return "https://" + host + (uri.getPort() == 443 ? "" : port);
        } catch (URISyntaxException | IllegalArgumentException error) {
            throw new IOException("Invalid music server origin");
        }
    }

    public static Pending newLogin(String origin, long now) throws IOException {
        return new Pending(origin, randomValue(), randomValue(), now);
    }

    public static String authorizationUrl(Pending pending) {
        try {
            String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                    .digest(pending.verifier.getBytes(StandardCharsets.US_ASCII)));
            return pending.origin + "/app-login"
                    + "?redirect_uri=com.ssytdlp.app%3A%2Foauth%2Fcallback"
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256"
                    + "&state=" + pending.state;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    public static String callbackCode(String callback, Pending pending, long now)
            throws IOException {
        requirePending(pending, now);
        if (callback == null || callback.length() > 2048
                || !callback.startsWith(REDIRECT_URI + "?")) {
            throw new IOException("Invalid music server callback");
        }
        try {
            URI uri = new URI(callback);
            if (uri.isOpaque() || uri.getRawAuthority() != null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || !"com.ssytdlp.app".equals(uri.getScheme())
                    || !"/oauth/callback".equals(uri.getRawPath())) {
                throw new IOException("Invalid music server callback");
            }
            String code = null;
            String state = null;
            for (Parameter parameter : parameters(uri.getRawQuery())) {
                if ("code".equals(parameter.name) && code == null) {
                    code = parameter.value;
                } else if ("state".equals(parameter.name) && state == null) {
                    state = parameter.value;
                } else {
                    throw new IOException("Invalid music server callback parameters");
                }
            }
            requireCode(code);
            if (state == null || !MessageDigest.isEqual(
                    pending.state.getBytes(StandardCharsets.US_ASCII),
                    state.getBytes(StandardCharsets.UTF_8))) {
                throw new IOException("Music server login state did not match");
            }
            return code;
        } catch (URISyntaxException error) {
            throw new IOException("Invalid music server callback");
        }
    }

    static void requirePending(Pending pending, long now) throws IOException {
        if (pending == null || now < pending.createdAt
                || now - pending.createdAt >= LOGIN_MAX_AGE_MS) {
            throw new IOException("Music server login expired; please sign in again");
        }
    }

    static void requireCode(String code) throws IOException {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IOException("Invalid music server authorization code");
        }
    }

    public static String playlistUrl(String pageUrl) {
        try {
            URI uri = youtubeUri(pageUrl);
            String list = unique(parameters(uri.getRawQuery()), "list");
            return list != null && PLAYLIST_ID.matcher(list).matches() ? pageUrl : null;
        } catch (IOException error) {
            return null;
        }
    }

    public static String preservePlaylist(String canonicalUrl, String sourceUrl) {
        if (playlistUrl(sourceUrl) == null) {
            return canonicalUrl;
        }
        try {
            URI canonical = youtubeUri(canonicalUrl);
            List<Parameter> sourceQuery = parameters(youtubeUri(sourceUrl).getRawQuery());
            String list = unique(sourceQuery, "list");
            String index = null;
            try {
                index = unique(sourceQuery, "index");
            } catch (IOException ignored) {
                // An ambiguous optional position must not discard the valid playlist.
            }
            StringBuilder result = new StringBuilder();
            result.append(canonical.getScheme()).append("://").append(canonical.getRawAuthority())
                    .append(canonical.getRawPath());
            boolean first = true;
            for (Parameter parameter : parameters(canonical.getRawQuery())) {
                if (!"list".equalsIgnoreCase(parameter.name)
                        && !"index".equalsIgnoreCase(parameter.name)) {
                    result.append(first ? '?' : '&').append(parameter.raw);
                    first = false;
                }
            }
            result.append(first ? '?' : '&').append("list=").append(list);
            if (index != null && PLAYLIST_INDEX.matcher(index).matches()) {
                result.append("&index=").append(index);
            }
            if (canonical.getRawFragment() != null) {
                result.append('#').append(canonical.getRawFragment());
            }
            return result.length() <= MAX_URL_LENGTH ? result.toString() : canonicalUrl;
        } catch (IOException error) {
            return canonicalUrl;
        }
    }

    public static String mediaUrl(String pageUrl, String activeVideoId) {
        String active = activeVideoId != null && VIDEO_ID.matcher(activeVideoId).matches()
                ? activeVideoId : null;
        try {
            URI uri = youtubeUri(pageUrl);
            List<Parameter> query = parameters(uri.getRawQuery());
            String id = videoId(uri, query);
            if (id != null && (active == null || active.equals(id))) {
                StringBuilder result = new StringBuilder();
                result.append(uri.getScheme()).append("://").append(uri.getRawAuthority())
                        .append(uri.getRawPath());
                boolean first = true;
                for (Parameter parameter : query) {
                    if (!playlistParameter(parameter.name)) {
                        result.append(first ? '?' : '&').append(parameter.raw);
                        first = false;
                    }
                }
                if (uri.getRawFragment() != null) {
                    result.append('#').append(uri.getRawFragment());
                }
                return result.toString();
            }
        } catch (IOException ignored) {
            // Native playback may continue while the browser is on an unrelated page.
        }
        return active == null ? null : "https://www.youtube.com/watch?v=" + active;
    }

    private static URI youtubeUri(String value) throws IOException {
        if (value == null || value.length() > MAX_URL_LENGTH || hasControl(value)) {
            throw new IOException("Invalid YouTube URL");
        }
        try {
            URI uri = new URI(value).parseServerAuthority();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.isOpaque() || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("Invalid YouTube URL");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if ((!host.equals("youtube.com") && !host.equals("www.youtube.com")
                    && !host.equals("m.youtube.com") && !host.equals("music.youtube.com")
                    && !host.equals("youtu.be"))
                    || !uri.getRawAuthority().equalsIgnoreCase(
                            host + (uri.getPort() == 443 ? ":443" : ""))) {
                throw new IOException("Invalid YouTube URL");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new IOException("Invalid YouTube URL");
        }
    }

    private static String videoId(URI uri, List<Parameter> query) throws IOException {
        String path = uri.getRawPath();
        String id = null;
        if ("youtu.be".equalsIgnoreCase(uri.getHost())) {
            id = path.length() > 1 ? path.substring(1) : null;
        } else if ("/watch".equals(path)) {
            id = unique(query, "v");
        } else {
            for (String prefix : new String[] {"/shorts/", "/live/", "/embed/"}) {
                if (path.startsWith(prefix)) {
                    id = path.substring(prefix.length());
                    break;
                }
            }
        }
        if (id != null && id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
    }

    private static boolean playlistParameter(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "list":
            case "listtype":
            case "list_type":
            case "playlist":
            case "index":
            case "start_radio":
            case "radio":
            case "playnext":
            case "shuffle":
                return true;
            default:
                return false;
        }
    }

    private static String unique(List<Parameter> parameters, String name) throws IOException {
        String value = null;
        for (Parameter parameter : parameters) {
            if (name.equals(parameter.name)) {
                if (value != null) {
                    throw new IOException("Ambiguous YouTube URL");
                }
                value = parameter.value;
            }
        }
        return value;
    }

    private static List<Parameter> parameters(String query) throws IOException {
        List<Parameter> result = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        try {
            for (String raw : query.split("&", -1)) {
                int equals = raw.indexOf('=');
                String name = URLDecoder.decode(equals < 0 ? raw : raw.substring(0, equals),
                        "UTF-8");
                String value = URLDecoder.decode(equals < 0 ? "" : raw.substring(equals + 1),
                        "UTF-8");
                if (name.isEmpty() || hasControl(name) || hasControl(value)
                        || name.indexOf('\ufffd') >= 0 || value.indexOf('\ufffd') >= 0) {
                    throw new IOException("Invalid URL parameters");
                }
                result.add(new Parameter(name, value, raw));
            }
            return result;
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid URL parameters");
        }
    }

    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 0x20 || value.charAt(i) == 0x7f) {
                return true;
            }
        }
        return false;
    }

    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String base64Url(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 5) / 6);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 6) {
                bits -= 6;
                result.append(BASE64[(buffer >>> bits) & 63]);
            }
        }
        if (bits != 0) {
            result.append(BASE64[(buffer << (6 - bits)) & 63]);
        }
        return result.toString();
    }

    private static final class Parameter {
        final String name;
        final String value;
        final String raw;

        Parameter(String name, String value, String raw) {
            this.name = name;
            this.value = value;
            this.raw = raw;
        }
    }
}
