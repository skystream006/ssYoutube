package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class MpvPlaybackRequestTest {
    private static final String VIDEO = "https://rr1.googlevideo.com/videoplayback?id=video";
    private static final String AUDIO = "https://rr2.googlevideo.com/videoplayback?id=audio";

    @Test
    public void audioLanguageDefaultsToEnglishAndRejectsUnsupportedValues() throws Exception {
        assertEquals("en", new MpvPlaybackRequest(VIDEO, null, 0, "/private/ca.pem")
                .options.get("alang"));
        for (String language : new String[]{null, "", "invalid", "es,en", "en;stop"}) {
            assertEquals("en", new MpvPlaybackRequest(
                    VIDEO, null, 0, "/private/ca.pem", language).options.get("alang"));
        }
    }

    @Test
    public void selectedLanguageAppliesToManifestAndSeparateAudioPlayback() throws Exception {
        String manifest = "https://manifest.googlevideo.com/api/manifest/hls_playlist/index.m3u8";
        MpvPlaybackRequest live = new MpvPlaybackRequest(
                manifest, null, -1, "/private/ca.pem", "es");
        assertEquals("es", live.options.get("alang"));
        assertFalse(live.options.containsKey("start"));
        MpvPlaybackRequest adaptive = new MpvPlaybackRequest(
                VIDEO, AUDIO, 0, "/private/ca.pem", "hi");
        assertEquals("hi", adaptive.options.get("alang"));
        assertArrayEquals(new String[]{"change-list", "audio-files", "append", AUDIO},
                loadCommands(adaptive).get(0));
    }

    @Test
    public void loadsNetworkUrlAsOneArgumentWithoutCommandInterpolation() throws Exception {
        String url = VIDEO + "&parameter=hello%20world,seek=100";
        MpvPlaybackRequest request = new MpvPlaybackRequest(url, null, 0, "/private/ca.pem");
        List<String[]> commands = loadCommands(request);
        assertEquals(1, commands.size());
        assertArrayEquals(new String[]{"loadfile", url, "replace"}, commands.get(0));
        assertFalse(request.options.containsKey("audio-files-append"));
        assertFalse(request.options.containsKey("audio-files"));
    }

    @Test
    public void appendsSeparateAudioBeforeLoadWithoutParsingItAsAList() throws Exception {
        String audio = AUDIO + "&value=a,b:c&escaped=%2C%3A&command=;stop";
        MpvPlaybackRequest request = new MpvPlaybackRequest(
                VIDEO, audio, 12_345, "/private/ca.pem");
        List<String[]> commands = loadCommands(request);
        assertEquals(2, commands.size());
        assertArrayEquals(new String[]{"change-list", "audio-files", "append", audio},
                commands.get(0));
        assertArrayEquals(new String[]{"loadfile", VIDEO, "replace"}, commands.get(1));
        assertFalse(request.options.containsKey("audio-files-append"));
        assertFalse(request.options.containsKey("audio-files"));
        assertEquals("12.345", request.options.get("start"));
    }

    @Test
    public void disablesDiscoveryButAllowsExplicitAudioToBeSelected() throws Exception {
        MpvPlaybackRequest request = new MpvPlaybackRequest(VIDEO, AUDIO, 0, "/private/ca.pem");
        assertFalse(request.options.containsKey("autoload-files"));
        assertEquals("no", request.options.get("sub-auto"));
        assertEquals("no", request.options.get("audio-file-auto"));
        assertEquals("no", request.options.get("cover-art-auto"));
        assertArrayEquals(new String[]{"change-list", "audio-files", "append", AUDIO},
                loadCommands(request).get(0));
    }

    @Test
    public void failedAudioConfigurationDoesNotLoadSilentVideo() throws Exception {
        MpvPlaybackRequest request = new MpvPlaybackRequest(VIDEO, AUDIO, 0, "/private/ca.pem");
        List<String[]> commands = new ArrayList<>();
        assertThrows(IOException.class, () -> request.load(command -> {
            commands.add(command);
            return false;
        }));
        assertEquals(1, commands.size());
        assertEquals("change-list", commands.get(0)[0]);
    }

    @Test
    public void failedLoadIsReportedForBothMuxedAndAdaptiveStreams() throws Exception {
        for (String audio : new String[]{null, AUDIO}) {
            MpvPlaybackRequest request = new MpvPlaybackRequest(VIDEO, audio, 0, "/private/ca.pem");
            List<String[]> commands = new ArrayList<>();
            assertThrows(IOException.class, () -> request.load(command -> {
                commands.add(command);
                return !"loadfile".equals(command[0]);
            }));
            assertEquals(audio == null ? 1 : 2, commands.size());
            assertEquals("loadfile", commands.get(commands.size() - 1)[0]);
        }
    }

    @Test
    public void manifestRequestsDoNotAddExternalAudioOrForceLiveStart() throws Exception {
        for (String manifest : new String[]{
                "https://manifest.googlevideo.com/api/manifest/hls_playlist/index.m3u8",
                "https://manifest.googlevideo.com/api/manifest/dash/index.mpd"}) {
            MpvPlaybackRequest request = new MpvPlaybackRequest(manifest, null, -1, "/private/ca.pem");
            List<String[]> commands = loadCommands(request);
            assertEquals(1, commands.size());
            assertArrayEquals(new String[]{"loadfile", manifest, "replace"}, commands.get(0));
            assertFalse(request.options.containsKey("start"));
        }
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
                Double.POSITIVE_INFINITY, Double.MAX_VALUE, Long.MAX_VALUE / 1000.0}) {
            assertEquals(-1, MpvPlaybackRequest.milliseconds(invalid));
        }
        assertEquals(0, MpvPlaybackRequest.milliseconds(0.0));
    }

    private static List<String[]> loadCommands(MpvPlaybackRequest request) throws IOException {
        List<String[]> commands = new ArrayList<>();
        request.load(command -> {
            commands.add(command);
            return true;
        });
        return commands;
    }
}
