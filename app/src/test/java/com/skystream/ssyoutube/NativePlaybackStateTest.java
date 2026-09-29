package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativePlaybackStateTest {
    private static final String FIRST = "abcdefghijk";
    private static final String SECOND = "lmnopqrstuv";

    @Test
    public void duplicatePageMessagesDoNotRestartOrUnpause() {
        NativePlaybackState state = new NativePlaybackState();
        assertEquals(NativePlaybackState.Selection.NEW_VIDEO, state.select(FIRST, 30_000));
        state.positionMs = 60_000;
        state.wantsPlay = false;
        assertEquals(NativePlaybackState.Selection.IGNORE, state.select(FIRST, 0));
        assertEquals(NativePlaybackState.Selection.IGNORE, state.select(FIRST, 30_000));
        assertEquals(60_000, state.positionMs);
        assertFalse(state.wantsPlay);
        assertEquals(NativePlaybackState.Selection.SEEK, state.select(FIRST, 90_000));
        assertEquals(90_000, state.positionMs);
        assertFalse(state.wantsPlay);
    }

    @Test
    public void pendingExtractionResumesButNeverPlaysInBackground() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 0);
        assertFalse(state.shouldExtract());
        assertFalse(state.shouldPlay());
        state.lifecyclePaused = false;
        assertTrue(state.shouldExtract());
        assertTrue(state.shouldPlay());
        state.lifecyclePaused = true;
        assertFalse(state.shouldExtract());
        assertFalse(state.shouldPlay());
        state.lifecyclePaused = false;
        assertTrue(state.shouldPlay());
        state.wantsPlay = false;
        state.lifecyclePaused = true;
        state.lifecyclePaused = false;
        assertFalse(state.shouldPlay());
    }

    @Test
    public void preparedAndFailedStatesDoNotStartDuplicateExtraction() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 0);
        state.lifecyclePaused = false;
        state.prepared = true;
        assertFalse(state.shouldExtract());
        state.prepared = false;
        state.failed = true;
        assertFalse(state.shouldExtract());
        assertFalse(state.shouldPlay());
        assertEquals(NativePlaybackState.Selection.NEW_VIDEO, state.select(SECOND, 0));
        assertTrue(state.shouldExtract());
        assertTrue(state.shouldPlay());
    }

    @Test
    public void closeClearsStateAndAllowsLaterPlayback() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 99_000);
        state.stop();
        state.lifecyclePaused = false;
        assertNull(state.videoId);
        assertFalse(state.shouldExtract());
        assertFalse(state.shouldPlay());
        assertEquals(NativePlaybackState.Selection.NEW_VIDEO, state.select(FIRST, 0));
        assertEquals(0, state.positionMs);
        assertTrue(state.shouldPlay());
        assertEquals(NativePlaybackState.Selection.IGNORE, state.select("../bad", 0));
        assertEquals(FIRST, state.videoId);
    }

    @Test
    public void freshUntimestampedLivePlaybackUsesDefaultLivePosition() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 0);
        assertTrue(state.useLiveDefaultPosition(true));
        assertFalse(state.useLiveDefaultPosition(false));
        state.lifecyclePaused = false;
        state.lifecyclePaused = true;
        state.lifecyclePaused = false;
        assertTrue(state.useLiveDefaultPosition(true));
    }

    @Test
    public void explicitLiveTimestampIsPreservedAcrossDuplicateMessages() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 45_000);
        assertFalse(state.useLiveDefaultPosition(true));
        state.select(FIRST, 0);
        assertFalse(state.useLiveDefaultPosition(true));
        assertEquals(45_000, state.positionMs);
        state.select(SECOND, 0);
        assertTrue(state.useLiveDefaultPosition(true));
        state.select(SECOND, 90_000);
        assertFalse(state.useLiveDefaultPosition(true));
        assertEquals(90_000, state.positionMs);
    }

    @Test
    public void restoredLivePositionsIncludingZeroDoNotJumpToLiveEdge() {
        NativePlaybackState state = new NativePlaybackState();
        state.restore(FIRST, 0, 0, false, true);
        assertFalse(state.useLiveDefaultPosition(true));
        assertEquals(0, state.positionMs);
        assertFalse(state.wantsPlay);
        state.restore(FIRST, 125_000, 0, true, true);
        assertFalse(state.useLiveDefaultPosition(true));
        assertEquals(125_000, state.positionMs);
        state.restore(FIRST, 0, 0, true, false);
        assertTrue(state.useLiveDefaultPosition(true));
    }

    @Test
    public void failedInitialLiveManifestDoesNotPinRetryToPlaceholderZero() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 0);
        state.prepared = true;
        state.capturePosition(0, false);
        state.failed = true;
        state.prepared = false;
        state.failed = false;
        assertFalse(state.hasPosition);
        assertTrue(state.useLiveDefaultPosition(true));
        state.restore(FIRST, state.positionMs, state.explicitStartMs, true, state.hasPosition);
        assertTrue(state.useLiveDefaultPosition(true));
    }

    @Test
    public void placeholderCapturePreservesExplicitAndRestoredLivePositions() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 45_000);
        state.capturePosition(0, false);
        assertEquals(45_000, state.positionMs);
        assertFalse(state.useLiveDefaultPosition(true));
        state.restore(FIRST, 0, 0, false, true);
        state.capturePosition(0, false);
        assertFalse(state.useLiveDefaultPosition(true));
    }

    @Test
    public void resolvedTimelineMakesCapturedPositionKnownIncludingZero() {
        NativePlaybackState state = new NativePlaybackState();
        state.select(FIRST, 0);
        state.capturePosition(0, true);
        assertFalse(state.useLiveDefaultPosition(true));
        state.capturePosition(123_000, true);
        assertEquals(123_000, state.positionMs);
        assertTrue(state.hasPosition);
    }
}
