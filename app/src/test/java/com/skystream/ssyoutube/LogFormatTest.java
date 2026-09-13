package com.skystream.ssyoutube;

import org.junit.Test;

import static org.junit.Assert.*;

public class LogFormatTest {
    @Test
    public void urlDiagnosticsKeepOnlyWebOrigins() {
        assertEquals("https://youtube.com/…", LogFormat.safeUrl(
                "https://" + "user" + ":" + "pass" + "@youtube.com/watch/private-id?v=secret#fragment"));
        assertEquals("(null)", LogFormat.safeUrl(null));
        assertEquals("(redacted URL)", LogFormat.safeUrl("https://[malformed?secret"));
        assertEquals("(redacted URL)", LogFormat.safeUrl("data:text/plain,secret"));
        assertEquals("(redacted URL)", LogFormat.safeUrl("file:///private/secret"));
    }

    @Test
    public void messageRedactionAppliesBeforeTruncationAndRemovesNewlines() {
        String text = LogFormat.sanitize("load https://youtube.com/watch?v=private\nnext");
        assertFalse(text.contains("private"));
        assertFalse(text.contains("\n"));
        assertTrue(text.contains("https://youtube.com/…"));
        assertEquals(4001, LogFormat.sanitize(new String(new char[5000]).replace('\0', 'x')).length());
    }

    @Test
    public void routineEventsIncludeCallerButNoFullTrace() {
        String entry = LogFormat.entry("D", "onResume", null, new StackTraceElement[] {
                new StackTraceElement("com.skystream.ssyoutube.Logger", "log", "Logger.java", 10),
                new StackTraceElement("com.skystream.ssyoutube.MainActivity", "onResume",
                        "MainActivity.java", 20)
        });
        assertTrue(entry.contains("D ssYouTube onResume"));
        assertTrue(entry.contains("MainActivity.onResume(MainActivity.java:20)"));
        assertFalse(entry.contains("    at "));
    }

    @Test
    public void errorsRetainStackFramesWithoutExceptionMessagesOrCausesLeaking() {
        Throwable error = new IllegalStateException("private-token",
                new IllegalArgumentException("private-account"));
        String entry = LogFormat.entry("E", "Failed operation", error,
                new StackTraceElement[0]);
        assertTrue(entry.contains("java.lang.IllegalStateException"));
        assertTrue(entry.contains("java.lang.IllegalArgumentException"));
        assertTrue(entry.contains("    at "));
        assertFalse(entry.contains("private-token"));
        assertFalse(entry.contains("private-account"));
    }
}
