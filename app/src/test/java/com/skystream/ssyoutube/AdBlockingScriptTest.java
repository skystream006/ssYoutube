package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class AdBlockingScriptTest {
    @Test
    public void videoPatchModeIsPassedLocallyWithoutAGlobalSetting() {
        String source = "function (patched) { return patched; }";
        assertEquals("(" + source + ")(false);", AdBlockingScript.createVideo(source, false));
        assertEquals("(" + source + ")(true);", AdBlockingScript.createVideo(source, true));
    }

    @Test
    public void patchChoiceIsSavedAndChangesReloadThePage() throws Exception {
        String activity = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssyoutube/MainActivity.java")), StandardCharsets.UTF_8);
        assertTrue(activity.contains("prefs.getBoolean(KEY_PATCHED_ADBLOCKING_ENABLED,"));
        assertTrue(activity.contains("prefs.edit().putBoolean(KEY_PATCHED_ADBLOCKING_ENABLED, enabled).apply()"));
        int start = activity.indexOf("private void setPatchedAdBlockingEnabled(");
        String setter = activity.substring(start, activity.indexOf("\n    }", start));
        assertTrue(setter.contains("mpvEnabled || !adBlockingEnabled"));
        assertTrue(setter.contains("updateAdBlocking(webView);"));
        assertTrue(setter.contains("webView.reload();"));
        assertTrue(activity.contains("patchedAdBlockingToggle.setChecked(patchedAdBlockingEnabled)"));
    }

    @Test
    public void onlyTrustedOriginsGetCosmeticRules() throws Exception {
        String script = AdBlockingScript.create(AdBlockEngineTest.bundled());
        assertTrue(script.contains("switch(location.origin)"));
        for (String origin : NativePlaybackScript.ORIGIN_RULES) {
            assertTrue(script.contains("case \"" + origin + "\":css="));
        }
        assertTrue(script.contains("default:return;"));
        assertFalse(script.contains("accounts.google.com"));
    }

    @Test
    public void repairsTheStylesheetIdempotentlyAfterNavigationAndDomChanges() throws Exception {
        String script = AdBlockingScript.create(AdBlockEngineTest.bundled());
        assertTrue(script.contains("if(window.__ssyoutubeAdBlocking)"));
        assertTrue(script.contains("getElementById('ssyoutube-adblocking')"));
        assertTrue(script.contains("if(style.textContent!==css)"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("DOMContentLoaded"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertFalse(script.contains("innerHTML"));
        assertFalse(script.contains("fetch("));
        assertFalse(script.contains("JSON.parse"));
        assertFalse(script.contains("HTMLMediaElement"));
        assertFalse(script.contains("cookie"));
        assertFalse(script.contains("localStorage"));
    }

    @Test
    public void quotesSelectorDataRatherThanExecutingIt() throws Exception {
        AdBlockEngine engine = AdBlockEngine.read(new StringReader(
                "hide|youtube.com|[data-ad=\"banner\"] .ad\\:slot"));
        String script = AdBlockingScript.create(engine);
        assertTrue(script.contains("[data-ad=\\\"banner\\\"] .ad\\\\:slot"));
        assertTrue(script.contains("\\u000a"));
    }
}
