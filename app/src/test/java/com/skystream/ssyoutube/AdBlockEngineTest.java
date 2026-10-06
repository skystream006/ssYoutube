package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class AdBlockEngineTest {
    private static final String PAGE = "https://m.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String AD = "https://ad.doubleclick.net/advertisement";

    static AdBlockEngine bundled() throws IOException {
        try (Reader reader = Files.newBufferedReader(
                Paths.get("src/main/res/raw/adblocking_rules.txt"), StandardCharsets.UTF_8)) {
            return AdBlockEngine.read(reader);
        }
    }

    @Test
    public void bundledRulesBlockAdvertisingDomainsAndFirstPartyAdPaths() throws Exception {
        AdBlockEngine engine = bundled();
        for (String url : new String[] {AD, "https://DOUBLECLICK.NET/",
                "https://pagead2.googlesyndication.com/pagead/js/ads.js",
                "https://www.googleadservices.com/pagead/",
                "https://m.youtube.com/pagead/adview?id=123",
                "https://www.youtube.com/api/stats/ads?ver=2"}) {
            assertTrue(url, engine.shouldBlock(PAGE, url, false));
        }
    }

    @Test
    public void preservesPlaybackSignInHistoryThumbnailsCommentsAndTheLogo() throws Exception {
        AdBlockEngine engine = bundled();
        for (String url : new String[] {
                "https://rr1---sn.example.googlevideo.com/videoplayback?x=1",
                "https://www.youtube.com/youtubei/v1/player",
                "https://m.youtube.com/youtubei/v1/next",
                "https://www.youtube.com/api/stats/watchtime",
                "https://www.youtube.com/api/stats/playback",
                "https://accounts.google.com/ServiceLogin",
                "https://accounts.youtube.com/accounts/SetSID",
                "https://www.google.com/recaptcha/api.js",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                "https://m.youtube.com/ssyoutube-app-logo.png",
                "https://m.youtube.com/s/player/base.js"}) {
            assertFalse(url, engine.shouldBlock(PAGE, url, false));
        }
    }

    @Test
    public void matchesParsedHostsAndPathsNotArbitraryUrlSubstrings() throws Exception {
        AdBlockEngine engine = bundled();
        for (String url : new String[] {
                "https://notdoubleclick.net/ad",
                "https://doubleclick.net.example.com/ad",
                "https://example.com/doubleclick.net/ad",
                "https://example.com/?url=https://doubleclick.net",
                "https://www.youtube.com/watch?next=/pagead/",
                "https://www.youtube.com/pageads/normal",
                "https://www.youtube.com/PAGEAD/normal",
                "https://doubleclick.net@example.com/ad",
                "https://doubleclick.net./ad"}) {
            assertFalse(url, engine.shouldBlock(PAGE, url, false));
        }
    }

    @Test
    public void scopesFilteringToHttpsYouTubeDocumentsIncludingShorts() throws Exception {
        AdBlockEngine engine = bundled();
        for (String origin : NativePlaybackScript.ORIGIN_RULES) {
            assertTrue(engine.shouldBlock(origin + "/shorts/dQw4w9WgXcQ", AD, false));
            assertTrue(engine.shouldBlock(origin + "/", AD, false));
        }
        for (String page : new String[] {null, "", "https://accounts.google.com/",
                "https://accounts.youtube.com/", "https://consent.youtube.com/",
                "https://youtube.com.example.org/", "https://youtu.be/dQw4w9WgXcQ",
                "http://m.youtube.com/", "https://m.youtube.com:444/",
                "https://user@m.youtube.com/", "about:blank"}) {
            assertFalse(engine.shouldBlock(page, AD, false));
            assertEquals("", engine.cosmeticCss(page));
        }
    }

    @Test
    public void neverFiltersMainFrameNavigation() throws Exception {
        assertFalse(bundled().shouldBlock(PAGE, AD, true));
        assertFalse(bundled().shouldBlock(PAGE, "https://m.youtube.com/pagead/", true));
    }

    @Test
    public void ignoresInvalidAndNonWebRequests() throws Exception {
        AdBlockEngine engine = bundled();
        for (String url : new String[] {null, "", "https://", "not a URL",
                "https://doubleclick.net/%xx", "https://user@doubleclick.net/",
                "blob:https://doubleclick.net/123", "data:text/plain,ad",
                "file:///doubleclick.net/", "javascript:alert(1)"}) {
            assertFalse(engine.shouldBlock(PAGE, url, false));
        }
    }

    @Test
    public void exceptionsTakePriorityRegardlessOfRuleOrder() throws Exception {
        for (String rules : new String[] {
                "block|ads.example.com|/\nallow|ads.example.com|/content/",
                "allow|ads.example.com|/content/\nblock|ads.example.com|/"}) {
            AdBlockEngine engine = AdBlockEngine.read(new StringReader(rules));
            assertTrue(engine.shouldBlock(PAGE, "https://ads.example.com/ad", false));
            assertFalse(engine.shouldBlock(PAGE, "https://sub.ads.example.com/content/video", false));
            assertTrue(engine.shouldBlock(PAGE, "https://ads.example.com/content-ad/", false));
        }
    }

    @Test
    public void cosmeticsAreScopedAndDoNotHideThePlayerOrSkipControls() throws Exception {
        AdBlockEngine engine = bundled();
        String css = engine.cosmeticCss(PAGE);
        assertTrue(css.contains("ytd-ad-slot-renderer{display:none!important;}"));
        assertTrue(css.contains("ytm-ad-slot-renderer{display:none!important;}"));
        assertFalse(css.contains("#movie_player"));
        assertFalse(css.contains(".ytp-ad-skip"));
        assertFalse(css.contains("ytd-player"));
        assertEquals("", engine.cosmeticCss("https://accounts.google.com/"));
    }

    @Test
    public void cosmeticRulesSupportDocumentSubdomainsWithoutLeakingToOtherOrigins() throws Exception {
        AdBlockEngine engine = AdBlockEngine.read(new StringReader(
                "hide|youtube.com|.ad\nhide|music.youtube.com|.music-ad"));
        assertEquals(".ad{display:none!important;}\n", engine.cosmeticCss(PAGE));
        assertTrue(engine.cosmeticCss("https://music.youtube.com/").contains(".music-ad"));
    }

    @Test
    public void disabledPreferencesAlwaysBypassMatchingIncludingMpvShorts() throws Exception {
        AdBlockEngine engine = bundled();
        for (boolean mpv : new boolean[] {false, true}) {
            for (boolean savedChoice : new boolean[] {false, true}) {
                boolean blocked = Preferences.isAdBlockingEnabled(mpv, savedChoice)
                        && engine.shouldBlock("https://m.youtube.com/shorts/dQw4w9WgXcQ", AD, false);
                assertEquals(!mpv && savedChoice, blocked);
            }
        }
    }

    @Test
    public void rejectsUnsupportedOrUnsafeRulesInsteadOfBroadeningThem() {
        for (String rule : new String[] {"block|*|/", "block|example.com.evil|",
                "block|https://example.com|/", "block|example.com|/?ad=1",
                "block|example.com|/#ad", "regex|example.com|.*",
                "hide|youtube.com|", "hide|youtube.com|.ad{color:red}",
                "hide|youtube.com|.ad;body", "hide|youtube.com|@import 'https://example.com'",
                "hide|youtube.com|.ad|.other"}) {
            assertThrows(rule, IOException.class, () -> AdBlockEngine.read(new StringReader(rule)));
        }
    }

    @Test
    public void rejectsOversizedResources() {
        StringBuilder rules = new StringBuilder();
        for (int index = 0; index < 513; index++) {
            rules.append("block|example.com|/\n");
        }
        assertThrows(IOException.class, () -> AdBlockEngine.read(new StringReader(rules.toString())));
        StringBuilder line = new StringBuilder("hide|youtube.com|.");
        for (int index = 0; index < 2048; index++) {
            line.append('a');
        }
        assertThrows(IOException.class, () -> AdBlockEngine.read(new StringReader(line.toString())));
    }

    @Test
    public void blankAndCommentLinesAreIgnored() throws Exception {
        AdBlockEngine engine = AdBlockEngine.read(new StringReader("# Rules\n\n  \n"));
        assertFalse(engine.shouldBlock(PAGE, AD, false));
        assertEquals("", engine.cosmeticCss(PAGE));
    }
}
