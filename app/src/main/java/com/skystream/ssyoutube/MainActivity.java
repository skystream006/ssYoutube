package com.skystream.ssyoutube;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.CompoundButton;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps browsing and sign-in in a single WebView while native playback owns all media.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ssyoutube_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DEFAULT_LANGUAGE = "default_language";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";
    private static final String KEY_RELATED_HIDDEN = "related_hidden";
    private static final String KEY_HEADER_HIDDEN = "header_hidden";
    private static final String KEY_LOGGING_ENABLED = "logging_enabled";
    private static final String KEY_STATS_FOR_NERDS_ENABLED = "stats_for_nerds_enabled";

    /**
     * Hides or restores the desktop watch page's related-videos sidebar. The sidebar is
     * rendered (and re-rendered while navigating between videos) well after the page is
     * loaded, so the choice is remembered in the page and re-applied on a short interval
     * instead of only once.
     *
     * @param hidden true to hide {@code #related}, false to restore it
     * @return the script to evaluate in the page
     */
    static String relatedVisibilityScript(boolean hidden) {
        return "(function(){"
                + "window.__ssyoutubeRelatedHidden=" + (hidden ? "true" : "false") + ";"
                + "function apply(){"
                + "var related=document.querySelector('#related');"
                + "if(related){related.style.display=window.__ssyoutubeRelatedHidden?'none':'';}"
                + "}"
                + "apply();"
                + "if(!window.__ssyoutubeRelatedWatcher){"
                + "window.__ssyoutubeRelatedWatcher=setInterval(apply,1000);"
                + "}"
                + "})()";
    }

    /** Uses a removable stylesheet so restoring the header preserves the site's own styles. */
    static String headerVisibilityScript(boolean hidden, boolean desktopMode) {
        String selector = desktopMode ? "#masthead-container" : "#header-bar";
        String layoutCss = desktopMode
                ? "ytd-app{--ytd-masthead-height:0px!important;}"
                        + "ytd-page-manager{margin-top:0!important;}"
                        + "ytd-watch-flexy{--ytd-watch-flexy-masthead-height:0px!important;}"
                : "ytm-app{padding-top:0!important;}";
        return "(function(){"
                + "window.__ssyoutubeHeaderHidden=" + (hidden ? "true" : "false") + ";"
                + "window.__ssyoutubeHeaderSelector='" + selector + "';"
                + "window.__ssyoutubeHeaderLayoutCss='" + layoutCss + "';"
                + "function apply(){"
                + "var style=document.getElementById('ssyoutube-header-visibility');"
                + "if(!window.__ssyoutubeHeaderHidden){"
                + "if(style){style.remove();}return;}"
                + "var parent=document.head||document.documentElement;"
                + "if(!parent){return;}"
                + "if(!style){style=document.createElement('style');"
                + "style.id='ssyoutube-header-visibility';parent.appendChild(style);}"
                + "var css=window.__ssyoutubeHeaderSelector+'{display:none!important;}'"
                + "+window.__ssyoutubeHeaderLayoutCss;"
                + "if(style.textContent!==css){style.textContent=css;}"
                + "}"
                + "apply();"
                + "if(!window.__ssyoutubeHeaderWatcher){"
                + "window.__ssyoutubeHeaderWatcher=setInterval(apply,1000);"
                + "}"
                + "})()";
    }

    /**
     * Elements that make up (or host) the comments section. The cleanup scripts below never
     * remove or decorate anything inside them: on large screens (e.g. unfolded foldables) the
     * mobile site renders comments inside an engagement panel whose sections look just like the
     * shelves the cleanup scripts target, which previously left the comment list empty.
     */
    static final String COMMENTS_SELECTOR =
            "ytm-comment-section-renderer,ytm-comments-entry-point-header-renderer,"
                    + "ytm-comment-thread-renderer,ytm-comment-renderer,"
                    + "ytm-comment-replies-renderer,ytm-engagement-panel,"
                    + "ytm-engagement-panel-section-list-renderer,#comments,"
                    + "ytd-comments,ytd-comment-thread-renderer,ytd-comment-renderer";

    /** JavaScript helper that reports whether a node belongs to the comments section. */
    private static final String COMMENTS_HELPER_SCRIPT =
            "var COMMENTS='" + COMMENTS_SELECTOR + "';"
                    + "function inComments(el){"
                    + "if(!el||el.nodeType!==1){return false;}"
                    + "if(el.closest&&el.closest(COMMENTS)){return true;}"
                    + "return !!(el.querySelector&&el.querySelector(COMMENTS));"
                    + "}";

    /** Removes the "Playables" shelves and navigation entries from YouTube pages. */
    static final String PLAYABLES_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubePlayablesCleanupInstalled){return;}"
                    + "window.__ssyoutubePlayablesCleanupInstalled=true;"
                    + COMMENTS_HELPER_SCRIPT
                    + "var selector='ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer,"
                    + "ytm-pivot-bar-item-renderer,ytd-guide-entry-renderer,"
                    + "ytd-mini-guide-entry-renderer,a[href*=\"playables\"]';"
                    + "function textOf(el){return ((el.innerText||el.textContent||'')+' '+"
                    + "(el.getAttribute&&el.getAttribute('aria-label')||'')+' '+"
                    + "(el.getAttribute&&el.getAttribute('title')||''))"
                    + ".replace(/\\s+/g,' ').trim().toLowerCase();}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function isPlayables(el){"
                    + "if(inComments(el)){return false;}"
                    + "var href=el.getAttribute&&el.getAttribute('href')||'';"
                    + "if(href.indexOf('playables')!==-1){return true;}"
                    + "return /\\bplayables?\\b/.test(textOf(el));"
                    + "}"
                    + "function removePlayables(root){"
                    + "var nodes=(root&&root.querySelectorAll)?asArray(root.querySelectorAll(selector)):[];"
                    + "if(root&&root.matches&&root.matches(selector)){nodes.push(root);}"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var el=nodes[i];"
                    + "if(!isPlayables(el)){continue;}"
                    + "var target=el.closest('ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer,"
                    + "ytm-pivot-bar-item-renderer,ytd-guide-entry-renderer,"
                    + "ytd-mini-guide-entry-renderer')||el;"
                    + "target.remove();"
                    + "}"
                    + "}"
                    + "removePlayables(document);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){removePlayables(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    /** Removes the "Posts" shelf from the YouTube home page as it appears. */
    static final String POSTS_CLEANUP_SCRIPT =
            "(function(){"
                    + "if(window.__ssyoutubePostsCleanupInstalled){return;}"
                    + "window.__ssyoutubePostsCleanupInstalled=true;"
                    + COMMENTS_HELPER_SCRIPT
                    + "var selector='ytm-rich-section-renderer,ytm-shelf-renderer,"
                    + "ytm-item-section-renderer,ytm-rich-shelf-renderer,"
                    + "ytd-rich-section-renderer,ytd-shelf-renderer';"
                    + "function textOf(el){return (el.innerText||el.textContent||'')"
                    + ".replace(/\\s+/g,' ').trim().toLowerCase();}"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function isPosts(el){"
                    + "if(inComments(el)){return false;}"
                    + "var headings=asArray(el.querySelectorAll('h1,h2,h3,h4,"
                    + "ytm-rich-section-renderer>yt-formatted-string,"
                    + ".section-title yt-formatted-string'));"
                    + "for(var i=0;i<headings.length;i++){if(textOf(headings[i])==='posts'){return true;}}"
                    + "return false;"
                    + "}"
                    + "function removePosts(root){"
                    + "var nodes=(root&&root.querySelectorAll)?asArray(root.querySelectorAll(selector)):[];"
                    + "if(root&&root.matches&&root.matches(selector)){nodes.push(root);}"
                    + "for(var i=0;i<nodes.length;i++){if(isPosts(nodes[i])){nodes[i].remove();}}"
                    + "}"
                    + "removePosts(document);"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){removePosts(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    /** Adds each channel's subscriber count beside its avatar on video cards. */
    static final String SUBSCRIBER_COUNT_SCRIPT =
            "(function(){"
                    + "var badgeClass='ssyoutube-subscriber-count';"
                    + "var avatarSelector='ytm-channel-thumbnail-with-link-renderer,"
                    + "ytm-channel-thumbnail-supported-renderer,ytm-channel-thumbnail-renderer,ytm-avatar';"
                    + "var cache=window.__ssyoutubeSubscriberCounts||(window.__ssyoutubeSubscriberCounts={});"
                    + COMMENTS_HELPER_SCRIPT
                    // Channel pages are full HTML documents, so the lookups are queued: a long
                    // comment list would otherwise fire hundreds of parallel requests, which
                    // starves the page's own continuation requests and leaves it half rendered.
                    + "var MAX_ACTIVE_LOOKUPS=3;"
                    + "var queue=window.__ssyoutubeSubscriberQueue||"
                    + "(window.__ssyoutubeSubscriberQueue={pending:[],active:0});"
                    + "function pump(){"
                    + "while(queue.active<MAX_ACTIVE_LOOKUPS&&queue.pending.length){"
                    + "queue.active++;"
                    + "queue.pending.shift()();"
                    + "}"
                    + "}"
                    + "function enqueue(job){queue.pending.push(job);pump();}"
                    + "function lookupDone(){queue.active--;pump();}"
                    + "function channelUrl(avatar){"
                    + "var link=avatar.querySelector('a[href]')||avatar.closest('a[href]');"
                    + "if(!link){return null;}"
                    + "var url;"
                    + "try{url=new URL(link.href,location.origin);}catch(e){return null;}"
                    + "if(url.origin!==location.origin||!/^\\/@[\\w.-]+$|^\\/channel\\/UC[\\w-]+$|"
                    + "^\\/c\\/[\\w.-]+$|^\\/user\\/[\\w.-]+$/.test(url.pathname)){"
                    + "return null;"
                    + "}"
                    + "return url.origin+url.pathname;"
                    + "}"
                    + "function subscriberCount(html){"
                    + "var start=html.indexOf('\"subscriberCountText\"');"
                    + "if(start===-1){return null;}"
                    + "var match=/\"simpleText\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"/"
                    + ".exec(html.slice(start,start+2000));"
                    + "if(!match){return null;}"
                    + "try{return JSON.parse('\"'+match[1]+'\"');}catch(e){return null;}"
                    + "}"
                    + "function addBadge(avatar,count){"
                    + "var badge=avatar.nextElementSibling;"
                    + "if(!badge||!badge.classList.contains(badgeClass)){badge=null;}"
                    + "if(!badge){"
                    + "badge=document.createElement('span');"
                    + "badge.className=badgeClass;"
                    + "badge.style.cssText='display:inline-block;margin-left:6px;vertical-align:middle;"
                    + "font-size:12px;line-height:1.2;white-space:nowrap;';"
                    + "avatar.insertAdjacentElement('afterend',badge);"
                    + "}"
                    + "badge.textContent=count;"
                    + "}"
                    + "function sync(root){"
                    + "var avatars=(root&&root.querySelectorAll)"
                    + "?Array.prototype.slice.call(root.querySelectorAll(avatarSelector)):[];"
                    + "if(root&&root.matches&&root.matches(avatarSelector)){"
                    + "avatars.push(root);"
                    + "}"
                    + "for(var i=0;i<avatars.length;i++){(function(avatar){"
                    + "if(inComments(avatar)){return;}"
                    + "var url=channelUrl(avatar);"
                    + "if(!url){return;}"
                    + "if(Object.prototype.hasOwnProperty.call(cache,url)){"
                    + "if(cache[url]){addBadge(avatar,cache[url]);}"
                    + "return;"
                    + "}"
                    + "cache[url]=null;"
                    + "enqueue(function(){"
                    + "try{"
                    + "fetch(url,{credentials:'same-origin'}).then(function(response){return response.text();})"
                    + ".then(function(html){"
                    + "var count=subscriberCount(html);"
                    + "cache[url]=count;"
                    + "if(count){addBadge(avatar,count);}"
                    + "}).catch(function(){}).then(lookupDone);"
                    + "}catch(e){lookupDone();}"
                    + "});"
                    + "})(avatars[i]);}"
                    + "}"
                    + "sync(document);"
                    + "if(window.__ssyoutubeSubscriberCountInstalled){return;}"
                    + "window.__ssyoutubeSubscriberCountInstalled=true;"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){sync(node);}"
                    + "}"
                    + "}"
                    + "}).observe(document.documentElement,{childList:true,subtree:true});"
                    + "})()";

    /**
     * Same-origin path the WebView requests for the bundled app logo. Requests to it never
     * reach the network, they are answered from the app resources by
     * {@link YouTubeWebViewClient#shouldInterceptRequest}.
     */
    static final String APP_LOGO_PATH = "/ssyoutube_app_logo.png";

    /**
     * Swaps the YouTube wordmark on the page for the bundled app logo.
     *
     * <p>Earlier revisions tried to locate the specific element YouTube uses to render its
     * logo (an {@code <img>}, an inline SVG, or a shadow-DOM subtree) and either swap its
     * {@code src} or overlay a positioned replacement on top of it. YouTube renders the same
     * logo differently across the mobile masthead, the desktop masthead, and the player
     * watermark, and has changed the underlying markup (plain image vs. shadow DOM vs. a CSS
     * background/mask image with no {@code <img>} at all) several times, so any approach tied
     * to a specific rendering technique kept breaking again after a page/markup change.
     *
     * <p>A later revision painted every matched container's own box with the bundled logo as a
     * CSS {@code background-image} and hid the container's existing content. That still failed
     * on the mobile masthead: {@code ytm-*} custom elements are frequently unstyled custom
     * elements with no box of their own ({@code display:contents} or a zero-size host), so a
     * {@code background-image} on the host paints nothing even though the hidden content
     * underneath is gone.
     *
     * <p>This version instead inserts a real {@code <img>} element sized and positioned with
     * inline styles that do not depend on the container having any intrinsic box: the container
     * is forced to {@code position:relative} with an explicit minimum size, and the image is
     * absolutely positioned to fill it. The overlay image is inserted into <em>both</em> the
     * light DOM and, when present, the shadow root directly, because a light-DOM child is only
     * visible if the shadow tree renders a {@code <slot>} for it, and the shadow tree's own
     * content otherwise remains invisible to light-DOM styling. Existing content is hidden with
     * {@code opacity:0} (not removed or measured) so it stays hit-testable and taps still reach
     * whatever click handler (e.g. the home link) it carries.
     */
    static final String APP_LOGO_SCRIPT =
            "(function(){"
                    + "var CLASS='ssyoutube-app-logo';"
                    + "var OVERLAY_CLASS='ssyoutube-app-logo-overlay';"
                    + "var CONTAINERS='ytm-mobile-topbar-renderer .topbar-logo,"
                    + "ytm-topbar-logo-renderer,ytm-youtube-logo,.mobile-topbar-header-logo,.mobile-topbar-logo,"
                    + "ytm-logo,ytd-topbar-logo-renderer,ytd-logo,a#logo,#logo-icon,"
                    + "yt-icon#logo-icon,yt-icon.logo-icon,.ytp-watermark,.ytm-watermark,"
                    + ".branding-img-container';"
                    + "var IMAGES='img[src*=\"yt_logo\"],img[src*=\"youtube_logo\"],"
                    + "img[src*=\"ytl_logo\"],img[src*=\"watermark\"],img.branding-img';"
                    + "var LOGO_SRC=location.origin+'" + APP_LOGO_PATH + "';"
                    + "var MIN_SIZE_PX=24;"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    // Hides everything a container currently renders internally, without caring
                    // whether that content is an <img>, inline SVG, or something else. Only
                    // opacity is touched (not visibility/display), so the hidden content stays
                    // hit-testable and a tap still reaches whatever click handler (e.g. the home
                    // link) it carries. The overlay image itself is skipped so it stays visible.
                    + "function hideLightChildren(node){"
                    + "var children=asArray(node.children);"
                    + "for(var i=0;i<children.length;i++){"
                    + "if(children[i].classList.contains(OVERLAY_CLASS)){continue;}"
                    + "children[i].style.setProperty('opacity','0','important');"
                    + "}"
                    + "}"
                    // Mobile masthead custom elements (ytm-youtube-logo, ytm-topbar-logo-renderer)
                    // attach an open shadow root and render their logo entirely inside it, where
                    // regular element styling and children from the light DOM cannot reach. The
                    // existing shadow content is hidden the same way as light DOM children, and a
                    // dedicated overlay <img> is appended directly into the shadow root itself so
                    // it renders even though nothing in the light DOM (like a normal child) would.
                    + "function paintShadow(shadow){"
                    + "var children=asArray(shadow.children);"
                    + "for(var i=0;i<children.length;i++){"
                    + "if(children[i].classList&&children[i].classList.contains(OVERLAY_CLASS)){"
                    + "continue;"
                    + "}"
                    + "children[i].style.setProperty('opacity','0','important');"
                    + "}"
                    + "placeOverlay(shadow);"
                    + "}"
                    // Creates (or reuses) the overlay <img> inside the given root, sizes it to
                    // fill the root's host, and keeps its src in sync with the bundled logo.
                    + "function placeOverlay(root){"
                    + "var img=root.querySelector('img.'+OVERLAY_CLASS);"
                    + "if(!img){"
                    + "img=document.createElement('img');"
                    + "img.className=OVERLAY_CLASS;"
                    + "img.alt='YouTube';"
                    + "root.appendChild(img);"
                    + "}"
                    + "img.style.setProperty('position','absolute','important');"
                    + "img.style.setProperty('top','0','important');"
                    + "img.style.setProperty('left','0','important');"
                    + "img.style.setProperty('width','100%','important');"
                    + "img.style.setProperty('height','100%','important');"
                    + "img.style.setProperty('object-fit','contain','important');"
                    + "img.style.setProperty('object-position','left center','important');"
                    + "img.style.setProperty('pointer-events','none','important');"
                    + "if(img.src!==LOGO_SRC){img.src=LOGO_SRC;}"
                    + "return img;"
                    + "}"
                    // Forces the container to establish its own box regardless of how YouTube
                    // styles it this week (including custom elements left at the default
                    // display:contents, which paint nothing at all), so the absolutely
                    // positioned overlay image always has somewhere to be positioned within.
                    + "function ensureBox(node){"
                    + "var style=node.style;"
                    + "var computed=window.getComputedStyle(node);"
                    + "if(computed.position==='static'){"
                    + "style.setProperty('position','relative','important');"
                    + "}"
                    + "if(computed.display==='contents'||computed.display==='inline'){"
                    + "style.setProperty('display','inline-block','important');"
                    + "}"
                    + "if(node.offsetWidth<MIN_SIZE_PX){"
                    + "style.setProperty('min-width',MIN_SIZE_PX+'px','important');"
                    + "}"
                    + "if(node.offsetHeight<MIN_SIZE_PX){"
                    + "style.setProperty('min-height',MIN_SIZE_PX+'px','important');"
                    + "}"
                    + "}"
                    + "function paintContainer(node){"
                    + "if(!node||!node.style){return;}"
                    + "node.classList.add(CLASS);"
                    + "ensureBox(node);"
                    + "hideLightChildren(node);"
                    + "placeOverlay(node);"
                    + "if(node.shadowRoot){paintShadow(node.shadowRoot);}"
                    + "}"
                    + "function replaceContainer(node){"
                    + "if(node.parentElement&&node.parentElement.closest"
                    + "&&node.parentElement.closest(CONTAINERS)){return;}"
                    + "paintContainer(node);"
                    + "}"
                    // Kept as a fallback for stray logo <img>s outside the known containers
                    // (e.g. the wordmark can occasionally appear on its own in search results
                    // or other embedded surfaces): a direct src swap is enough for a plain img.
                    + "function replaceImage(image){"
                    + "if(image.classList&&image.classList.contains(CLASS)"
                    + "&&image.src===LOGO_SRC){return;}"
                    + "image.classList.add(CLASS);"
                    + "image.alt='YouTube';"
                    + "image.src=LOGO_SRC;"
                    + "image.style.setProperty('object-fit','contain','important');"
                    + "}"
                    + "function watch(target){"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){applyLogos(node);}"
                    + "}"
                    + "}"
                    + "}).observe(target,{childList:true,subtree:true});"
                    + "}"
                    + "function observeShadowRoots(root){"
                    + "if(!root||!root.querySelectorAll){return;}"
                    + "var all=asArray(root.querySelectorAll('*'));"
                    + "for(var i=0;i<all.length;i++){"
                    + "var shadow=all[i].shadowRoot;"
                    + "if(shadow&&!shadow.__ssyoutubeLogoObserved){"
                    + "shadow.__ssyoutubeLogoObserved=true;"
                    + "applyLogos(shadow);"
                    + "watch(shadow);"
                    + "}"
                    + "}"
                    + "}"
                    // Mobile's masthead components (ytm-youtube-logo, ytm-topbar-logo-renderer)
                    // render their markup inside an open shadow root, unlike the desktop
                    // ytd-* equivalents which use plain light DOM. Regular querySelectorAll
                    // calls cannot see across that boundary, so shadow roots are located and
                    // searched (and watched for further mutations) explicitly.
                    + "function applyLogos(root){"
                    + "if(!root){return;}"
                    + "var containers=root.querySelectorAll?asArray(root.querySelectorAll(CONTAINERS)):[];"
                    + "if(root.matches&&root.matches(CONTAINERS)){containers.push(root);}"
                    + "for(var i=0;i<containers.length;i++){replaceContainer(containers[i]);}"
                    + "var images=root.querySelectorAll?asArray(root.querySelectorAll(IMAGES)):[];"
                    + "if(root.matches&&root.matches(IMAGES)){images.push(root);}"
                    + "for(var j=0;j<images.length;j++){replaceImage(images[j]);}"
                    + "observeShadowRoots(root);"
                    + "}"
                    + "applyLogos(document);"
                    + "if(window.__ssyoutubeAppLogoInstalled){return;}"
                    + "window.__ssyoutubeAppLogoInstalled=true;"
                    + "document.addEventListener('yt-navigate-finish',function(){"
                    + "applyLogos(document);"
                    + "},true);"
                    + "watch(document.documentElement);"
                    + "setInterval(function(){applyLogos(document);},1000);"
                    + "})()";

    /** Height the bundled logo is downscaled to before it is handed to the WebView. */
    private static final int APP_LOGO_HEIGHT_PX = 96;

    /**
     * Delays (in milliseconds) at which {@link #APP_LOGO_SCRIPT} is re-injected after
     * {@code onPageFinished} fires. The mobile masthead's custom elements can attach their
     * shadow DOM and lay themselves out well after the WebView considers the page "finished",
     * so a single injection right at that point can run before the logo container exists or
     * has a size, and the swap is skipped until the script's own polling catches up. Re-running
     * the injection natively at a few short delays closes that gap without waiting on the
     * in-page interval.
     */
    private static final long[] APP_LOGO_REINJECT_DELAYS_MS = {300L, 1000L, 2500L, 5000L};

    private static final String PLAYBACK_MESSAGE_NAME = "ssYouTubePlayback";
    private static final String STATE_PLAYER = "native_player";
    private static final String STATE_FULLSCREEN = "native_fullscreen";
    private static final String STATE_MINIMIZED = "native_minimized";
    private static final String STATE_CLOSED_VIDEO = "closed_video";
    private static final String STATE_PLAYBACK_KEY = "playback_key";
    private static final String STATE_ROUTE_KEY = "route_key";

    private final Map<Integer, byte[]> appLogoCache = new HashMap<>();

    private final Handler logoInjectionHandler = new Handler(Looper.getMainLooper());
    private final Handler watchHistoryHandler = new Handler(Looper.getMainLooper());
    private final NativeWatchHistoryState watchHistoryState = new NativeWatchHistoryState();
    private final Runnable watchHistoryTick = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed || isDestroyed()) {
                return;
            }
            updateWatchHistory(false);
            watchHistoryHandler.postDelayed(this, NativeWatchHistoryState.SAMPLE_MS);
        }
    };

    private WebView webView;
    private NativePlayerView nativePlayer;
    private boolean playerFullscreen;
    private boolean playerMinimized;
    private boolean activityResumed;
    private String closedVideoId;
    private String lastPlaybackKey;
    private String lastRouteKey;
    private ViewGroup rootContainer;
    private ImageButton settingsButton;
    private TextView statsOverlay;
    private SharedPreferences prefs;
    private boolean desktopMode;
    private boolean relatedHidden;
    private boolean headerHidden;
    private volatile boolean loggingEnabled;
    private volatile boolean statsForNerdsEnabled;
    private StatsMonitor statsMonitor;
    private AppUpdater appUpdater;
    private boolean updatesResumed;
    private Logger logger;
    private int originalSystemUiVisibility;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false);
        relatedHidden = prefs.getBoolean(KEY_RELATED_HIDDEN, false);
        headerHidden = prefs.getBoolean(KEY_HEADER_HIDDEN, false);
        loggingEnabled = prefs.getBoolean(KEY_LOGGING_ENABLED, false);
        logger = Logger.get(this);
        logger.setEnabled(loggingEnabled);
        statsForNerdsEnabled = prefs.getBoolean(KEY_STATS_FOR_NERDS_ENABLED, false);
        logActivity("onCreate");
        applyTheme(prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        rootContainer = findViewById(R.id.root_container);
        settingsButton = findViewById(R.id.settings_button);
        statsOverlay = findViewById(R.id.stats_overlay);
        nativePlayer = new NativePlayerView(this, new NativePlayerView.Listener() {
            @Override
            public void onClose() {
                closeNativePlayback(true);
            }

            @Override
            public void onMinimize() {
                setPlayerFullscreen(false);
                playerMinimized = true;
                updatePlayerLayout();
            }

            @Override
            public void onToggleFullscreen() {
                if (playerMinimized) {
                    playerMinimized = false;
                    updatePlayerLayout();
                } else {
                    setPlayerFullscreen(!playerFullscreen);
                }
            }
        });
        nativePlayer.setPreferredLanguage(prefs.getString(
                KEY_DEFAULT_LANGUAGE, Preferences.DEFAULT_LANGUAGE));
        nativePlayer.setVisibility(View.GONE);
        rootContainer.addView(nativePlayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootContainer.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updatePlayerLayout();
            }
        });
        statsMonitor = new StatsMonitor(this, statsOverlay);
        appUpdater = new ViewModelProvider(this).get(AppUpdater.class);
        appUpdater.events().observe(this, event -> {
            if (updatesResumed) {
                appUpdater.dispatch(this);
            }
        });
        appUpdater.checkOnStartup(savedInstanceState);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Settings button clicked");
                showPreferences();
            }
        });

        configureWebView(webView);
        setStatsForNerdsEnabled(statsForNerdsEnabled);

        if (savedInstanceState != null) {
            closedVideoId = savedInstanceState.getString(STATE_CLOSED_VIDEO);
            lastPlaybackKey = savedInstanceState.getString(STATE_PLAYBACK_KEY);
            lastRouteKey = savedInstanceState.getString(STATE_ROUTE_KEY);
            Bundle playerState = savedInstanceState.getBundle(STATE_PLAYER);
            if (playerState != null) {
                nativePlayer.restoreState(playerState);
            }
            playerMinimized = savedInstanceState.getBoolean(STATE_MINIMIZED);
            setPlayerFullscreen(savedInstanceState.getBoolean(STATE_FULLSCREEN)
                    && nativePlayer.hasVideo());
            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(startUrl(getIntent()));
            }
        } else {
            webView.loadUrl(startUrl(getIntent()));
        }
        updatePlayerLayout();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        logActivity("onNewIntent");
        setIntent(intent);
        closedVideoId = null;
        lastPlaybackKey = null;
        setPlayerFullscreen(false);
        String url = startUrl(intent);
        webView.loadUrl(url);
        routePage(url);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        logActivity("onSaveInstanceState");
        webView.saveState(outState);
        Bundle playerState = new Bundle();
        nativePlayer.saveState(playerState);
        outState.putBundle(STATE_PLAYER, playerState);
        outState.putBoolean(STATE_FULLSCREEN, playerFullscreen);
        outState.putBoolean(STATE_MINIMIZED, playerMinimized);
        outState.putString(STATE_CLOSED_VIDEO, closedVideoId);
        outState.putString(STATE_PLAYBACK_KEY, lastPlaybackKey);
        outState.putString(STATE_ROUTE_KEY, lastRouteKey);
        appUpdater.saveState(outState);
    }

    @Override
    protected void onPause() {
        updatesResumed = false;
        updateWatchHistory(true);
        activityResumed = false;
        watchHistoryHandler.removeCallbacks(watchHistoryTick);
        nativePlayer.onPause();
        super.onPause();
        logActivity("onPause");
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        logActivity("onResume");
        activityResumed = true;
        webView.onResume();
        nativePlayer.onResume();
        routePage(webView.getUrl());
        watchHistoryHandler.removeCallbacks(watchHistoryTick);
        watchHistoryHandler.post(watchHistoryTick);
        updatesResumed = true;
        appUpdater.dispatch(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rootContainer.post(this::updatePlayerLayout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppUpdater.INSTALL_PERMISSION_REQUEST) {
            appUpdater.onInstallPermissionResult();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        statsMonitor.onStart();
    }

    @Override
    protected void onStop() {
        statsMonitor.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logActivity("onDestroy");
        statsMonitor.onDestroy();
        logoInjectionHandler.removeCallbacksAndMessages(null);
        watchHistoryHandler.removeCallbacksAndMessages(null);
        watchHistoryState.reset();
        nativePlayer.release();
        if (webView != null) {
            webView.stopLoading();
            rootContainer.removeView(webView);
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        logActivity("onKeyDown keyCode=" + keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK && playerFullscreen) {
            setPlayerFullscreen(false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && goBack()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setPlayerFullscreen(boolean fullscreen) {
        if (playerFullscreen == fullscreen) {
            return;
        }
        playerFullscreen = fullscreen;
        if (fullscreen) {
            playerMinimized = false;
            originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        }
        updatePlayerLayout();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        logActivity("configureWebView");
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(Preferences.userAgent(desktopMode));
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(desktopMode);
        settings.setBuiltInZoomControls(desktopMode);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        // Persist the session cookies so the user only has to sign in once.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(view, true);
        }

        installPlaybackRouting(view);
        view.setWebViewClient(new YouTubeWebViewClient());
        view.setWebChromeClient(new WebChromeClient());
    }

    private void installPlaybackRouting(WebView view) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try {
                WebViewCompat.addWebMessageListener(view, PLAYBACK_MESSAGE_NAME,
                        NativePlaybackScript.ORIGIN_RULES,
                        (source, message, origin, mainFrame, reply) -> {
                            if (source == webView && !isDestroyed()
                                    && message.getType() == WebMessageCompat.TYPE_STRING
                                    && isTrustedPlaybackMessage(mainFrame, origin.toString(),
                                            source.getUrl(), message.getData())) {
                                routePage(source.getUrl());
                            }
                        });
            } catch (IllegalArgumentException | UnsupportedOperationException error) {
                logActivity("Playback messages unavailable", error);
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(view,
                        NativePlaybackScript.SCRIPT + ";" + NativeWatchHistoryScript.SCRIPT,
                        NativePlaybackScript.ORIGIN_RULES);
            } catch (IllegalArgumentException | UnsupportedOperationException error) {
                logActivity("Document start script unavailable", error);
            }
        }
    }

    static boolean isTrustedPlaybackMessage(boolean mainFrame, String sourceOrigin,
            String currentUrl, String reportedUrl) {
        if (!mainFrame || !PlaybackRequest.isYouTubePage(currentUrl)
                || !PlaybackRequest.isYouTubePage(reportedUrl)) {
            return false;
        }
        String origin = playbackOrigin(sourceOrigin);
        if (origin == null || !origin.equals(playbackOrigin(currentUrl))
                || !origin.equals(playbackOrigin(reportedUrl))) {
            return false;
        }
        PlaybackRequest current = PlaybackRequest.fromUrl(currentUrl);
        PlaybackRequest reported = PlaybackRequest.fromUrl(reportedUrl);
        if (current == null || reported == null) {
            return current == null && reported == null && currentUrl.equals(reportedUrl);
        }
        return current.videoId.equals(reported.videoId)
                && current.startPositionMs == reported.startPositionMs;
    }

    private static String playbackOrigin(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1
                    && uri.getPort() != 443)) {
                return null;
            }
            String origin = "https://" + uri.getHost().toLowerCase(Locale.US);
            return NativePlaybackScript.ORIGIN_RULES.contains(origin) ? origin : null;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    static String canonicalPlaybackUrl(PlaybackRequest request, boolean desktopMode) {
        String url = request.watchUrl();
        if (request.startPositionMs > 0) {
            url += "&t=" + request.startPositionMs / 1000;
        }
        return Preferences.siteModeUrl(url, desktopMode);
    }

    static String closedVideoForPage(String closedVideoId, PlaybackRequest request) {
        return request != null && request.videoId.equals(closedVideoId) ? closedVideoId : null;
    }

    private void routePage(String url) {
        if (url == null || nativePlayer == null || isDestroyed()) {
            return;
        }
        if (!PlaybackRequest.isYouTubePage(url)) {
            closedVideoId = null;
            closeNativePlayback(false);
            lastRouteKey = null;
            return;
        }
        PlaybackRequest request = PlaybackRequest.fromUrl(url);
        closedVideoId = closedVideoForPage(closedVideoId, request);
        if (request == null) {
            lastRouteKey = null;
            if (nativePlayer.hasVideo()) {
                setPlayerFullscreen(false);
                playerMinimized = true;
                updatePlayerLayout();
            }
            return;
        }
        String key = request.videoId + ":" + request.startPositionMs;
        boolean changedRoute = !key.equals(lastRouteKey);
        lastRouteKey = key;
        if (request.videoId.equals(closedVideoId)) {
            return;
        }
        if (!nativePlayer.hasVideo() || !key.equals(lastPlaybackKey)) {
            if (nativePlayer.hasVideo() && !request.videoId.equals(nativePlayer.getVideoId())) {
                updateWatchHistory(true);
            }
            nativePlayer.play(request.videoId, request.startPositionMs);
            lastPlaybackKey = key;
            if (!activityResumed) {
                nativePlayer.onPause();
            }
        }
        if (changedRoute) {
            playerMinimized = false;
        }
        updatePlayerLayout();
    }

    private void closeNativePlayback(boolean suppressCurrentVideo) {
        if (suppressCurrentVideo) {
            PlaybackRequest current = PlaybackRequest.fromUrl(webView.getUrl());
            closedVideoId = current != null ? current.videoId : nativePlayer.getVideoId();
            if (closedVideoId == null && lastPlaybackKey != null) {
                int separator = lastPlaybackKey.indexOf(':');
                if (separator > 0) {
                    closedVideoId = lastPlaybackKey.substring(0, separator);
                }
            }
        }
        updateWatchHistory(true);
        watchHistoryState.reset();
        nativePlayer.stop();
        lastPlaybackKey = null;
        playerMinimized = false;
        setPlayerFullscreen(false);
        updatePlayerLayout();
    }

    private void updateWatchHistory(boolean flush) {
        if (nativePlayer == null || !nativePlayer.hasVideo()) {
            watchHistoryState.reset();
            return;
        }
        String id = nativePlayer.getVideoId();
        watchHistoryState.onContinuityToken(nativePlayer.getHistoryContinuityToken());
        NativeWatchHistoryState.Report report = watchHistoryState.sample(id,
                nativePlayer.getPositionMsForHistory(), nativePlayer.getDurationMsForHistory(),
                !flush && nativePlayer.isPlayingForHistory(),
                nativePlayer.getPlaybackSpeedForHistory(), SystemClock.elapsedRealtime(), flush);
        if (webView != null && !isDestroyed() && playbackOrigin(webView.getUrl()) != null) {
            webView.evaluateJavascript(
                    NativeWatchHistoryScript.update(id, watchHistoryState.session(), report, flush),
                    null);
        }
    }

    private void updatePlayerLayout() {
        if (nativePlayer == null || webView == null || isDestroyed()) {
            return;
        }
        boolean visible = nativePlayer.hasVideo();
        nativePlayer.setVisibility(visible ? View.VISIBLE : View.GONE);
        nativePlayer.setFullscreen(playerFullscreen);
        nativePlayer.setMinimized(playerMinimized);
        webView.setVisibility(playerFullscreen ? View.INVISIBLE : View.VISIBLE);
        int width = rootContainer.getWidth();
        int height = rootContainer.getHeight();
        int toolbar = Math.round(48 * getResources().getDisplayMetrics().density);
        int margin = getResources().getDimensionPixelSize(R.dimen.miniplayer_margin);
        FrameLayout.LayoutParams playerParams;
        int webTop = 0;
        if (playerFullscreen) {
            playerParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else if (playerMinimized) {
            int miniWidth = Math.min(
                    getResources().getDimensionPixelSize(R.dimen.miniplayer_width),
                    Math.max(1, width - 2 * margin));
            int miniHeight = Math.min(miniWidth * 9 / 16 + toolbar,
                    Math.max(1, height - 2 * margin));
            playerParams = new FrameLayout.LayoutParams(miniWidth, miniHeight,
                    Gravity.BOTTOM | Gravity.END);
            playerParams.setMarginEnd(margin);
            playerParams.bottomMargin = margin;
        } else {
            int dockHeight = Math.min(width * 9 / 16 + toolbar, Math.max(toolbar, height / 2));
            playerParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dockHeight, Gravity.TOP);
            webTop = visible ? dockHeight : 0;
        }
        nativePlayer.setLayoutParams(playerParams);
        FrameLayout.LayoutParams webParams = (FrameLayout.LayoutParams) webView.getLayoutParams();
        if (webParams.topMargin != webTop) {
            webParams.topMargin = webTop;
            webView.setLayoutParams(webParams);
        }
        nativePlayer.bringToFront();
        statsOverlay.bringToFront();
        settingsButton.bringToFront();
        FrameLayout.LayoutParams settingsParams =
                (FrameLayout.LayoutParams) settingsButton.getLayoutParams();
        int settingsBottom = visible && playerMinimized
                ? playerParams.height + 2 * margin : margin;
        if (settingsParams.bottomMargin != settingsBottom) {
            settingsParams.bottomMargin = settingsBottom;
            settingsButton.setLayoutParams(settingsParams);
        }
        updateSettingsButton(webView.getUrl());
    }

    private boolean goBack() {
        logActivity("goBack");
        return navigate(backSteps());
    }

    private boolean goForward() {
        logActivity("goForward");
        return navigate(forwardSteps());
    }

    private int backSteps() {
        WebBackForwardList history = webView.copyBackForwardList();
        return NavigationHistory.backSteps(historyUrls(history), history.getCurrentIndex());
    }

    private int forwardSteps() {
        WebBackForwardList history = webView.copyBackForwardList();
        return NavigationHistory.forwardSteps(historyUrls(history), history.getCurrentIndex());
    }

    private boolean navigate(int steps) {
        logActivity("navigate steps=" + steps);
        if (steps == 0 || !webView.canGoBackOrForward(steps)) {
            return false;
        }
        webView.goBackOrForward(steps);
        return true;
    }

    private List<String> historyUrls(WebBackForwardList history) {
        List<String> urls = new ArrayList<>(history.getSize());
        for (int i = 0; i < history.getSize(); i++) {
            urls.add(history.getItemAtIndex(i).getUrl());
        }
        return urls;
    }

    private void applyTheme(int theme) {
        logActivity("applyTheme theme=" + theme);
        int mode;
        if (theme == Preferences.THEME_LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (theme == Preferences.THEME_DARK) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void updateSettingsButton(String url) {
        logActivityUrl("updateSettingsButton url=", url);
        boolean show = !playerFullscreen && (Preferences.isHomePage(url)
                || (desktopMode && Preferences.isVideoPage(url)));
        settingsButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setStatsForNerdsEnabled(boolean enabled) {
        statsForNerdsEnabled = enabled;
        statsOverlay.setVisibility(enabled ? View.VISIBLE : View.GONE);
        statsMonitor.setEnabled(enabled);
    }

    /** Applies the current related-sidebar choice to {@code view}. */
    private void applyRelatedVisibility(WebView view) {
        logActivity("applyRelatedVisibility hidden=" + relatedHidden);
        if (view == null || !PlaybackRequest.isYouTubePage(view.getUrl())) {
            return;
        }
        view.evaluateJavascript(relatedVisibilityScript(relatedHidden), null);
    }

    private void applyHeaderVisibility(WebView view) {
        if (view == null || !PlaybackRequest.isYouTubePage(view.getUrl())) {
            return;
        }
        view.evaluateJavascript(headerVisibilityScript(headerHidden, desktopMode), null);
    }

    private void openPreferencePanel(AlertDialog dialog) {
        logActivity("openPreferencePanel");
        settingsButton.setImageResource(R.drawable.ic_close);
        settingsButton.setContentDescription(getString(R.string.close));
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void closePreferencePanel() {
        logActivity("closePreferencePanel");
        settingsButton.animate().translationY(0f).setDuration(
                getResources().getInteger(android.R.integer.config_shortAnimTime))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        settingsButton.setImageResource(R.drawable.ic_settings);
                        settingsButton.setContentDescription(getString(R.string.settings));
                    }
                });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Settings button clicked");
                showPreferences();
            }
        });
    }

    private void openSupportedLinkSettings() {
        Uri packageUri = Uri.parse("package:" + getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Some devices do not expose the dedicated supported-links screen.
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            Toast.makeText(this, R.string.supported_links_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showPreferences() {
        logActivity("showPreferences");
        View content = getLayoutInflater().inflate(R.layout.dialog_preferences, null);
        TextView versionView = content.findViewById(R.id.app_version);
        versionView.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME));
        content.findViewById(R.id.check_updates_button).setOnClickListener(v ->
                appUpdater.check(true));
        Spinner languageSpinner = content.findViewById(R.id.language_spinner);
        Spinner themeSpinner = content.findViewById(R.id.theme_spinner);
        Spinner siteModeSpinner = content.findViewById(R.id.site_mode_spinner);
        TextView advancedToggle = content.findViewById(R.id.advanced_toggle);
        View advancedSettings = content.findViewById(R.id.advanced_settings);
        advancedToggle.setOnClickListener(v -> {
            boolean expanded = advancedSettings.getVisibility() != View.VISIBLE;
            advancedSettings.setVisibility(expanded ? View.VISIBLE : View.GONE);
            advancedToggle.setText(expanded ? R.string.advanced_expanded
                    : R.string.advanced_collapsed);
            advancedToggle.setContentDescription(getString(expanded ? R.string.collapse_advanced
                    : R.string.expand_advanced));
        });
        ToggleButton relatedVideosToggle = content.findViewById(R.id.related_videos_toggle);
        ToggleButton headerToggle = content.findViewById(R.id.header_toggle);
        Switch loggingToggle = content.findViewById(R.id.logging_toggle);
        Switch statsForNerdsToggle = content.findViewById(R.id.stats_for_nerds_toggle);

        languageSpinner.setSelection(Preferences.languageIndex(prefs.getString(
                KEY_DEFAULT_LANGUAGE, Preferences.DEFAULT_LANGUAGE)));
        int theme = prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM);
        if (theme == Preferences.THEME_LIGHT) {
            themeSpinner.setSelection(1);
        } else if (theme == Preferences.THEME_DARK) {
            themeSpinner.setSelection(2);
        } else {
            themeSpinner.setSelection(0);
        }
        siteModeSpinner.setSelection(desktopMode ? 1 : 0);
        relatedVideosToggle.setChecked(relatedHidden);
        headerToggle.setChecked(headerHidden);
        loggingToggle.setChecked(loggingEnabled);
        statsForNerdsToggle.setChecked(statsForNerdsEnabled);

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = Preferences.languageAt(position);
                prefs.edit().putString(KEY_DEFAULT_LANGUAGE, selected).apply();
                nativePlayer.setPreferredLanguage(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selected = Preferences.THEME_SYSTEM;
                if (position == 1) {
                    selected = Preferences.THEME_LIGHT;
                } else if (position == 2) {
                    selected = Preferences.THEME_DARK;
                }
                if (selected == prefs.getInt(KEY_THEME, Preferences.THEME_SYSTEM)) {
                    return;
                }
                logActivity("Theme preference changed to " + selected);
                prefs.edit().putInt(KEY_THEME, selected).apply();
                applyTheme(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        siteModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean wantsDesktop = position == 1;
                if (wantsDesktop == desktopMode) {
                    return;
                }
                logActivity("Site mode preference changed desktop=" + wantsDesktop);
                desktopMode = wantsDesktop;
                prefs.edit().putBoolean(KEY_DESKTOP_MODE, wantsDesktop).apply();
                WebSettings webSettings = webView.getSettings();
                webSettings.setUserAgentString(Preferences.userAgent(wantsDesktop));
                webSettings.setSupportZoom(wantsDesktop);
                webSettings.setBuiltInZoomControls(wantsDesktop);
                webSettings.setDisplayZoomControls(false);
                webView.clearHistory();
                webView.loadUrl(Preferences.siteModeUrl(webView.getUrl(), wantsDesktop));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        relatedVideosToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == relatedHidden) {
                    return;
                }
                logActivity("Related videos preference changed hidden=" + isChecked);
                relatedHidden = isChecked;
                prefs.edit().putBoolean(KEY_RELATED_HIDDEN, relatedHidden).apply();
                applyRelatedVisibility(webView);
            }
        });

        headerToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == headerHidden) {
                    return;
                }
                headerHidden = isChecked;
                prefs.edit().putBoolean(KEY_HEADER_HIDDEN, headerHidden).apply();
                applyHeaderVisibility(webView);
            }
        });

        loggingToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == loggingEnabled) {
                    return;
                }
                loggingEnabled = isChecked;
                prefs.edit().putBoolean(KEY_LOGGING_ENABLED, loggingEnabled).apply();
                logger.setEnabled(isChecked);
            }
        });
        content.findViewById(R.id.view_logs_button).setOnClickListener(v -> showLogs());
        content.findViewById(R.id.share_log_button).setOnClickListener(v -> shareLog());
        content.findViewById(R.id.supported_links_button).setOnClickListener(v ->
                openSupportedLinkSettings());
        content.findViewById(R.id.clear_log_button).setOnClickListener(v ->
                logger.clear((file, success) -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, success ? R.string.log_cleared
                                : R.string.log_operation_failed, Toast.LENGTH_SHORT).show();
                    }
                }));

        statsForNerdsToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == statsForNerdsEnabled) {
                    return;
                }
                logActivity("Stats for nerds preference changed enabled=" + isChecked);
                prefs.edit().putBoolean(KEY_STATS_FOR_NERDS_ENABLED, isChecked).apply();
                setStatsForNerdsEnabled(isChecked);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setTitle(getString(R.string.preferences));
            dialogWindow.setBackgroundDrawableResource(R.drawable.bg_preference_panel);
            dialogWindow.setGravity(Gravity.BOTTOM);
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialogWindow.setWindowAnimations(R.style.PreferencePanelAnimation);
        }

        ImageButton backButton = content.findViewById(R.id.back_button);
        ImageButton forwardButton = content.findViewById(R.id.forward_button);
        backButton.setEnabled(backSteps() != 0);
        forwardButton.setEnabled(forwardSteps() != 0);
        backButton.setAlpha(backButton.isEnabled() ? 1f : 0.4f);
        forwardButton.setAlpha(forwardButton.isEnabled() ? 1f : 0.4f);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Back navigation clicked");
                if (goBack()) {
                    dialog.dismiss();
                }
            }
        });
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Forward navigation clicked");
                if (goForward()) {
                    dialog.dismiss();
                }
            }
        });
        content.findViewById(R.id.refresh_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Refresh clicked");
                webView.reload();
                dialog.dismiss();
            }
        });
        content.findViewById(R.id.home_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logActivity("Home clicked");
                webView.loadUrl(Preferences.homeUrl(desktopMode));
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                logActivity("Preferences dismissed");
                closePreferencePanel();
            }
        });

        openPreferencePanel(dialog);
        dialog.show();

        content.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int panelHeight = bottom - top;
            if (panelHeight > 0 && panelHeight != oldBottom - oldTop) {
                settingsButton.animate().translationY(-panelHeight).setDuration(
                        getResources().getInteger(android.R.integer.config_shortAnimTime));
            }
        });
    }

    private String startUrl(Intent intent) {
        logActivity("startUrl");
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getDataString() != null) {
            String inAppUrl = SiteScope.normalizeInAppUrl(intent.getDataString());
            if (inAppUrl != null) {
                PlaybackRequest request = PlaybackRequest.fromUrl(inAppUrl);
                if (request != null) {
                    return canonicalPlaybackUrl(request, desktopMode);
                }
                return inAppUrl;
            }
        }
        return Preferences.homeUrl(desktopMode);
    }

    /**
     * Decodes the bundled logo once per theme, downscaled to roughly the size the page
     * renders it at, and keeps the encoded bytes around for later requests.
     *
     * @param resource the drawable holding the logo for the active theme
     * @return the encoded PNG bytes of the downscaled logo
     */
    private synchronized byte[] appLogoBytes(int resource) {
        byte[] cached = appLogoCache.get(resource);
        if (cached != null) {
            return cached;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), resource, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outHeight / (options.inSampleSize * 2) >= APP_LOGO_HEIGHT_PX) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource, options);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded);
            bitmap.recycle();
        }
        byte[] bytes = encoded.toByteArray();
        appLogoCache.put(resource, bytes);
        return bytes;
    }

    /**
     * @param url a request URL seen by the WebView
     * @return true when the request is the WebView asking for the bundled app logo
     */
    static boolean isAppLogoRequest(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        int pathStart = lower.indexOf('/', lower.indexOf("://") + 3);
        if (pathStart < 0) {
            return false;
        }
        int pathEnd = lower.length();
        for (int i = pathStart; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '?' || c == '#') {
                pathEnd = i;
                break;
            }
        }
        return lower.substring(pathStart, pathEnd).equals(APP_LOGO_PATH);
    }

    private void showLogs() {
        TextView text = new TextView(this);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setText(R.string.logs_loading);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.view_logs)
                .setView(scroll, padding, padding, padding, 0)
                .setPositiveButton(R.string.close, null)
                .show();
        logger.read((logs, success) -> {
            if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                return;
            }
            if (!success) {
                text.setText(R.string.log_operation_failed);
            } else if (logs.isEmpty()) {
                text.setText(R.string.log_empty);
            } else {
                text.setText(logs);
            }
        });
    }

    private void shareLog() {
        logger.share((file, success) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (!success || file == null) {
                Toast.makeText(this, success ? R.string.log_empty
                        : R.string.log_operation_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                android.net.Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setClipData(ClipData.newRawUri(getString(R.string.share_log), uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, getString(R.string.share_log)));
            } catch (ActivityNotFoundException | IllegalArgumentException error) {
                Toast.makeText(this, R.string.log_operation_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Records routine activities with a caller location, rather than a full stack trace. */
    private void logActivity(String message) {
        if (!loggingEnabled) {
            return;
        }
        logger.log("D", message, null);
    }

    /**
     * Records the exception type without URLs, response bodies or credentials.
     */
    private void logActivity(String message, Throwable throwable) {
        if (!loggingEnabled) {
            return;
        }
        logger.log("E", message + " " + throwable.getClass().getSimpleName(), null);
    }

    /**
     * Records only the origin of a URL-bearing activity, omitting browsing identifiers.
     */
    private void logActivityUrl(String prefix, String url) {
        if (!loggingEnabled) {
            return;
        }
        logActivity(prefix + LogFormat.safeUrl(url));
    }

    private class YouTubeWebViewClient extends WebViewClient {

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && loggingEnabled) {
                logger.log("E", "Web resource error code=" + error.getErrorCode()
                        + " mainFrame=" + request.isForMainFrame() + " "
                        + LogFormat.safeUrl(request.getUrl().toString()), new Throwable());
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int code, String description, String url) {
            super.onReceivedError(view, code, description, url);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && loggingEnabled) {
                logger.log("E", "Page load error code=" + code + " "
                        + LogFormat.safeUrl(url), new Throwable());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);
            if (loggingEnabled) {
                logger.log("E", "HTTP error status=" + response.getStatusCode()
                        + " mainFrame=" + request.isForMainFrame() + " "
                        + LogFormat.safeUrl(request.getUrl().toString()), new Throwable());
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isAppLogoRequest(url)) {
                logActivityUrl("Serving app logo ", url);
                return appLogoResponse();
            }
            return super.shouldInterceptRequest(view, request);
        }

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (isAppLogoRequest(url)) {
                logActivityUrl("Serving app logo ", url);
                return appLogoResponse();
            }
            return super.shouldInterceptRequest(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return !SiteScope.isInAppUrl(request.getUrl().toString());
            }
            String url = request.getUrl().toString();
            return handleUrl(view, url);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // This legacy callback does not identify frames. Only main-document callbacks may
            // normalize a URL with loadUrl(), otherwise a subframe could navigate the browser.
            return !SiteScope.isInAppUrl(url);
        }

        private boolean handleUrl(WebView view, String url) {
            logActivityUrl("handleUrl ", url);
            String inAppUrl = SiteScope.normalizeInAppUrl(url);
            if (inAppUrl == null) {
                logActivityUrl("Blocked out-of-scope URL ", url);
                return true;
            }
            PlaybackRequest request = PlaybackRequest.fromUrl(inAppUrl);
            if (request != null && !PlaybackRequest.isYouTubePage(inAppUrl)) {
                inAppUrl = canonicalPlaybackUrl(request, desktopMode);
            }
            if (!inAppUrl.equals(url)) {
                logActivityUrl("Normalized URL to ", inAppUrl);
                view.loadUrl(inAppUrl);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            logActivityUrl("onPageStarted ", url);
            logoInjectionHandler.removeCallbacksAndMessages(null);
            if (handleUrl(view, url)) {
                return;
            }
            updateSettingsButton(url);
            applyPageScripts(view);
            routePage(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            logActivityUrl("onPageFinished ", url);
            updateSettingsButton(view.getUrl());
            applyPageScripts(view);
            routePage(view.getUrl());
            CookieManager.getInstance().flush();
            scheduleAppLogoReinjection(view);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            updateSettingsButton(url);
            applyPageScripts(view);
            routePage(url);
        }

        private void applyPageScripts(WebView view) {
            if (view != webView || !PlaybackRequest.isYouTubePage(view.getUrl())) {
                return;
            }
            view.evaluateJavascript(
                    NativePlaybackScript.SCRIPT + ";" + NativeWatchHistoryScript.SCRIPT, null);
            applyRelatedVisibility(view);
            applyHeaderVisibility(view);
            view.evaluateJavascript(PLAYABLES_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(POSTS_CLEANUP_SCRIPT, null);
            view.evaluateJavascript(SUBSCRIBER_COUNT_SCRIPT, null);
            view.evaluateJavascript(APP_LOGO_SCRIPT, null);
        }

        /**
         * Re-runs {@link MainActivity#APP_LOGO_SCRIPT} at a few short delays after the page
         * finishes loading. See {@link MainActivity#APP_LOGO_REINJECT_DELAYS_MS} for why a
         * single injection at {@code onPageFinished} is not always enough on the mobile site.
         */
        private void scheduleAppLogoReinjection(WebView view) {
            logActivity("scheduleAppLogoReinjection");
            WeakReference<WebView> viewRef = new WeakReference<>(view);
            for (long delayMs : APP_LOGO_REINJECT_DELAYS_MS) {
                logoInjectionHandler.postDelayed(() -> {
                    WebView target = viewRef.get();
                    if (target != null && target.isAttachedToWindow()
                            && PlaybackRequest.isYouTubePage(target.getUrl())) {
                        logActivity("Reinject app logo after " + delayMs + "ms");
                        target.evaluateJavascript(APP_LOGO_SCRIPT, null);
                    }
                }, delayMs);
            }
        }

        private WebResourceResponse appLogoResponse() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse("image/png", null, 200, "OK", headers,
                    new ByteArrayInputStream(appLogoBytes(appLogoResource())));
        }

        private int appLogoResource() {
            int nightMode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES
                    ? R.drawable.app_logo_dark : R.drawable.app_logo_light;
        }

    }
}
