package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundPlaybackStateTest {
    @Test
    public void activePlaybackOrExtractionNeedsProtectionImmediately() {
        BackgroundPlaybackState state = new BackgroundPlaybackState();
        assertTrue(state.needsProtection(true, false, 0));
        assertTrue(state.needsProtection(true, true, 60_000));
    }

    @Test
    public void idlePlaybackNeverStartsOrRestartsAService() {
        BackgroundPlaybackState state = new BackgroundPlaybackState();
        assertFalse(state.needsProtection(false, false, 0));
        assertTrue(state.needsProtection(true, false, 100));
        assertFalse(state.needsProtection(false, false, 101));
    }

    @Test
    public void graceCoversTwentySecondAutoplayAndExpiresAtThirtySeconds() {
        BackgroundPlaybackState state = new BackgroundPlaybackState();
        assertTrue(state.needsProtection(true, true, 0));
        assertTrue(state.needsProtection(false, true, 100));
        assertTrue(state.needsProtection(false, true, 20_100));
        assertTrue(state.needsProtection(false, true, 30_099));
        assertFalse(state.needsProtection(false, true, 30_100));
        assertFalse(state.needsProtection(false, true, 60_000));
    }

    @Test
    public void resumedPlaybackResetsTheIdleDeadline() {
        BackgroundPlaybackState state = new BackgroundPlaybackState();
        assertTrue(state.needsProtection(false, true, 0));
        assertTrue(state.needsProtection(true, true, 29_999));
        assertTrue(state.needsProtection(false, true, 30_000));
        assertTrue(state.needsProtection(false, true, 59_999));
        assertFalse(state.needsProtection(false, true, 60_000));
    }
}
