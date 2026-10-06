package com.skystream.ssyoutube;

/** Only inserts a scoped stylesheet; it never patches the player, fetch, or account APIs. */
final class AdBlockingScript {
    private AdBlockingScript() { }

    static String create(AdBlockEngine engine) {
        StringBuilder cases = new StringBuilder();
        for (String origin : NativePlaybackScript.ORIGIN_RULES) {
            cases.append("case ").append(quote(origin)).append(":css=")
                    .append(quote(engine.cosmeticCss(origin))).append(";break;");
        }
        return "(function(){"
                + "if(window.__ssyoutubeAdBlocking){window.__ssyoutubeAdBlocking();return;}"
                + "var css='';switch(location.origin){" + cases + "default:return;}"
                + "function apply(){"
                + "var parent=document.head||document.documentElement;if(!parent){return;}"
                + "var style=document.getElementById('ssyoutube-adblocking');"
                + "if(!style){style=document.createElement('style');"
                + "style.id='ssyoutube-adblocking';parent.appendChild(style);}"
                + "if(style.textContent!==css){style.textContent=css;}"
                + "}"
                + "window.__ssyoutubeAdBlocking=apply;"
                + "new MutationObserver(apply).observe(document,{childList:true,subtree:true});"
                + "document.addEventListener('DOMContentLoaded',apply);"
                + "window.addEventListener('yt-navigate-finish',apply);apply();"
                + "})()";
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                result.append('\\').append(character);
            } else if (character < 32 || character == '\u2028' || character == '\u2029') {
                result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
