package com.skystream.ssyoutube;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats diagnostics without persisting URL credentials or browsing identifiers. */
final class LogFormat {
    private static final Pattern URL = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://\\S+");
    private static final int MAX_MESSAGE = 4000;

    private LogFormat() { }

    static String safeUrl(String value) {
        if (value == null) {
            return "(null)";
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host != null && ("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme))) {
                return scheme.toLowerCase(Locale.ROOT) + "://" + host + "/…";
            }
        } catch (Exception ignored) {
            // Malformed and non-web URLs may contain credentials too.
        }
        return "(redacted URL)";
    }

    static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        Matcher matcher = URL.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(safeUrl(matcher.group())));
        }
        matcher.appendTail(result);
        String clean = result.toString().replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ");
        return clean.length() <= MAX_MESSAGE ? clean : clean.substring(0, MAX_MESSAGE) + "…";
    }

    static String entry(String level, String message, Throwable error, StackTraceElement[] caller) {
        StringBuilder text = new StringBuilder(new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
        text.append(' ').append(level).append(" ssYouTube ").append(sanitize(message));
        for (StackTraceElement frame : caller) {
            if (frame.getClassName().startsWith("com.skystream.ssyoutube.")
                    && !frame.getClassName().equals("com.skystream.ssyoutube.Logger")
                    && !frame.getClassName().equals(LogFormat.class.getName())
                    && !frame.getMethodName().equals("logActivity")
                    && !frame.getMethodName().equals("logActivityUrl")) {
                text.append(" [").append(frame).append(']');
                break;
            }
        }
        text.append('\n');
        // Exception messages can contain tokens or page content; retain types and frames only.
        for (int cause = 0; error != null && cause < 4; cause++, error = error.getCause()) {
            text.append(error.getClass().getName()).append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 60); i++) {
                text.append("    at ").append(frames[i]).append('\n');
            }
        }
        return text.toString();
    }
}
