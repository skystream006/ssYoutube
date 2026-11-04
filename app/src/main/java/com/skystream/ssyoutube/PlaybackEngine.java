package com.skystream.ssyoutube;

enum PlaybackEngine {
    WEB("web"), MPV("mpv"), EXOPLAYER("exoplayer");

    final String preference;

    PlaybackEngine(String preference) {
        this.preference = preference;
    }

    boolean isNative() {
        return this != WEB;
    }

    static PlaybackEngine fromPreference(String value, boolean legacyMpvEnabled) {
        for (PlaybackEngine engine : values()) {
            if (engine.preference.equals(value)) {
                return engine;
            }
        }
        return legacyMpvEnabled ? MPV : WEB;
    }
}
