package com.skystream.ssyoutube;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decides which network requests made by the YouTube mobile site should be blocked.
 * Kept free of Android dependencies so it can be unit tested on the JVM.
 */
public final class AdBlocker {

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "doubleclick.net",
            "adnxs.com",
            "adsrvr.org",
            "googleadservices.com",
            "googleads.g.doubleclick.net",
            "googlesyndication.com",
            "google-analytics.com",
            "googletagservices.com",
            "googletagmanager.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "static.doubleclick.net",
            "tpc.googlesyndication.com",
            "ads.youtube.com"
    );

    private static final List<String> BLOCKED_PATHS = Arrays.asList(
            "/pagead/",
            "/ads/",
            "/ptracking",
            "/api/stats/ads",
            "/api/stats/qoe",
            "/get_midroll_",
            "/pcs/activeview",
            "/generate_ad",
            "/ad_companion",
            "/log_event?",
            "/youtubei/v1/ads"
    );

    private static final List<String> BLOCKED_QUERY_PARAMETERS = Arrays.asList(
            "ad_format",
            "ad_type",
            "ad_slot",
            "adurl",
            "google_ad_client",
            "google_ad_slot"
    );

    private AdBlocker() {
    }

    /**
     * @param url an absolute request URL
     * @return true when the request is an advertising/tracking request that should be dropped
     */
    public static boolean isAd(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }

        String host = hostOf(lower);
        if (host != null) {
            for (String blocked : BLOCKED_HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
            }
        }

        for (String path : BLOCKED_PATHS) {
            if (lower.contains(path)) {
                return true;
            }
        }
        return hasBlockedQueryParameter(lower);
    }

    private static boolean hasBlockedQueryParameter(String lowerUrl) {
        int queryStart = lowerUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        int fragmentStart = lowerUrl.indexOf('#', queryStart);
        String query = lowerUrl.substring(queryStart + 1,
                fragmentStart < 0 ? lowerUrl.length() : fragmentStart);
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String name = separator < 0 ? parameter : parameter.substring(0, separator);
            if (BLOCKED_QUERY_PARAMETERS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String lowerUrl) {
        int schemeEnd = lowerUrl.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        int start = schemeEnd + 3;
        int end = lowerUrl.length();
        for (int i = start; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = lowerUrl.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }
}
