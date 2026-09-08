package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Validates the shape of the ad JSON-pruning script injected by {@link MainActivity},
 * mirroring uBlock Origin's {@code json-prune} scriptlet approach.
 */
public class MainActivityAdScriptTest {

    @Test
    public void prunesKnownAdSignalingKeys() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("'playerAds'"));
        assertTrue(script.contains("'adPlacements'"));
        assertTrue(script.contains("'adSlots'"));
        assertTrue(script.contains("'adBreakHeartbeatParams'"));
    }

    @Test
    public void patchesJsonParseAndResponseJson() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.contains("JSON.parse=function"));
        assertTrue(script.contains("Response.prototype.json=function"));
    }

    @Test
    public void prunesExistingInitialData() {
        String script = MainActivity.AD_JSON_PRUNE_SCRIPT;
        assertTrue(script.contains("prune(window.ytInitialPlayerResponse,0)"));
        assertTrue(script.contains("prune(window.ytInitialData,0)"));
    }

    @Test
    public void removesBuyNowButtonsAsynchronously() {
        String script = MainActivity.BUY_NOW_CLEANUP_SCRIPT;
        assertTrue(script.contains("BuyNowCleanupInstalled"));
        assertTrue(script.contains("buy\\s+(it\\s+)?now"));
        assertTrue(script.contains("shop\\s+now"));
        assertTrue(script.contains("visit\\s+site"));
        assertTrue(script.contains("if(inComments(el)){continue;}"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("addedNodes"));
        assertTrue(script.contains("characterData:true"));
        assertTrue(script.contains("setInterval"));
    }

    @Test
    public void hidesShoppingRenderersWithCss() {
        String script = MainActivity.AD_HIDING_SCRIPT;
        assertTrue(script.contains("ytm-product-card-renderer"));
        assertTrue(script.contains("ytm-shopping-offer-renderer"));
        assertTrue(script.contains("ytd-merch-shelf-renderer"));
    }

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
    public void setsVideoPosterFromPlayerThumbnail() {
        String script = MainActivity.VIDEO_THUMBNAIL_POSTER_SCRIPT;
        assertTrue(script.contains("ytInitialPlayerResponse"));
        assertTrue(script.contains("videoDetails"));
        assertTrue(script.contains("thumbnail.thumbnails"));
        assertTrue(script.contains("querySelectorAll('video')"));
        assertTrue(script.contains("setAttribute('poster',thumbnail)"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("setInterval"));
        assertTrue(script.contains("yt-navigate-start"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertTrue(script.contains("removeAttribute('poster')"));
        assertTrue(script.contains("details.videoId===videoId"));
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
                MainActivity.BUY_NOW_CLEANUP_SCRIPT,
                MainActivity.PLAYABLES_CLEANUP_SCRIPT,
                MainActivity.POSTS_CLEANUP_SCRIPT,
                MainActivity.SUBSCRIBER_COUNT_SCRIPT }) {
            assertTrue(script.contains("function inComments(el)"));
            assertTrue(script.contains(MainActivity.COMMENTS_SELECTOR));
        }
    }

    @Test
    public void togglesFullscreenOnVerticalVideoSwipes() {
        String script = MainActivity.FULLSCREEN_GESTURE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("FullscreenGestureInstalled"));
        assertTrue(script.contains("touchstart"));
        assertTrue(script.contains("touchend"));
        assertTrue(script.contains(".ytp-fullscreen-button"));
        assertTrue(script.contains("button.fullscreen-icon"));
        assertTrue(script.contains("requestFullscreen"));
        assertTrue(script.contains("exitFullscreen"));
        assertTrue(script.contains("isFullscreen"));
        assertTrue(script.contains("isPlaying"));
        assertTrue(script.contains("dy<0&&!fullscreen"));
        assertTrue(script.contains("dy>0&&fullscreen"));
        assertTrue(script.contains("button.click()"));
    }

    @Test
    public void tracksSwipesThroughMoveAndCancel() {
        for (String script : new String[] {
                MainActivity.FULLSCREEN_GESTURE_SCRIPT,
                MainActivity.MINIPLAYER_GESTURE_SCRIPT }) {
            assertTrue(script.contains("touchmove"));
            assertTrue(script.contains("touchcancel"));
            assertTrue(script.contains("composedPath"));
            assertTrue(script.contains("window.innerHeight||800)*0.06"));
            assertTrue(script.contains("onVerticalSwipe"));
        }
    }

    @Test
    public void minimizesToPipOnDownwardVideoSwipe() {
        String script = MainActivity.MINIPLAYER_GESTURE_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("MiniplayerGestureInstalled"));
        assertTrue(script.contains("touchstart"));
        assertTrue(script.contains("touchend"));
        assertTrue(script.contains("isWatchPage"));
        assertTrue(script.contains("swipe.dy<=0"));
        assertTrue(script.contains("pathname||'')==='/watch'"));
        assertTrue(script.contains("isFullscreen"));
        assertTrue(script.contains("isPlaying"));
        assertTrue(script.contains("yt-navigate-finish"));
        assertTrue(script.contains("__ssyoutubeResultsUrl"));
        assertTrue(script.contains("window.ssYouTubeNative.minimize"));
    }

    @Test
    public void miniplayerShowsOnlyTheVideoScaledToFit() {
        String script = MainActivity.MINIPLAYER_VIEW_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("ssyoutube-miniplayer-style"));
        assertTrue(script.contains("__ssyoutubeMiniplayerViewActive=true"));
        assertTrue(script.contains("overflow:hidden!important"));
        assertTrue(script.contains("width:100vw!important"));
        assertTrue(script.contains("height:100vh!important"));
        assertTrue(script.contains("object-fit:contain!important"));
        assertTrue(script.contains(".ytp-chrome-bottom"));
        assertTrue(script.contains("display:none!important"));
        assertTrue(script.contains("ssyoutube-miniplayer-player"));
        assertTrue(script.contains("new MutationObserver"));
        assertTrue(script.contains("playerFrom(mutations[i].addedNodes[j])"));
        assertFalse(script.contains("setInterval(apply,500)"));
    }

    @Test
    public void miniplayerViewResetRestoresThePage() {
        String script = MainActivity.MINIPLAYER_VIEW_RESET_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("__ssyoutubeMiniplayerViewActive=false"));
        assertTrue(script.contains("ViewObserver.disconnect()"));
        assertTrue(script.contains("removeChild(style)"));
        assertTrue(script.contains("classList.remove('ssyoutube-miniplayer')"));
        assertTrue(script.contains("classList.remove('ssyoutube-miniplayer-player')"));
    }

    @Test
    public void miniplayerRestartsPlaybackAfterWebViewIsMoved() {
        String script = MainActivity.MINIPLAYER_PLAYBACK_RESUME_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("video.ended"));
        assertTrue(script.contains("video.paused"));
        assertTrue(script.contains("video.play()"));
        assertTrue(script.contains("playback.catch(function()"));
        assertTrue(script.contains("attempt<5"));
        assertTrue(script.contains("scheduleResume(250,attempt+1)"));
        assertTrue(script.contains("__ssyoutubeMiniplayerKeepPlaying=true"));
        assertTrue(script.contains("video.addEventListener('pause'"));
        assertTrue(script.contains("new MutationObserver"));
    }

    @Test
    public void miniplayerPlaybackResumeResetStopsTheWatchdog() {
        String script = MainActivity.MINIPLAYER_PLAYBACK_RESUME_RESET_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("__ssyoutubeMiniplayerKeepPlaying=false"));
        assertTrue(script.contains("clearTimeout"));
        assertTrue(script.contains("ResumeObserver.disconnect()"));
    }

    @Test
    public void blocksPlaybackOnTheResultsPageBehindTheMiniplayer() {
        String script = MainActivity.RESULTS_AUTOPLAY_BLOCK_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("__ssyoutubeBlockResultsPlayback=true"));
        assertTrue(script.contains("HTMLMediaElement"));
        assertTrue(script.contains("proto.play=function()"));
        assertTrue(script.contains("addEventListener('play'"));
        assertTrue(script.contains("removeAttribute('autoplay')"));
        assertTrue(script.contains("setInterval(suppressAll,500)"));
    }

    @Test
    public void resultsPlaybackBlockResetRestoresPlayback() {
        String script = MainActivity.RESULTS_AUTOPLAY_BLOCK_RESET_SCRIPT;
        assertTrue(script.startsWith("(function"));
        assertTrue(script.contains("__ssyoutubeBlockResultsPlayback=false"));
        assertTrue(script.contains("clearInterval"));
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
        // Earlier revisions swapped or overlaid the specific <img>/shadow-DOM element YouTube
        // used to render its logo, and a later revision painted the container's own box with a
        // CSS background-image. Both broke on the mobile masthead because its ytm-* custom
        // elements are frequently unstyled (display:contents or a zero-size host), so a
        // background-image on the host paints nothing. This version instead forces the
        // container to have an explicit box and inserts a real, absolutely positioned <img>
        // into both the light DOM and (when present) the shadow root directly, which renders
        // regardless of the host's own sizing/display or whether the shadow tree defines a
        // <slot> for light DOM children.
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
        // Only opacity is touched (not visibility/display) so hidden content stays
        // hit-testable and a tap still reaches the original click handler (e.g. the home link).
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
}
