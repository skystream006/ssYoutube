package com.skystream.ssyoutube;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared restrictions for anonymous extraction and media requests, including every redirect. */
final class NativeNetworkPolicy {
    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int READ_TIMEOUT_MS = 20_000;
    static final int MAX_REDIRECTS = 5;
    static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36";

    private NativeNetworkPolicy() { }

    static URI requireHttps(String value, boolean media) throws IOException {
        try {
            if (value == null || value.length() > 65_536) {
                throw new IOException("Invalid native request");
            }
            URI uri = new URI(value).parseServerAuthority();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("Unsafe native request");
            }
            String host = uri.getHost().toLowerCase(Locale.US);
            String authority = host + (uri.getPort() == 443 ? ":443" : "");
            if (!authority.equalsIgnoreCase(uri.getRawAuthority())) {
                throw new IOException("Invalid native authority");
            }
            boolean allowed = domain(host, "googlevideo.com") || domain(host, "youtube.com");
            if (!media) {
                allowed |= domain(host, "youtube-nocookie.com")
                        || domain(host, "ytimg.com")
                        || host.equals("youtubei.googleapis.com")
                        || host.equals("www.google.com");
            }
            if (!allowed) {
                throw new IOException("Unsupported native host");
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException error) {
            throw new IOException("Invalid native request");
        }
    }

    static URI redirect(URI current, String location, boolean media) throws IOException {
        if (location == null) {
            throw new IOException("Missing native redirect");
        }
        try {
            return requireHttps(current.resolve(location).toString(), media);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid native redirect");
        }
    }

    static boolean isMediaUrl(String value) {
        try {
            requireHttps(value, true);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    static boolean isCredentialHeader(String name) {
        return "Cookie".equalsIgnoreCase(name) || "Cookie2".equalsIgnoreCase(name)
                || "Authorization".equalsIgnoreCase(name)
                || "Proxy-Authorization".equalsIgnoreCase(name);
    }

    static String anonymousConsentCookies(Map<String, List<String>> headers,
                                         URI initial, URI target) {
        if (!initial.getScheme().equalsIgnoreCase(target.getScheme())
                || !initial.getHost().equalsIgnoreCase(target.getHost())
                || (initial.getPort() == -1 ? 443 : initial.getPort())
                != (target.getPort() == -1 ? 443 : target.getPort())) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (!"Cookie".equalsIgnoreCase(header.getKey()) || header.getValue() == null) {
                continue;
            }
            for (String value : header.getValue()) {
                if (value == null) {
                    continue;
                }
                for (String part : value.split(";")) {
                    String cookie = part.trim();
                    int separator = cookie.indexOf('=');
                    if (separator < 0) {
                        continue;
                    }
                    String name = cookie.substring(0, separator);
                    String content = cookie.substring(separator + 1);
                    if (("CONSENT".equals(name) || "SOCS".equals(name))
                            && content.matches("[A-Za-z0-9+._=-]{1,512}")
                            && names.add(name)) {
                        if (result.length() > 0) {
                            result.append("; ");
                        }
                        result.append(name).append('=').append(content);
                    }
                }
            }
        }
        return result.toString();
    }

    private static boolean domain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
