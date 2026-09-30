package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Locale;

import org.junit.Test;

public class MpvPlaybackRequestTest {
    private static final String VIDEO = "https://rr1.googlevideo.com/videoplayback?id=video";
    private static final String AUDIO = "https://rr2.googlevideo.com/videoplayback?id=audio";

    @Test
    public void loadsNetworkUrlAsOneArgumentWithoutCommandInterpolation() throws Exception {
        String url = VIDEO + "&parameter=hello%20world,seek=100";
        MpvPlaybackRequest request = new MpvPlaybackRequest(url, null, 0, "/private/ca.pem");
        assertArrayEquals(new String[]{"loadfile", url, "replace"}, request.loadCommand());
        assertFalse(request.options.containsKey("audio-files-append"));
    }

    @Test
    public void addsSeparateAudioWithoutParsingItAsAList() throws Exception {
        MpvPlaybackRequest request = new MpvPlaybackRequest(
                VIDEO, AUDIO + "&value=a,b:c", 12_345, "/private/ca.pem");
        assertEquals(AUDIO + "&value=a,b:c", request.options.get("audio-files-append"));
        assertEquals("12.345", request.options.get("start"));
    }

    @Test
    public void liveDefaultDoesNotForceTheStartOfTheDvrWindow() throws Exception {
        MpvPlaybackRequest request = new MpvPlaybackRequest(VIDEO, null, -1, "/private/ca.pem");
        assertFalse(request.options.containsKey("start"));
        assertEquals("0.000", new MpvPlaybackRequest(VIDEO, null, 0, "/private/ca.pem")
                .options.get("start"));
    }

    @Test
    public void playbackIsAnonymousVerifiedHttpsWithoutScriptsOrUrlLogging() throws Exception {
        MpvPlaybackRequest request = new MpvPlaybackRequest(VIDEO, AUDIO, 0, "/private/ca.pem");
        assertEquals("no", request.options.get("cookies"));
        assertEquals("", request.options.get("http-header-fields"));
        assertEquals("yes", request.options.get("tls-verify"));
        assertEquals("/private/ca.pem", request.options.get("tls-ca-file"));
        assertEquals("all=no", request.options.get("msg-level"));
        assertEquals("no", request.options.get("terminal"));
        assertEquals("no", request.options.get("config"));
        assertEquals("no", request.options.get("load-scripts"));
        assertEquals("no", request.options.get("ytdl"));
        assertEquals("lavf", request.options.get("demuxer"));
        assertEquals("protocol_whitelist=%20%https,tls,tcp,crypto",
                request.options.get("demuxer-lavf-o"));
        assertEquals(request.options.get("demuxer-lavf-o"), request.options.get("stream-lavf-o"));
    }

    @Test
    public void rejectsUnsafeVideoAndAudioInputs() {
        for (String unsafe : new String[]{"file:///private/token", "http://rr1.googlevideo.com/a",
                "https://attacker.example/video", "https://" + "viewer@" + "rr1.googlevideo.com/a",
                "https://rr1.googlevideo.com:8443/a", "https://rr1.googlevideo.com/a\nstop"}) {
            assertThrows(IOException.class, () ->
                    new MpvPlaybackRequest(unsafe, null, 0, "/private/ca.pem"));
            assertThrows(IOException.class, () ->
                    new MpvPlaybackRequest(VIDEO, unsafe, 0, "/private/ca.pem"));
        }
    }

    @Test
    public void positionsAreFiniteAndLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("12.345", MpvPlaybackRequest.seconds(12_345));
            assertEquals("0.000", MpvPlaybackRequest.seconds(-1));
        } finally {
            Locale.setDefault(previous);
        }
        assertEquals(12_345, MpvPlaybackRequest.milliseconds(12.345));
        for (Double invalid : new Double[]{null, -1.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            assertEquals(-1, MpvPlaybackRequest.milliseconds(invalid));
        }
        assertEquals(0, MpvPlaybackRequest.milliseconds(0.0));
    }
}
