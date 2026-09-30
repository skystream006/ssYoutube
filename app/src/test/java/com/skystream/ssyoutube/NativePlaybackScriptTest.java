package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativePlaybackScriptTest {
    @Test
    public void usesOnlyExplicitYouTubeOrigins() {
        assertEquals(4, NativePlaybackScript.ORIGIN_RULES.size());
        assertTrue(NativePlaybackScript.ORIGIN_RULES.contains("https://m.youtube.com"));
        assertTrue(NativePlaybackScript.ORIGIN_RULES.contains("https://www.youtube.com"));
        assertFalse(NativePlaybackScript.ORIGIN_RULES.contains("*"));
        assertFalse(NativePlaybackScript.ORIGIN_RULES.contains("https://accounts.google.com"));
        assertTrue(NativePlaybackScript.SCRIPT.contains("location.protocol!=='https:'"));
    }

    @Test
    public void disablesAllWebMediaInsteadOfFilteringAdsOrPlayerResponses() {
        String script = NativePlaybackScript.SCRIPT;
        assertTrue(script.contains("proto.play=function(){silence(this);return Promise.resolve();}"));
        assertTrue(script.contains("querySelectorAll('video,audio')"));
        assertTrue(script.contains("media.muted=true"));
        assertTrue(script.contains("media.pause()"));
        assertTrue(script.contains("removeAttribute('autoplay')"));
        assertFalse(script.contains("JSON.parse="));
        assertFalse(script.contains("Response.prototype"));
        assertFalse(script.contains("playabilityStatus"));
        assertFalse(script.contains("adPlacements"));
        assertFalse(script.contains("fetch="));
    }

    @Test
    public void routesMainFrameLocationChangesWithoutTrustingClickedLinks() {
        String script = NativePlaybackScript.SCRIPT;
        assertTrue(script.contains("window.top===window"));
        assertTrue(script.contains("location.href!==lastUrl"));
        assertTrue(script.contains("ssYouTubePlayback.postMessage(location.href)"));
        assertTrue(script.contains("['pushState','replaceState']"));
        assertTrue(script.contains("original.apply(this,arguments)"));
        assertTrue(script.contains("popstate"));
        assertTrue(script.contains("hashchange"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertFalse(script.contains("addJavascriptInterface"));
    }

    @Test
    public void installationIsIdempotentAndSupportsEarlyAndLateDocuments() {
        String script = NativePlaybackScript.SCRIPT;
        assertTrue(script.contains("if(window.__ssyoutubeNativePlayback)"));
        assertTrue(script.contains("document.head||document.documentElement"));
        assertTrue(script.contains("DOMContentLoaded"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("if(pending){return;}pending=true"));
        assertTrue(script.contains("setInterval(update,1000)"));
    }

    @Test
    public void collapsesPlayerPlaceholdersWithoutRemovingWatchContent() {
        String script = NativePlaybackScript.SCRIPT;
        assertTrue(script.contains("ytm-watch .player-size,ytm-watch .player-placeholder"));
        assertTrue(script.contains("ytd-watch-flexy #full-bleed-container"));
        assertTrue(script.contains("#player-wide-container"));
        assertTrue(script.contains("height:0!important;min-height:0!important;"));
        assertTrue(script.contains("padding:0!important;margin:0!important;"));
        assertTrue(script.contains("--ytd-watch-flexy-player-height:0px!important;"));
        assertTrue(script.contains("ytm-watch .watch-below-the-player"));
        assertFalse(script.contains("ytm-watch{display:none"));
        assertFalse(script.contains("#primary{display:none"));
        assertFalse(script.contains("#comments"));
    }
}
