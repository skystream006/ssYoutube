package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers retained page customizations and the native playback routing boundary. */
public class MainActivityPageScriptTest {

    @Test
    public void removesPlayablesSections() {
        String script = MainActivity.PLAYABLES_CLEANUP_SCRIPT;
        assertTrue(script.contains("PlayablesCleanupInstalled"));
        assertTrue(script.contains("if(inComments(el)){return false;}"));
        assertTrue(script.contains("playables?"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
    }

    @Test
    public void removesPostsSections() {
        String script = MainActivity.POSTS_CLEANUP_SCRIPT;
        assertTrue(script.contains("PostsCleanupInstalled"));
        assertTrue(script.contains("if(inComments(el)){return false;}"));
        assertTrue(script.contains("==='posts'"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
    }

    @Test
    public void injectsSubscriberCountsBesideChannelAvatars() {
        String script = MainActivity.SUBSCRIBER_COUNT_SCRIPT;
        assertTrue(script.contains("subscriberCountText"));
        assertTrue(script.contains("ssyoutube-subscriber-count"));
        assertTrue(script.contains("insertAdjacentElement('afterend',badge)"));
        assertTrue(script.contains("fetch(url,{credentials:'same-origin'})"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("if(inComments(avatar)){return;}"));
        assertTrue(script.contains("MAX_ACTIVE_LOOKUPS=3"));
        assertTrue(script.contains("enqueue(function(){"));
        assertTrue(script.contains("lookupDone"));
    }

    @Test
    public void keepsCommentRenderersOutOfCleanupScripts() {
        assertTrue(MainActivity.COMMENTS_SELECTOR.contains("ytm-comment-thread-renderer"));
        assertTrue(MainActivity.COMMENTS_SELECTOR.contains("ytm-comment-renderer"));
        assertTrue(MainActivity.COMMENTS_SELECTOR.contains("ytm-engagement-panel"));
        assertTrue(MainActivity.COMMENTS_SELECTOR.contains("ytd-comments"));
        for (String script : new String[] {
                MainActivity.PLAYABLES_CLEANUP_SCRIPT,
                MainActivity.POSTS_CLEANUP_SCRIPT,
                MainActivity.SUBSCRIBER_COUNT_SCRIPT }) {
            assertTrue(script.contains("function inComments(el)"));
            assertTrue(script.contains(MainActivity.COMMENTS_SELECTOR));
        }
    }

    @Test
    public void replacesYouTubeLogosWithTheAppLogo() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("AppLogoInstalled"));
        assertTrue(script.contains("ytm-mobile-topbar-renderer .topbar-logo"));
        assertTrue(script.contains("ytm-topbar-logo-renderer"));
        assertTrue(script.contains("ytm-youtube-logo"));
        assertTrue(script.contains("ytd-topbar-logo-renderer"));
        assertTrue(script.contains("img[src*=\"yt_logo\"]"));
        assertTrue(script.contains("LOGO_SRC=location.origin+'" + MainActivity.APP_LOGO_PATH + "'"));
        assertTrue(script.contains("image.classList&&image.classList.contains(CLASS)"));
        assertTrue(script.contains("image.src=LOGO_SRC"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("yt-navigate-finish"));
    }

    @Test
    public void replacesMobileTopbarLogoRegardlessOfItsTag() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        // The mobile masthead uses c3-icon.mobile-topbar-logo, not .topbar-logo.
        assertTrue(script.contains(",.mobile-topbar-logo,"));
        assertTrue(script.contains("root.querySelectorAll(CONTAINERS)"));
        assertTrue(script.contains("root.matches(CONTAINERS)"));
        assertTrue(script.contains("node.parentElement.closest(CONTAINERS)"));
    }

    @Test
    public void replacesThePlayerWatermarkLogo() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        assertTrue(script.contains(".ytp-watermark"));
        assertTrue(script.contains(".ytm-watermark"));
        assertTrue(script.contains(".branding-img-container"));
        assertTrue(script.contains("img[src*=\"watermark\"]"));
        assertTrue(script.contains("img.branding-img"));
    }

    @Test
    public void paintsLogoContainersWithAnOverlayImageInsteadOfSwappingInternalContent() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        assertTrue(script.contains("function paintContainer(node)"));
        assertTrue(script.contains("function ensureBox(node)"));
        assertTrue(script.contains("function placeOverlay(root)"));
        assertTrue(script.contains("function paintShadow(shadow)"));
        assertTrue(script.contains("if(node.shadowRoot){paintShadow(node.shadowRoot);}"));
        assertTrue(script.contains("position','absolute','important'"));
        assertTrue(script.contains("min-width"));
        assertTrue(script.contains("min-height"));
        assertTrue(script.contains("function hideLightChildren(node)"));
        assertTrue(script.contains("opacity','0','important'"));
    }

    @Test
    public void recognisesAppLogoRequests() {
        assertTrue(MainActivity.isAppLogoRequest(
                "https://m.youtube.com" + MainActivity.APP_LOGO_PATH));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://www.youtube.com" + MainActivity.APP_LOGO_PATH + "?v=1"));
        assertFalse(MainActivity.isAppLogoRequest("https://m.youtube.com/"));
        assertFalse(MainActivity.isAppLogoRequest(
                "https://m.youtube.com/other" + MainActivity.APP_LOGO_PATH));
        assertFalse(MainActivity.isAppLogoRequest(null));
    }

    @Test
    public void relatedVisibilityScriptTogglesRelatedSidebar() {
        String hide = MainActivity.relatedVisibilityScript(true);
        assertTrue(hide.contains("window.__ssyoutubeRelatedHidden=true"));
        assertTrue(hide.contains("document.querySelector('#related')"));
        assertTrue(hide.contains("style.display=window.__ssyoutubeRelatedHidden?'none':''"));

        String show = MainActivity.relatedVisibilityScript(false);
        assertTrue(show.contains("window.__ssyoutubeRelatedHidden=false"));
    }

    @Test
    public void headerVisibilityTargetsTheSelectedSiteMode() {
        String desktop = MainActivity.headerVisibilityScript(true, true);
        assertTrue(desktop.contains("window.__ssyoutubeHeaderHidden=true"));
        assertTrue(desktop.contains("window.__ssyoutubeHeaderSelector='#masthead-container'"));
        assertFalse(desktop.contains("#header-bar"));

        String mobile = MainActivity.headerVisibilityScript(true, false);
        assertTrue(mobile.contains("window.__ssyoutubeHeaderSelector='#header-bar'"));
        assertFalse(mobile.contains("#masthead-container"));
        assertFalse(mobile.contains("__ssyoutubeRelatedHidden"));
    }

    @Test
    public void hiddenHeaderCollapsesOnlyTheSelectedSiteModesSpacing() {
        String desktop = MainActivity.headerVisibilityScript(true, true);
        assertTrue(desktop.contains("ytd-app{--ytd-masthead-height:0px!important;}"));
        assertTrue(desktop.contains("ytd-page-manager{margin-top:0!important;}"));
        assertTrue(desktop.contains(
                "ytd-watch-flexy{--ytd-watch-flexy-masthead-height:0px!important;}"));
        assertFalse(desktop.contains("ytm-app{"));

        String mobile = MainActivity.headerVisibilityScript(true, false);
        assertTrue(mobile.contains("ytm-app{padding-top:0!important;}"));
        assertFalse(mobile.contains("ytd-app{"));
        assertFalse(mobile.contains("ytd-page-manager{"));
        assertFalse(mobile.contains("ytd-watch-flexy{"));
    }

    @Test
    public void headerVisibilityRestoresStylesAndHandlesLateRendering() {
        for (boolean desktop : new boolean[] {false, true}) {
            String script = MainActivity.headerVisibilityScript(false, desktop);
            assertTrue(script.contains("window.__ssyoutubeHeaderHidden=false"));
            assertTrue(script.contains("getElementById('ssyoutube-header-visibility')"));
            assertTrue(script.contains("if(style){style.remove();}return;"));
            assertTrue(script.contains("document.head||document.documentElement"));
            assertTrue(script.contains("if(!parent){return;}"));
            assertTrue(script.contains("document.createElement('style')"));
            assertTrue(script.contains("{display:none!important;}"));
            assertTrue(script.contains("window.__ssyoutubeHeaderLayoutCss="));
            assertTrue(script.contains("+window.__ssyoutubeHeaderLayoutCss;"));
            assertTrue(script.contains("if(!window.__ssyoutubeHeaderWatcher)"));
            assertTrue(script.contains("setInterval(apply,1000)"));
            assertFalse(script.contains(".style.display="));
        }
    }

    @Test
    public void acceptsOnlyMainFrameMessagesForTheCurrentVideoAndOrigin() {
        for (String origin : NativePlaybackScript.ORIGIN_RULES) {
            String url = origin + "/watch?v=dQw4w9WgXcQ";
            assertTrue(MainActivity.isTrustedPlaybackMessage(true, origin, url, url));
            assertFalse(MainActivity.isTrustedPlaybackMessage(false, origin, url, url));
            assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, url,
                    origin + "/watch?v=abcdefghijk"));
        }
    }

    @Test
    public void rejectsCrossOriginMessagesAndNonYouTubePages() {
        String url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ";
        for (String origin : new String[] {"https://accounts.google.com",
                "https://m.youtube.com.evil.example", "http://m.youtube.com",
                "https://m.youtube.com:444", "https://evil.example@m.youtube.com"}) {
            assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, url, url));
        }
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, "https://m.youtube.com",
                "https://accounts.google.com/", url));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, "https://www.youtube.com",
                url, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, "https://m.youtube.com",
                url, null));
    }

    @Test
    public void rejectsClickedVideosBeforeNavigationAndForgedSeekPositions() {
        String origin = "https://m.youtube.com";
        String video = origin + "/watch?v=dQw4w9WgXcQ";
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, origin + "/", video));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, video, video + "&t=30"));
        assertTrue(MainActivity.isTrustedPlaybackMessage(true, origin, video + "&t=30",
                video + "&start=30"));
    }

    @Test
    public void acceptsNonVideoRoutingOnlyForTheExactCurrentPage() {
        String origin = "https://m.youtube.com";
        String results = origin + "/results?search_query=music";
        assertTrue(MainActivity.isTrustedPlaybackMessage(true, origin, results, results));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, results, origin + "/"));
    }

    @Test
    public void shortsMessagesCannotSelectNativePlaybackEvenForTheSameVideo() {
        String origin = "https://m.youtube.com";
        String shorts = origin + "/shorts/dQw4w9WgXcQ";
        String watch = origin + "/watch?v=dQw4w9WgXcQ";
        assertTrue(MainActivity.isTrustedPlaybackMessage(true, origin, shorts, shorts));
        assertFalse(MainActivity.isTrustedPlaybackMessage(false, origin, shorts, shorts));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, shorts, watch));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, watch, shorts));
        assertFalse(MainActivity.isTrustedPlaybackMessage(true, origin, shorts,
                origin + "/shorts/abcdefghijk"));
        assertNull(MainActivity.closedVideoForPage("dQw4w9WgXcQ",
                PlaybackRequest.fromUrl(shorts)));
    }

    @Test
    public void canonicalizesShortLinksWithoutLosingStartPosition() {
        PlaybackRequest request = PlaybackRequest.fromUrl("https://youtu.be/dQw4w9WgXcQ?t=1m30s");
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90",
                MainActivity.canonicalPlaybackUrl(request, false));
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90",
                MainActivity.canonicalPlaybackUrl(request, true));
    }

    @Test
    public void closedVideoStaysSuppressedOnlyUntilLeavingItsPage() {
        PlaybackRequest video = PlaybackRequest.fromUrl(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ");
        String closed = MainActivity.closedVideoForPage(video.videoId, video);
        assertEquals(video.videoId, closed);
        assertEquals(closed, MainActivity.closedVideoForPage(closed, video));

        closed = MainActivity.closedVideoForPage(closed,
                PlaybackRequest.fromUrl("https://m.youtube.com/results?search_query=music"));
        assertNull(closed);
        assertNull(MainActivity.closedVideoForPage(closed, video));
        assertNull(MainActivity.closedVideoForPage(video.videoId,
                PlaybackRequest.fromUrl("https://m.youtube.com/")));
        assertNull(MainActivity.closedVideoForPage(video.videoId,
                PlaybackRequest.fromUrl("https://m.youtube.com/watch?v=abcdefghijk")));
    }
}
