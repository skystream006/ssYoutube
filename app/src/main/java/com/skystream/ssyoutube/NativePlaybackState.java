package com.skystream.ssyoutube;

/** URL-free state kept across asynchronous extraction, lifecycle pauses and view resizing. */
final class NativePlaybackState {
    enum Selection { NEW_VIDEO, SEEK, IGNORE }

    String videoId;
    long positionMs;
    long explicitStartMs;
    boolean wantsPlay;
    boolean lifecyclePaused = true;
    boolean prepared;
    boolean failed;

    Selection select(String id, long startPositionMs) {
        if (!isVideoId(id)) {
            return Selection.IGNORE;
        }
        long start = Math.max(0, startPositionMs);
        if (id.equals(videoId)) {
            if (start > 0 && start != explicitStartMs) {
                positionMs = start;
                explicitStartMs = start;
                return Selection.SEEK;
            }
            return Selection.IGNORE;
        }
        videoId = id;
        positionMs = start;
        explicitStartMs = start;
        wantsPlay = true;
        prepared = false;
        failed = false;
        return Selection.NEW_VIDEO;
    }

    boolean shouldExtract() {
        return videoId != null && !lifecyclePaused && !prepared && !failed;
    }

    boolean shouldPlay() {
        return videoId != null && !lifecyclePaused && wantsPlay && !failed;
    }

    void stop() {
        videoId = null;
        positionMs = 0;
        explicitStartMs = 0;
        wantsPlay = false;
        prepared = false;
        failed = false;
    }

    static boolean isVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }
}
