package com.skystream.ssyoutube;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Makes the WebView a browsing surface, leaving all media playback to the native player. */
final class NativePlaybackScript {
    static final Set<String> ORIGIN_RULES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "https://youtube.com", "https://www.youtube.com", "https://m.youtube.com",
            "https://music.youtube.com")));

    static final String SCRIPT =
            "(function(){"
                    + "if(location.protocol!=='https:'||"
                    + "!/^(www\\.|m\\.|music\\.)?youtube\\.com$/.test(location.hostname)){return;}"
                    + "if(window.__ssyoutubeNativePlayback){"
                    + "window.__ssyoutubeNativePlayback();return;}"
                    + "var lastUrl='',pending=false;"
                    + "function silence(media){"
                    + "try{media.autoplay=false;media.removeAttribute('autoplay');"
                    + "media.muted=true;if(!media.paused){media.pause();}}catch(e){}"
                    + "}"
                    + "var proto=window.HTMLMediaElement&&HTMLMediaElement.prototype;"
                    + "if(proto){proto.play=function(){silence(this);return Promise.resolve();};}"
                    + "document.addEventListener('play',function(event){"
                    + "if(event.target instanceof HTMLMediaElement){silence(event.target);}"
                    + "},true);"
                    + "function update(){"
                    + "pending=false;"
                    + "var media=document.querySelectorAll('video,audio');"
                    + "for(var i=0;i<media.length;i++){silence(media[i]);}"
                    + "var parent=document.head||document.documentElement;"
                    + "if(parent&&!document.getElementById('ssyoutube-native-player')){"
                    + "var style=document.createElement('style');style.id='ssyoutube-native-player';"
                    + "style.textContent='video,audio,#player,#movie_player,#player-container-outer,"
                    + "#player-container-inner,ytd-player,ytm-player,ytm-player-container,"
                    + "#player-container-id,.player-container,#player-full-bleed-container,"
                    + "#player-wide-container,ytd-watch-flexy #full-bleed-container,"
                    + "ytm-watch .player-size,ytm-watch .player-placeholder,"
                    + "ytm-app .player-size,ytm-app .player-placeholder,"
                    + "ytm-watch .player-container-wrapper,ytm-watch .player-container-placeholder"
                    + "{display:none!important;height:0!important;min-height:0!important;"
                    + "max-height:0!important;width:0!important;min-width:0!important;"
                    + "padding:0!important;margin:0!important;overflow:hidden!important;}"
                    + "ytd-watch-flexy{--ytd-watch-flexy-player-height:0px!important;"
                    + "--ytd-watch-flexy-player-min-height:0px!important;"
                    + "--ytd-watch-flexy-player-max-height:0px!important;}"
                    + "ytm-watch .watch-below-the-player,ytm-watch .watch-below-the-player-container"
                    + ",ytm-app .watch-below-the-player,ytm-app .watch-below-the-player-container"
                    + "{padding-top:0!important;margin-top:0!important;}';"
                    + "parent.appendChild(style);"
                    + "}"
                    + "if(window.top===window&&location.href!==lastUrl&&"
                    + "window.ssYouTubePlayback&&window.ssYouTubePlayback.postMessage){"
                    + "window.ssYouTubePlayback.postMessage(location.href);lastUrl=location.href;}"
                    + "}"
                    + "function schedule(){"
                    + "if(pending){return;}pending=true;setTimeout(update,0);"
                    + "}"
                    + "window.__ssyoutubeNativePlayback=update;"
                    + "['pushState','replaceState'].forEach(function(name){"
                    + "var original=history[name];history[name]=function(){"
                    + "var result=original.apply(this,arguments);schedule();return result;};"
                    + "});"
                    + "new MutationObserver(schedule).observe(document,{childList:true,subtree:true});"
                    + "document.addEventListener('DOMContentLoaded',schedule);"
                    + "window.addEventListener('yt-navigate-finish',schedule,true);"
                    + "window.addEventListener('popstate',schedule);"
                    + "window.addEventListener('hashchange',schedule);"
                    + "setInterval(update,1000);update();"
                    + "})()";

    private NativePlaybackScript() { }
}
