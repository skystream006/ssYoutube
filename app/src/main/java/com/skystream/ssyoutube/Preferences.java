package com.skystream.ssyoutube;

import java.util.Locale;

/**
 * User preference values and the URL/user-agent rules that depend on them. Kept free of
 * Android dependencies so it can be unit tested on the JVM.
 */
public final class Preferences {

    /** Follow the system-wide dark/light setting. */
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    static final String MOBILE_HOME_URL = "https://m.youtube.com/";
    static final String DESKTOP_HOME_URL = "https://www.youtube.com/";

    private Preferences() {
    }

    /**
     * @param desktopMode true when the user asked for the desktop site
     * @return the user agent the WebView should report
     */
    public static String userAgent(boolean desktopMode) {
        return desktopMode ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT;
    }

    /**
     * @param desktopMode true when the user asked for the desktop site
     * @return the YouTube start page matching the mode
     */
    public static String homeUrl(boolean desktopMode) {
        return desktopMode ? DESKTOP_HOME_URL : MOBILE_HOME_URL;
    }

    /**
     * @param url the currently loaded URL
     * @param desktopMode true when the user asked for the desktop site
     * @return the same YouTube page on the host matching the selected site mode
     *         when it is loaded from youtube.com, m.youtube.com or www.youtube.com
     */
    public static String siteModeUrl(String url, boolean desktopMode) {
        if (url == null) {
            return homeUrl(desktopMode);
        }
        String lower = url.toLowerCase(Locale.US);
        boolean isHttps = lower.startsWith("https://");
        boolean isHttp = lower.startsWith("http://");
        if (!isHttps && !isHttp) {
            return homeUrl(desktopMode);
        }
        int authorityStart = lower.indexOf("://") + 3;
        if (authorityStart >= url.length()) {
            return homeUrl(desktopMode);
        }
        int authorityEnd = url.length();
        for (int i = authorityStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        String host = lower.substring(authorityStart, authorityEnd);
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (!host.equals("youtube.com") && !host.equals("m.youtube.com")
                && !host.equals("www.youtube.com")) {
            return url;
        }
        return "https://" + (desktopMode ? "www.youtube.com" : "m.youtube.com")
                + url.substring(authorityEnd);
    }

    /**
     * The floating settings button is only shown on the YouTube home page, i.e. the page
     * that carries the bottom navigation bar.
     *
     * @param url the currently loaded URL
     * @return true when the settings button should be visible
     */
    public static boolean isHomePage(String url) {
        String path = youTubePath(url);
        return path != null && (path.isEmpty() || path.equals("/"));
    }

    /**
     * The related-videos toggle is only useful on a video page, i.e. the watch page that
     * carries the {@code #related} sidebar on the desktop site.
     *
     * @param url the currently loaded URL
     * @return true when the URL is a YouTube watch page
     */
    public static boolean isVideoPage(String url) {
        String path = youTubePath(url);
        return path != null && (path.equals("/watch") || path.startsWith("/watch/"));
    }

    /**
     * @param url the currently loaded URL
     * @return the lower-cased path of {@code url} without its query or fragment, or null
     *         when the URL is not a youtube.com page
     */
    private static String youTubePath(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.US);
        int scheme = lower.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int pathStart = scheme + 3;
        while (pathStart < lower.length()) {
            char c = lower.charAt(pathStart);
            if (c == '/' || c == '?' || c == '#') {
                break;
            }
            pathStart++;
        }
        String host = lower.substring(scheme + 3, pathStart);
        if (!host.equals("youtube.com") && !host.endsWith(".youtube.com")) {
            return null;
        }
        String rest = lower.substring(pathStart);
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        return rest.substring(0, cut);
    }
}
