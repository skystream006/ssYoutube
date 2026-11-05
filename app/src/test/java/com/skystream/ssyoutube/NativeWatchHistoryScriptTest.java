package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
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
        assertTrue(script.contains("console.warn('ssYouTube: native history request failed')"));
        assertFalse(script.contains("console.warn(e"));
    }

    @Test
    public void cachesOnlyTheSamePlaybackSessionAndAccount() {
        String script = NativeWatchHistoryScript.SCRIPT;
        assertTrue(script.contains("active.session!==session"));
        assertTrue(script.contains("active.account!==identity"));
        assertTrue(script.contains("if(identity===null){if(active){invalidate();active=null;}return;}"));
        assertTrue(script.contains("if(current&&current!==id&&!outgoing){"
                + "active.urls=null;active.pending=null;return;}"));
        assertTrue(script.contains("account()===context.account"));
        assertTrue(script.contains("yt-navigate-start"));
        assertTrue(script.contains("blocked.has(response)"));
        assertTrue(script.contains("candidates.push(window.ytInitialPlayerResponse);if(initial){"));
        assertTrue(script.contains("context.sent?Promise.resolve()"));
        assertTrue(script.contains("cpn:nonce()"));
        assertFalse(script.contains("setInterval"));
    }

    @Test
    public void retainsBusyReportsAndAllowsOnlyValidatedOutgoingSessionFinals() {
        String script = NativeWatchHistoryScript.SCRIPT;
        assertTrue(script.contains("context.pending=merge(context.pending,report);drain(context)"));
        assertTrue(script.contains("context.busy=false;drain(context)"));
        assertTrue(script.contains("newer.starts=older.starts+','+newer.starts"));
        assertTrue(script.contains("older.starts.length+newer.starts.length<2048"));
        assertTrue(script.contains(
                "outgoing=(finish||(report&&!report.playing))&&active.urls"));
        assertTrue(script.contains("active===context||context.finishing"));
        assertTrue(script.contains("if(!valid(context)){context.pending=null;return;}"));
    }

    @Test
    public void serializesOnlyValidatedNativeIdsAndMeasuredProgress() {
        assertEquals("", NativeWatchHistoryScript.update("');alert(1)//", 1, null, false));
        assertEquals("", NativeWatchHistoryScript.update(null, 1, null, false));
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample("abcdefghijk", 5000, 90000, true, 1, 0, false);
        NativeWatchHistoryState.Report report =
                state.sample("abcdefghijk", 6000, 90000, true, 1, 1000, true);
        String script = NativeWatchHistoryScript.update("abcdefghijk", state.session(), report, false);
        assertTrue(script.contains("position:6.000,duration:90.000"));
        assertTrue(script.contains("starts:'5.000',ends:'6.000',playing:true"));
        assertTrue(script.contains("window.__ssyoutubeWatchHistory('abcdefghijk',1,"));
        assertTrue(NativeWatchHistoryScript.update("abcdefghijk", 1, null, true)
                .contains("'abcdefghijk',1,null,true"));
    }

    @Test
    public void shippedHistoryScriptPassesExecutableFixtures() throws Exception {
        Path script = Files.createTempFile("ssyoutube-history", ".js");
        Path output = Files.createTempFile("ssyoutube-history-tests", ".log");
        Process process = null;
        try {
            Files.write(script, NativeWatchHistoryScript.SCRIPT.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder builder = new ProcessBuilder("node", "--test",
                    "src/test/js/native_watch_history.test.cjs")
                    .redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("SSYOUTUBE_HISTORY_SCRIPT", script.toString());
            try {
                process = builder.start();
            } catch (IOException error) {
                Assume.assumeNoException("Node.js is needed for native-history fixtures", error);
            }
            assertTrue("Native-history fixtures timed out", process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8),
                    0, process.exitValue());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(script);
            Files.deleteIfExists(output);
        }
    }
}
