package com.skystream.ssyoutube;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable, bounded rules for advertising subrequests and ordinary cosmetic selectors. */
final class AdBlockEngine {
    private static final int MAX_RULES = 512;
    private static final int MAX_LINE_LENGTH = 2048;
    private final List<Rule> blocks;
    private final List<Rule> exceptions;
    private final List<Rule> cosmetics;

    private AdBlockEngine(List<Rule> blocks, List<Rule> exceptions, List<Rule> cosmetics) {
        this.blocks = Collections.unmodifiableList(blocks);
        this.exceptions = Collections.unmodifiableList(exceptions);
        this.cosmetics = Collections.unmodifiableList(cosmetics);
    }

    static AdBlockEngine read(Reader input) throws IOException {
        List<Rule> blocks = new ArrayList<>();
        List<Rule> exceptions = new ArrayList<>();
        List<Rule> cosmetics = new ArrayList<>();
        BufferedReader reader = new BufferedReader(input);
        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            if (++count > MAX_RULES || line.length() > MAX_LINE_LENGTH) {
                throw new IOException("Adblocking rules exceed limits");
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3 || !parts[1].matches(
                    "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) {
                throw new IOException("Invalid adblocking rule");
            }
            Rule rule = new Rule(parts[1], parts[2]);
            if ("hide".equals(parts[0])) {
                if (rule.value.isEmpty() || rule.value.matches(".*[{};@\\p{Cntrl}].*")) {
                    throw new IOException("Invalid cosmetic selector");
                }
                cosmetics.add(rule);
            } else if (("block".equals(parts[0]) || "allow".equals(parts[0]))
                    && rule.value.startsWith("/") && !rule.value.contains("?")
                    && !rule.value.contains("#")) {
                ("block".equals(parts[0]) ? blocks : exceptions).add(rule);
            } else {
                throw new IOException("Unsupported adblocking rule");
            }
        }
        return new AdBlockEngine(blocks, exceptions, cosmetics);
    }

    boolean shouldBlock(String documentUrl, String requestUrl, boolean mainFrame) {
        if (mainFrame || !isSupportedPage(documentUrl)) {
            return false;
        }
        URI request = webUri(requestUrl);
        if (request == null) {
            return false;
        }
        String host = request.getHost().toLowerCase(Locale.ROOT);
        String path = request.getRawPath().isEmpty() ? "/" : request.getRawPath();
        for (Rule rule : exceptions) {
            if (rule.matches(host, path)) {
                return false;
            }
        }
        for (Rule rule : blocks) {
            if (rule.matches(host, path)) {
                return true;
            }
        }
        return false;
    }

    String cosmeticCss(String documentUrl) {
        if (!isSupportedPage(documentUrl)) {
            return "";
        }
        String host = webUri(documentUrl).getHost().toLowerCase(Locale.ROOT);
        StringBuilder css = new StringBuilder();
        for (Rule rule : cosmetics) {
            if (rule.matchesHost(host)) {
                css.append(rule.value).append("{display:none!important;}\n");
            }
        }
        return css.toString();
    }

    static boolean isSupportedPage(String url) {
        URI uri = webUri(url);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && PlaybackRequest.isYouTubePage(url);
    }

    private static URI webUri(String url) {
        if (url == null || url.length() > 16384) {
            return null;
        }
        try {
            URI uri = new URI(url).parseServerAuthority();
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            return uri;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private static final class Rule {
        final String host;
        final String value;

        Rule(String host, String value) {
            this.host = host;
            this.value = value;
        }

        boolean matchesHost(String candidate) {
            return candidate.equals(host) || candidate.endsWith("." + host);
        }

        boolean matches(String candidate, String path) {
            return matchesHost(candidate) && path.startsWith(value);
        }
    }
}
