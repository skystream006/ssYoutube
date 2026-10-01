package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackRequestTest {

    private static final String ID = "dQw4w9WgXcQ";
    private static final String WATCH = "https://www.youtube.com/watch?v=" + ID;

    @Test
    public void recognizesExactHostsAndNormalizesWatchUrls() {
        for (String host : new String[]{
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"
        }) {
            assertRequest("https://" + host + "/watch?v=" + ID, 0);
            assertRequest("http://" + host + "/watch?v=" + ID, 0);
            assertRequest("https://" + host + ":443/watch?v=" + ID, 0);
            assertRequest("http://" + host + ":80/watch?v=" + ID, 0);
        }
        assertRequest("HTTPS://WWW.YOUTUBE.COM/watch?v=" + ID, 0);
        assertRequest(WATCH + "&list=PL_example&index=2&feature=share", 0);
    }

    @Test
    public void acceptsDirectVideoPathsAndShortlinks() {
        for (String path : new String[]{"live", "embed"}) {
            assertRequest("https://m.youtube.com/" + path + "/" + ID, 0);
            assertRequest("https://www.youtube.com/" + path + "/" + ID + "/", 0);
        }
        for (String host : new String[]{"youtu.be", "www.youtu.be", "YOUTU.BE"}) {
            assertRequest("https://" + host + "/" + ID + "?si=sharing", 0);
            assertRequest("http://" + host + ":80/" + ID + "/", 0);
        }
    }

    @Test
    public void leavesShortsInTheWebView() {
        for (String host : new String[]{
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"
        }) {
            for (String path : new String[]{
                    "/shorts", "/shorts/", "/shorts/" + ID, "/shorts/" + ID + "/",
                    "/shorts/" + ID + "?t=10&feature=share", "/shorts/" + ID + "#t=10"
            }) {
                String url = "https://" + host + path;
                assertTrue(url, PlaybackRequest.isShortsPage(url));
                assertNull(url, PlaybackRequest.fromUrl(url));
            }
        }
        assertTrue(PlaybackRequest.isShortsPage("HTTPS://WWW.YOUTUBE.COM:443/shorts/" + ID));
        assertTrue(PlaybackRequest.isShortsPage("http://m.youtube.com:80/shorts/" + ID));
    }

    @Test
    public void shortsPredicateRequiresATrustedOriginAndShortsPath() {
        for (String url : new String[]{
                null, "", "/shorts/" + ID, "https://youtube.com",
                "https://youtube.com/shorts-other/" + ID,
                "https://youtube.com/watch?v=" + ID + "&next=/shorts/" + ID,
                "https://youtu.be/" + ID, "https://youtu.be/shorts/" + ID,
                "https://youtube.com.evil.example/shorts/" + ID,
                "https://evil.example@youtube.com/shorts/" + ID,
                "https://youtube.com:444/shorts/" + ID,
                "https://accounts.google.com/shorts/" + ID,
                "file://youtube.com/shorts/" + ID
        }) {
            assertFalse(url, PlaybackRequest.isShortsPage(url));
        }
        assertRequest(WATCH, 0);
    }

    @Test
    public void decodesQueryComponentsExactlyOnce() {
        assertRequest("https://youtube.com/watch?%76=%64Qw4w9WgXcQ&%74=1%6D2s", 62000);
        assertRequest("https://youtube.com/watch?feature=a%26v%3Devil&v=" + ID, 0);
        assertRequest(WATCH + "&description=two+words", 0);
        PlaybackRequest request =
                PlaybackRequest.fromUrl("https://youtube.com/watch?v=AbC%5Fdef%2D123");
        assertNotNull(request);
        assertEquals("AbC_def-123", request.videoId);
    }

    @Test
    public void rejectsUntrustedAndAmbiguousOrigins() {
        for (String origin : new String[]{
                "https://youtube.com.evil.example",
                "https://www.youtube.com.evil.example",
                "https://notyoutube.com",
                "https://evil-youtube.com",
                "https://evil.youtube.com",
                "https://www.youtu.be.evil.example",
                "https://youtube.com.",
                "https://youtube%2ecom",
                "https://youtub\u0435.com",
                "https://accounts.google.com",
                "https://youtube-nocookie.com",
                "https://youtube.com@evil.example",
                "https://evil.example@youtube.com",
                "https://viewer%3Aplaceholder@youtube.com",
                "https://@youtube.com",
                "https://youtube.com:80",
                "http://youtube.com:443",
                "https://youtube.com:8443",
                "https://youtube.com:-1",
                "https://youtube.com:",
                "https://youtube.com:999999999999",
                "ftp://youtube.com",
                "javascript://youtube.com",
                "file://youtube.com",
                "intent://youtube.com",
                "//youtube.com"
        }) {
            String url = origin + "/watch?v=" + ID;
            assertNull(url, PlaybackRequest.fromUrl(url));
            assertFalse(url, PlaybackRequest.isYouTubePage(url));
        }
    }

    @Test
    public void rejectsMalformedAndRelativeUrls() {
        for (String url : new String[]{
                null, "", " ", "/watch?v=" + ID, "youtube.com/watch?v=" + ID,
                "https:youtube.com/watch?v=" + ID,
                "https:///youtube.com/watch?v=" + ID,
                " https://youtube.com/watch?v=" + ID,
                WATCH + " ", WATCH + "\n", WATCH + "&bad=%",
                "https://youtube.com\\@evil.example/watch?v=" + ID,
                "https://youtube.com/watch?v=%GG"
        }) {
            assertNull(url, PlaybackRequest.fromUrl(url));
            assertFalse(url, PlaybackRequest.isYouTubePage(url));
        }
    }

    @Test
    public void doesNotExtractVideosFromRedirectsOrOtherPageTypes() {
        for (String url : new String[]{
                "https://youtube.com",
                "https://youtube.com/",
                "https://youtube.com/results?search_query=" + ID,
                "https://youtube.com/playlist?list=" + ID,
                "https://youtube.com/@channel",
                "https://youtube.com/redirect?q=" + WATCH,
                "https://youtube.com/attribution_link?u=%2Fwatch%3Fv%3D" + ID,
                "https://evil.example/?url=" + WATCH,
                "https://youtube.com/?v=" + ID,
                "https://youtube.com/watch#v=" + ID,
                "https://youtube.com/watch/extra?v=" + ID,
                "https://youtube.com//watch?v=" + ID,
                "https://youtube.com/%77atch?v=" + ID,
                "https://youtube.com/shorts/" + ID + "/extra",
                "https://youtube.com/live/../" + ID,
                "https://youtube.com/embed/videoseries?list=" + ID,
                "https://youtu.be/watch?v=" + ID,
                "https://youtu.be/" + ID + "/extra",
                "https://youtu.be/?v=" + ID
        }) {
            assertNull(url, PlaybackRequest.fromUrl(url));
        }
    }

    @Test
    public void rejectsPlaylistEmbedAsVideo() {
        assertNull(PlaybackRequest.fromUrl("https://youtube.com/embed/videoseries"));
        assertNull(PlaybackRequest.fromUrl(
                "https://youtube.com/embed/videoseries?list=" + ID));
        assertNull(PlaybackRequest.fromUrl(
                "https://youtube.com/embed/videoseries/?v=" + ID));
        assertRequest("https://youtube.com/embed/" + ID, 0);
    }

    @Test
    public void rejectsMissingMalformedAndInjectedVideoIds() {
        for (String value : new String[]{
                "", "short", ID + "x", ID.substring(1), "abc.def-123",
                "abcdefghij+", "abcdefghij%20", "abcdefghij%2F",
                "abcdefghij%00", "abcdefghij%0A", "abcdefghij%C3%A9",
                "abcdefghij%FF", "%2564Qw4w9WgXcQ",
                ID + "%26v%3Devil", ID + "%3Fstart%3D1",
                "%3Cscript%3E", ID + ";v=other"
        }) {
            assertNull(value, PlaybackRequest.fromUrl("https://youtube.com/watch?v=" + value));
        }
        assertNull(PlaybackRequest.fromUrl("https://youtube.com/watch"));
        assertNull(PlaybackRequest.fromUrl("https://youtube.com/watch?v"));
        assertNull(PlaybackRequest.fromUrl("https://youtube.com/watch?V=" + ID));
        assertNull(PlaybackRequest.fromUrl("https://youtube.com/shorts/%64Qw4w9WgXcQ"));
        assertNull(PlaybackRequest.fromUrl(WATCH + "&v=" + ID));
        assertNull(PlaybackRequest.fromUrl(WATCH + "&%76=other"));
        assertNull(PlaybackRequest.fromUrl(WATCH + "&t=1&t=2"));
        assertNull(PlaybackRequest.fromUrl(WATCH + "&start=1&start=2"));
    }

    @Test
    public void parsesSecondsAndOrderedTimeUnits() {
        assertRequest(WATCH + "&t=90", 90000);
        assertRequest(WATCH + "&t=00090", 90000);
        assertRequest(WATCH + "&t=90s", 90000);
        assertRequest(WATCH + "&t=2m", 120000);
        assertRequest(WATCH + "&t=1h", 3600000);
        assertRequest(WATCH + "&t=1h2m3s", 3723000);
        assertRequest(WATCH + "&t=1h3s", 3603000);
        assertRequest(WATCH + "&t=1m90s", 150000);
        assertRequest(WATCH + "&start=32", 32000);
        assertRequest("https://youtu.be/" + ID + "?t=1m2s", 62000);
        assertRequest("https://youtube.com/embed/" + ID + "?start=30", 30000);
        assertRequest("https://youtube.com/live/" + ID + "?t=20", 20000);
    }

    @Test
    public void parsesFragmentTimesAndUsesDocumentedPrecedence() {
        assertRequest(WATCH + "#t=1h2m3s", 3723000);
        assertRequest(WATCH + "#1m", 60000);
        assertRequest(WATCH + "#90", 90000);
        assertRequest(WATCH + "#t=1%6D", 60000);
        assertRequest(WATCH + "&t=12&start=34#t=56", 12000);
        assertRequest(WATCH + "&start=34#1m", 34000);
        assertRequest(WATCH + "&t=bad&start=34#1m", 0);
        assertRequest(WATCH + "#chapter", 0);
        assertRequest(WATCH + "#t=1m&v=other", 0);
    }

    @Test
    public void ignoresMalformedNegativeAndOverflowingTimes() {
        for (String value : new String[]{
                "", "-1", "%2B1", "1.5", "NaN", "Infinity", "1e3",
                "%201", "1%20", "1H", "1m2h", "1m2m", "h", "1h2", "1%00",
                "604801", "169h", "168h1s", "10081m",
                "9223372036854775807", "9223372036854775808",
                "9999999999999999999999999999999999999999",
                "9223372036854775807h", "999999999999999999999999m"
        }) {
            assertRequest(WATCH + "&t=" + value, 0);
            assertRequest(WATCH + "&start=" + value, 0);
            assertRequest(WATCH + "#t=" + value, 0);
        }
        assertRequest(WATCH + "&start=1m", 0);
    }

    @Test
    public void acceptsSevenDayBoundaryWithoutOverflow() {
        long sevenDaysMs = 604800000L;
        assertRequest(WATCH + "&t=604800", sevenDaysMs);
        assertRequest(WATCH + "&t=168h", sevenDaysMs);
        assertRequest(WATCH + "&t=10080m", sevenDaysMs);
        assertRequest(WATCH + "&t=167h59m59s", sevenDaysMs - 1000);
        assertRequest(WATCH + "&start=604800", sevenDaysMs);
        assertRequest(WATCH + "#t=168h", sevenDaysMs);
    }

    @Test
    public void browserPredicateChecksOriginAndExcludesShortlinks() {
        for (String host : new String[]{
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"
        }) {
            assertTrue(PlaybackRequest.isYouTubePage("https://" + host));
            assertTrue(PlaybackRequest.isYouTubePage("http://" + host + ":80/"));
            assertTrue(PlaybackRequest.isYouTubePage("https://" + host + ":443/results?q=music"));
        }
        assertTrue(PlaybackRequest.isYouTubePage("HTTPS://WWW.YOUTUBE.COM/playlist?list=PL123"));
        assertTrue(PlaybackRequest.isYouTubePage("https://youtube.com/watch?v=invalid"));
        assertFalse(PlaybackRequest.isYouTubePage("https://youtu.be/" + ID));
        assertFalse(PlaybackRequest.isYouTubePage("https://www.youtu.be/" + ID));
    }

    private static void assertRequest(String url, long positionMs) {
        PlaybackRequest request = PlaybackRequest.fromUrl(url);
        assertNotNull(url, request);
        assertEquals(url, ID, request.videoId);
        assertEquals(url, positionMs, request.startPositionMs);
        assertEquals(url, WATCH, request.watchUrl());
    }
}
