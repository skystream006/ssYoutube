package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Test;

public class PageElementBlockingScriptTest {
    @Test
    public void shortsThumbnailsRecommendationsAndDistractingStartOff() {
        EnumSet<PageElementBlockingScript.Option> defaults =
                EnumSet.noneOf(PageElementBlockingScript.Option.class);
        for (PageElementBlockingScript.Option option : PageElementBlockingScript.Option.values()) {
            if (option.defaultEnabled) {
                defaults.add(option);
            }
        }
        assertEquals(8, PageElementBlockingScript.Option.values().length);
        assertEquals(EnumSet.of(PageElementBlockingScript.Option.PLAYABLES,
                PageElementBlockingScript.Option.AUTODUBBED,
                PageElementBlockingScript.Option.MEMBERS_ONLY,
                PageElementBlockingScript.Option.END_VIDEO), defaults);
        assertEquals("(function(options){})({\"shorts\":false,\"playables\":true,"
                        + "\"recommended\":false,\"distracting\":false,\"thumbnails\":false,"
                        + "\"autodubbed\":true,\"members_only\":true,\"end_video\":true});",
                PageElementBlockingScript.create("function(options){}", defaults));
    }

    @Test
    public void everyOptionCanBeEnabledOrDisabledWithoutChangingTheOthers() throws Exception {
        String source = "function(options){}";
        assertEquals(source, PageElementBlockingScript.read(new StringReader(source)));
        for (PageElementBlockingScript.Option selected : PageElementBlockingScript.Option.values()) {
            String enabled = PageElementBlockingScript.create(source, EnumSet.of(selected));
            String disabled = PageElementBlockingScript.create(source,
                    EnumSet.complementOf(EnumSet.of(selected)));
            for (PageElementBlockingScript.Option option : PageElementBlockingScript.Option.values()) {
                assertTrue(enabled.contains("\"" + option.key + "\":" + (option == selected)));
                assertTrue(disabled.contains("\"" + option.key + "\":" + (option != selected)));
            }
            assertTrue(selected.preferenceKey.startsWith("block_youtube_"));
        }
    }

    @Test
    public void pageBlockingIsPersistedAndInstalledOutsideTheAdblockAndMpvGates() throws Exception {
        String activity = new String(Files.readAllBytes(
                java.nio.file.Paths.get("src/main/java/com/skystream/ssyoutube/MainActivity.java")),
                StandardCharsets.UTF_8);
        assertTrue(activity.contains("prefs.getBoolean(option.preferenceKey, option.defaultEnabled)"));
        assertTrue(activity.contains("prefs.edit().putBoolean(option.preferenceKey, checked).apply()"));
        String integration = activity.substring(
                activity.indexOf("private void updatePageElementBlocking("),
                activity.indexOf("static boolean isTrustedPlaybackMessage("));
        assertTrue(integration.contains("pageElementScriptHandler.remove()"));
        assertTrue(integration.contains("WebViewCompat.addDocumentStartJavaScript("));
        assertTrue(integration.contains("NativePlaybackScript.ORIGIN_RULES"));
        assertTrue(integration.contains("AdBlockEngine.isSupportedPage(view.getUrl())"));
        assertFalse(integration.contains("mpvEnabled"));
        assertFalse(integration.contains("adBlockingEnabled"));
        assertFalse(integration.contains("reload()"));
        assertFalse(activity.contains("PLAYABLES_CLEANUP_SCRIPT"));
    }

    @Test
    public void bundledScriptPassesExecutableBrowserFixtures() throws Exception {
        Path output = Files.createTempFile("ssyoutube-page-element-tests", ".log");
        Process process = null;
        try {
            try {
                process = new ProcessBuilder("node", "--test",
                        "src/test/js/youtube_page_elements.test.cjs")
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            } catch (IOException error) {
                Assume.assumeNoException("Node.js is needed for page-element fixtures", error);
            }
            assertTrue("Page-element fixtures timed out", process.waitFor(45, TimeUnit.SECONDS));
            String report = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertEquals(report, 0, process.exitValue());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(output);
        }
    }
}
