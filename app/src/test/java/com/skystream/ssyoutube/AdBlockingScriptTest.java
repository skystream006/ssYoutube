package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import org.junit.Test;

public class AdBlockingScriptTest {
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
