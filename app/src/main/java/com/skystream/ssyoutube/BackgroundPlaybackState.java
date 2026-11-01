package com.skystream.ssyoutube;

/** Keeps an existing session through YouTube's autoplay countdown, but never starts an idle one. */
final class BackgroundPlaybackState {
    static final long IDLE_GRACE_MS = 30_000;
    private long idleSince = -1;

    boolean needsProtection(boolean active, boolean serviceRequested, long now) {
        if (active) {
            idleSince = -1;
        } else if (idleSince < 0) {
            idleSince = now;
        }
        return active || (serviceRequested && now - idleSince < IDLE_GRACE_MS);
    }
}
