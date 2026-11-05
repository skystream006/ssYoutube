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

    static PlaybackEngine fromPreference(String value, Boolean legacyMpvEnabled) {
        for (PlaybackEngine engine : values()) {
            if (engine.preference.equals(value)) {
                return engine;
            }
        }
        return legacyMpvEnabled == null ? EXOPLAYER : (legacyMpvEnabled ? MPV : WEB);
    }
}
