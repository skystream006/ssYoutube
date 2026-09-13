package com.skystream.ssyoutube;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formats diagnostics without persisting URL credentials or browsing identifiers. */
final class LogFormat {
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
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (start < message.length() && result.length() <= MAX_MESSAGE) {
            char character = message.charAt(start);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                if (result.length() == 0 || result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                start++;
                continue;
            }
            int end = start + 1;
            while (end < message.length() && !Character.isWhitespace(message.charAt(end))
                    && !Character.isISOControl(message.charAt(end))) {
                end++;
            }
            // Scan tokens once instead of regex backtracking over untrusted URL-like text.
            String token = message.substring(start, end);
            String clean = token.contains("://") ? safeUrl(token) : token;
            int remaining = MAX_MESSAGE + 1 - result.length();
            result.append(clean, 0, Math.min(clean.length(), remaining));
            start = end;
        }
        String clean = result.toString();
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
