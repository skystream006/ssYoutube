package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Test;

public class NativeWatchHistoryScriptTest {
    @Test
    public void metadataAndTrackingRequestsStayInsideTrustedYouTubeDocuments() {
        String script = NativeWatchHistoryScript.SCRIPT;
        assertTrue(script.contains("window.top===window&&location.protocol==='https:'"));
        assertTrue(script.contains("response.videoDetails.videoId!==id"));
        assertTrue(script.contains("if(pageId()!==id){return null;}"));
        assertTrue(script.contains("url.username||url.password||url.hash"));
        assertTrue(script.contains("url.pathname!==path"));
        assertTrue(script.contains("value!==id"));
        assertTrue(script.contains("'/api/stats/playback'"));
        assertTrue(script.contains("'/api/stats/watchtime'"));
        assertTrue(script.contains("redirect:'error'"));
        assertTrue(script.contains("controller.abort()"));
        assertFalse(script.contains("document.cookie"));
        assertFalse(script.contains("postMessage"));
        assertFalse(script.contains("localStorage"));
        assertFalse(script.contains("console."));
    }

    @Test
    public void cachesOnlyTheSamePlaybackSessionAndAccount() {
        String script = NativeWatchHistoryScript.SCRIPT;
        assertTrue(script.contains("active.session!==session"));
        assertTrue(script.contains("active.account!==identity"));
        assertTrue(script.contains("if(!current&&identity===null){active.urls=null;return;}"));
        assertTrue(script.contains("if(current&&current!==id){active.urls=null;return;}"));
        assertTrue(script.contains("active!==context||account()!==context.account"));
        assertTrue(script.contains("yt-navigate-start"));
        assertTrue(script.contains("response===blocked"));
        assertTrue(script.contains("if(initial){candidates.push(window.ytInitialPlayerResponse)"));
        assertTrue(script.contains("context.sent?Promise.resolve()"));
        assertTrue(script.contains("cpn:nonce()"));
        assertFalse(script.contains("setInterval"));
    }

    @Test
    public void serializesOnlyValidatedNativeIdsAndMeasuredProgress() {
        assertEquals("", NativeWatchHistoryScript.update("');alert(1)//", 1, null));
        assertEquals("", NativeWatchHistoryScript.update(null, 1, null));
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample("abcdefghijk", 5000, 90000, true, 1, 0, false);
        NativeWatchHistoryState.Report report =
                state.sample("abcdefghijk", 6000, 90000, true, 1, 1000, true);
        String script = NativeWatchHistoryScript.update("abcdefghijk", state.session(), report);
        assertTrue(script.contains("position:6.000,duration:90.000"));
        assertTrue(script.contains("starts:'5.000',ends:'6.000',playing:true"));
        assertTrue(script.contains("window.__ssyoutubeWatchHistory('abcdefghijk',1,"));
        assertTrue(NativeWatchHistoryScript.update("abcdefghijk", 1, null)
                .contains("'abcdefghijk',1,null"));
    }
}
