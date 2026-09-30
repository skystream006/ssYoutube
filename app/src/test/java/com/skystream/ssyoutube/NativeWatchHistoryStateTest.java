package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Test;

public class NativeWatchHistoryStateTest {
    private static final String ID = "abcdefghijk";

    @Test
    public void reportsNativePositionAndDurationEveryTenSeconds() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        for (int second = 0; second < 10; second++) {
            assertNull(state.sample(ID, 30000 + second * 1000, 90000, true, 1,
                    second * 1000, false));
        }
        NativeWatchHistoryState.Report report =
                state.sample(ID, 40000, 90000, true, 1, 10000, false);
        assertEquals(ID, report.videoId);
        assertEquals("30.000", report.starts);
        assertEquals("40.000", report.ends);
        assertEquals(40000, report.positionMs);
        assertEquals(90000, report.durationMs);
        assertTrue(report.playing);
        assertNull(state.sample(ID, 41000, 90000, true, 1, 11000, false));
    }

    @Test
    public void excludesForwardAndBackwardSeeksFromWatchedRanges() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample(ID, 0, 90000, true, 1, 0, false);
        state.sample(ID, 1000, 90000, true, 1, 1000, false);
        state.sample(ID, 60000, 90000, true, 1, 2000, false);
        state.sample(ID, 61000, 90000, true, 1, 3000, false);
        state.sample(ID, 5000, 90000, true, 1, 4000, false);
        NativeWatchHistoryState.Report report =
                state.sample(ID, 6000, 90000, true, 1, 5000, true);
        assertEquals("0.000,60.000,5.000", report.starts);
        assertEquals("1.000,61.000,6.000", report.ends);
        assertEquals(6000, report.positionMs);
    }

    @Test
    public void flushesOnPauseAndDoesNotCountPausedOrBufferingTime() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample(ID, 0, 90000, true, 1, 0, false);
        state.sample(ID, 1000, 90000, true, 1, 1000, false);
        NativeWatchHistoryState.Report paused =
                state.sample(ID, 1000, 90000, false, 1, 2000, false);
        assertEquals("1.000", paused.ends);
        assertFalse(paused.playing);
        assertNull(state.sample(ID, 1000, 90000, false, 1, 20000, false));
        assertNull(state.sample(ID, 1000, 90000, true, 1, 21000, false));
        NativeWatchHistoryState.Report resumed =
                state.sample(ID, 2000, 90000, true, 1, 22000, true);
        assertEquals("1.000", resumed.starts);
        assertEquals("2.000", resumed.ends);
    }

    @Test
    public void switchingOrClosingNeverReusesOtherVideoRangesOrSessions() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample(ID, 0, 0, true, 1, 0, false);
        NativeWatchHistoryState.Report first =
                state.sample(ID, 1000, 0, true, 1, 1000, true);
        assertNull(state.sample("12345678901", 8000, 0, true, 1, 2000, false));
        NativeWatchHistoryState.Report second =
                state.sample("12345678901", 9000, 0, true, 1, 3000, true);
        assertEquals("8.000", second.starts);
        assertNotEquals(first.session, second.session);
        state.reset();
        state.sample("12345678901", 0, 0, true, 1, 4000, false);
        NativeWatchHistoryState.Report reopened =
                state.sample("12345678901", 1000, 0, true, 1, 5000, true);
        assertNotEquals(second.session, reopened.session);
    }

    @Test
    public void ignoresUnresolvedInvalidOrUnobservedPlayback() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        assertNull(state.sample("bad", 0, 0, true, 1, 0, false));
        assertNull(state.sample(ID, -1, 0, true, 1, 0, false));
        state.sample(ID, 0, 0, true, 1, 0, false);
        assertNull(state.sample(ID, 10000, 0, true, 1, 10000, false));
        assertNull(state.sample(ID, 10000, 0, true, 1, 20000, false));
    }

    @Test
    public void supportsPlaybackSpeedAndUnknownLiveDuration() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample(ID, 20000, -1, true, 2, 0, false);
        NativeWatchHistoryState.Report report =
                state.sample(ID, 22000, -1, true, 2, 1000, true);
        assertEquals("20.000", report.starts);
        assertEquals("22.000", report.ends);
        assertEquals(0, report.durationMs);
    }

    @Test
    public void playerEventsSplitEvenSmallSeeksAndPausesBetweenSamples() {
        NativeWatchHistoryState state = new NativeWatchHistoryState();
        state.sample(ID, 0, 90000, true, 1, 0, false);
        state.sample(ID, 1000, 90000, true, 1, 1000, false);
        state.onContinuityToken(1);
        state.sample(ID, 2100, 90000, true, 1, 2000, false);
        NativeWatchHistoryState.Report report =
                state.sample(ID, 3100, 90000, true, 1, 3000, true);
        assertEquals("0.000,2.100", report.starts);
        assertEquals("1.000,3.100", report.ends);
    }
}
