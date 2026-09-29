package com.skystream.ssyoutube;

/** URL-free state kept across asynchronous extraction, lifecycle pauses and view resizing. */
final class NativePlaybackState {
    enum Selection { NEW_VIDEO, SEEK, IGNORE }

    String videoId;
    long positionMs;
    long explicitStartMs;
    boolean hasPosition;
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
                hasPosition = true;
                return Selection.SEEK;
            }
            return Selection.IGNORE;
        }
        videoId = id;
        positionMs = start;
        explicitStartMs = start;
        hasPosition = start > 0;
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

    boolean useLiveDefaultPosition(boolean live) {
        return live && !hasPosition;
    }

    void capturePosition(long position, boolean resolvedTimeline) {
        if (videoId != null && resolvedTimeline) {
            positionMs = Math.max(0, position);
            hasPosition = true;
        }
    }

    void restore(String id, long position, long explicitStart, boolean resumePlayback,
                 boolean positionKnown) {
        if (!isVideoId(id)) {
            return;
        }
        stop();
        select(id, position);
        explicitStartMs = Math.max(0, explicitStart);
        wantsPlay = resumePlayback;
        hasPosition = positionKnown || positionMs > 0;
    }

    void stop() {
        videoId = null;
        positionMs = 0;
        explicitStartMs = 0;
        hasPosition = false;
        wantsPlay = false;
        prepared = false;
        failed = false;
    }

    static boolean isVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }
}
