package com.skystream.ssyoutube;

final class BackgroundAudioScript {
    static final String MESSAGE_NAME = "ssYouTubeBackgroundAudio";
    static final String STATE = "window.__ssyoutubeBackgroundAudio"
            + "?window.__ssyoutubeBackgroundAudio.state():null";
    private static final String PAUSE_MEDIA = "Array.prototype.forEach.call("
            + "document.querySelectorAll('video,audio'),function(media){media.pause();});";
    static final String PAUSE = "if(window.__ssyoutubeBackgroundAudio){"
            + "window.__ssyoutubeBackgroundAudio.pause();}else{" + PAUSE_MEDIA + "}";

    private BackgroundAudioScript() { }

    static String create(String source, boolean enabled) {
        return "(" + source + ")(" + enabled + ");";
    }

    static String foreground(boolean foreground, boolean protectedPlayback) {
        return "if(window.__ssyoutubeBackgroundAudio){window.__ssyoutubeBackgroundAudio"
                + ".setForeground(" + foreground + "," + protectedPlayback + ");}"
                + (!foreground && !protectedPlayback ? "else{" + PAUSE_MEDIA + "}" : "");
    }
}
