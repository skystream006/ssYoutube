package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdBlockerTest {

    @Test
    public void blocksKnownAdHosts() {
        assertTrue(AdBlocker.isAd("https://pagead2.googlesyndication.com/pagead/js/ads.js"));
        assertTrue(AdBlocker.isAd("https://static.doubleclick.net/instream/ad_status.js"));
        assertTrue(AdBlocker.isAd("https://www.google-analytics.com/collect"));
        assertTrue(AdBlocker.isAd("https://securepubads.g.doubleclick.net/tag/js/gpt.js"));
        assertTrue(AdBlocker.isAd("https://ib.adnxs.com/getuid"));
    }

    @Test
    public void blocksAdPaths() {
        assertTrue(AdBlocker.isAd("https://m.youtube.com/pagead/interaction/"));
        assertTrue(AdBlocker.isAd("https://m.youtube.com/api/stats/ads?foo=1"));
        assertTrue(AdBlocker.isAd("https://m.youtube.com/ptracking?video_id=1"));
        assertTrue(AdBlocker.isAd("https://m.youtube.com/youtubei/v1/ads?key=abc"));
    }

    @Test
    public void blocksKnownAdQueryParameters() {
        assertTrue(AdBlocker.isAd("https://m.youtube.com/watch?v=abc&ad_format=preroll"));
        assertTrue(AdBlocker.isAd("https://example.com/request?google_ad_client=ca-pub-123"));
    }

    @Test
    public void allowsRegularYouTubeTraffic() {
        assertFalse(AdBlocker.isAd("https://m.youtube.com/"));
        assertFalse(AdBlocker.isAd("https://m.youtube.com/watch?v=abc"));
        assertFalse(AdBlocker.isAd("https://i.ytimg.com/vi/abc/hq.jpg"));
        assertFalse(AdBlocker.isAd("https://accounts.google.com/ServiceLogin"));
        assertFalse(AdBlocker.isAd("https://m.youtube.com/watch?ad_formats=available"));
        assertFalse(AdBlocker.isAd("https://m.youtube.com/api/stats/playback?docid=abc"));
        assertFalse(AdBlocker.isAd("https://m.youtube.com/api/stats/watchtime?docid=abc&cmt=42"));
    }

    @Test
    public void doesNotMatchLookalikeHosts() {
        assertFalse(AdBlocker.isAd("https://notdoubleclick.net/x"));
        assertFalse(AdBlocker.isAd("https://m.youtube.com/watch?u=https://doubleclick.net"));
    }

    @Test
    public void ignoresNonHttpUrls() {
        assertFalse(AdBlocker.isAd(null));
        assertFalse(AdBlocker.isAd(""));
        assertFalse(AdBlocker.isAd("data:text/html,hello"));
    }
}
