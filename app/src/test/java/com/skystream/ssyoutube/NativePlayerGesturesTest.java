package com.skystream.ssyoutube;

import static com.skystream.ssyoutube.NativePlayerGestures.Action.*;
import static com.skystream.ssyoutube.NativePlayerGestures.Mode.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativePlayerGesturesTest {
    private final NativePlayerGestures gestures =
            new NativePlayerGestures(8, 100, 48, 300, 500);

    @Test
    public void singleTapWaitsForDoubleTapWindowAndFiresOnlyOnce() {
        assertEquals(NONE, gestures.down(20, 80, 0, 300, NORMAL));
        assertEquals(NONE, gestures.up(20, 80, 50));
        assertEquals(300, gestures.tapDelay(50));
        assertEquals(NONE, gestures.confirmTap(349));
        assertEquals(SINGLE_TAP, gestures.confirmTap(350));
        assertEquals(NONE, gestures.confirmTap(400));
        assertEquals(-1, gestures.tapDelay(400));
    }

    @Test
    public void doubleTapSeeksOnlyOnSecondUpWithoutTogglingControls() {
        for (int x : new int[] {20, 150, 280}) {
            gestures.down(x, 80, 0, 300, NORMAL);
            gestures.up(x, 80, 40);
            assertEquals(NONE, gestures.down(x, 80, 120, 300, NORMAL));
            assertEquals(-1, gestures.tapDelay(120));
            assertEquals(x == 20 ? SEEK_BACK : x == 280 ? SEEK_FORWARD : TOGGLE_PLAY,
                    gestures.up(x, 80, 170));
            assertEquals(NONE, gestures.confirmTap(600));
        }
    }

    @Test
    public void fullscreenDoubleTapUsesSameSeekZones() {
        gestures.down(280, 80, 0, 300, FULLSCREEN);
        gestures.up(280, 80, 40);
        gestures.down(280, 80, 120, 300, FULLSCREEN);
        assertEquals(SEEK_FORWARD, gestures.up(280, 80, 170));
    }

    @Test
    public void miniDoubleTapRemainsAnExpandClickInEveryZone() {
        for (int x : new int[] {20, 150, 280}) {
            gestures.down(x, 80, 0, 300, MINIMIZED);
            gestures.up(x, 80, 40);
            gestures.down(x, 80, 120, 300, MINIMIZED);
            assertEquals(SINGLE_TAP, gestures.up(x, 80, 170));
        }
    }

    @Test
    public void expiredOrDistantTapsDoNotSeek() {
        gestures.down(20, 80, 0, 300, NORMAL);
        gestures.up(20, 80, 40);
        assertEquals(SINGLE_TAP, gestures.down(20, 80, 341, 300, NORMAL));
        gestures.up(20, 80, 360);
        assertEquals(SINGLE_TAP, gestures.down(280, 80, 400, 300, NORMAL));
        gestures.up(280, 80, 440);
        assertEquals(SINGLE_TAP, gestures.confirmTap(740));
    }

    @Test
    public void nearbyTapsAcrossZoneBoundaryDoNotSeekOrPause() {
        gestures.down(99, 80, 0, 300, NORMAL);
        gestures.up(99, 80, 40);
        assertEquals(SINGLE_TAP, gestures.down(101, 80, 120, 300, NORMAL));
        assertEquals(NONE, gestures.up(101, 80, 170));
        assertEquals(SINGLE_TAP, gestures.confirmTap(470));
    }

    @Test
    public void tapsCannotPairAcrossModeOrSurfaceSizeChanges() {
        gestures.down(20, 80, 0, 300, NORMAL);
        gestures.up(20, 80, 40);
        assertEquals(SINGLE_TAP, gestures.down(20, 80, 120, 300, FULLSCREEN));
        gestures.up(20, 80, 170);
        assertEquals(SINGLE_TAP, gestures.down(20, 80, 220, 500, FULLSCREEN));
        assertEquals(NONE, gestures.up(20, 80, 250));
    }

    @Test
    public void verticalSwipesHaveModeSpecificActions() {
        assertEquals(ENTER_FULLSCREEN, swipe(NORMAL, 0, -70));
        assertEquals(EXPAND, swipe(MINIMIZED, 0, -70));
        assertEquals(NONE, swipe(FULLSCREEN, 0, -70));
        assertEquals(EXIT_FULLSCREEN, swipe(FULLSCREEN, 0, 70));
        assertEquals(MINIMIZE, swipe(NORMAL, 0, 70));
        assertEquals(DISMISS, swipe(MINIMIZED, 0, 70));
        assertEquals(NONE, gestures.confirmTap(1000));
    }

    @Test
    public void horizontalDiagonalAndSmallDragsDoNotTriggerAnyAction() {
        assertEquals(NONE, swipe(NORMAL, 80, 10));
        assertEquals(NONE, swipe(NORMAL, 60, 70));
        assertEquals(NONE, swipe(NORMAL, 0, 47));
        assertEquals(NONE, gestures.confirmTap(1000));
    }

    @Test
    public void swipeCanBeSlowAndUsesDistanceNotFlingVelocity() {
        gestures.down(150, 160, 0, 300, NORMAL);
        gestures.move(150, 100);
        assertEquals(ENTER_FULLSCREEN, gestures.up(150, 80, 2000));
    }

    @Test
    public void draggingBackToStartAndLongPressAreNotTaps() {
        gestures.down(20, 80, 0, 300, NORMAL);
        gestures.move(100, 80);
        assertEquals(NONE, gestures.up(20, 80, 200));
        gestures.down(20, 80, 300, 300, NORMAL);
        assertEquals(NONE, gestures.up(20, 80, 801));
        assertEquals(NONE, gestures.confirmTap(1200));
    }

    @Test
    public void cancellationSuppressesPendingSingleAndRemainingPointerEvents() {
        gestures.down(20, 80, 0, 300, NORMAL);
        gestures.up(20, 80, 40);
        gestures.cancel();
        assertEquals(NONE, gestures.confirmTap(400));
        gestures.move(20, 200);
        assertEquals(NONE, gestures.up(20, 200, 450));
        assertEquals(-1, gestures.tapDelay(450));
    }

    @Test
    public void secondPointerOrCancelDuringDoubleTapNeverSeeks() {
        gestures.down(20, 80, 0, 300, NORMAL);
        gestures.up(20, 80, 40);
        gestures.down(20, 80, 120, 300, NORMAL);
        gestures.cancel();
        assertEquals(NONE, gestures.up(20, 80, 170));
        assertEquals(NONE, gestures.confirmTap(600));
    }

    @Test
    public void secondTapBecomingSwipeDoesNotAlsoSeekOrClick() {
        gestures.down(20, 160, 0, 300, NORMAL);
        gestures.up(20, 160, 40);
        gestures.down(20, 160, 120, 300, NORMAL);
        assertEquals(ENTER_FULLSCREEN, gestures.up(20, 80, 250));
        assertEquals(NONE, gestures.confirmTap(600));
    }

    @Test
    public void eachDoubleTapPairSeeksOnceAndCanBeRepeated() {
        for (int i = 0; i < 4; i++) {
            long time = i * 200;
            gestures.down(280, 80, time, 300, NORMAL);
            gestures.up(280, 80, time + 30);
            gestures.down(280, 80, time + 70, 300, NORMAL);
            assertEquals(SEEK_FORWARD, gestures.up(280, 80, time + 100));
        }
        assertEquals(NONE, gestures.confirmTap(1200));
    }

    @Test
    public void seekZonesAndInvalidSizesAreExplicit() {
        assertEquals(SEEK_BACK, NativePlayerGestures.tapZone(0, 300));
        assertEquals(TOGGLE_PLAY, NativePlayerGestures.tapZone(100, 300));
        assertEquals(TOGGLE_PLAY, NativePlayerGestures.tapZone(200, 300));
        assertEquals(SEEK_FORWARD, NativePlayerGestures.tapZone(300, 300));
        assertEquals(NONE, NativePlayerGestures.tapZone(-1, 300));
        assertEquals(NONE, NativePlayerGestures.tapZone(301, 300));
        assertEquals(NONE, NativePlayerGestures.tapZone(0, 0));
        gestures.down(0, 0, 0, 0, NORMAL);
        assertEquals(NONE, gestures.up(0, 0, 30));
        assertEquals(NONE, gestures.confirmTap(400));
    }

    @Test
    public void seekingClampsToStartAndKnownDuration() {
        assertEquals(20_000, NativePlayerGestures.seekPosition(30_000, 60_000, false));
        assertEquals(40_000, NativePlayerGestures.seekPosition(30_000, 60_000, true));
        assertEquals(0, NativePlayerGestures.seekPosition(5_000, 60_000, false));
        assertEquals(60_000, NativePlayerGestures.seekPosition(55_000, 60_000, true));
        assertEquals(0, NativePlayerGestures.seekPosition(0, 0, true));
    }

    @Test
    public void unknownDurationAndExtremePositionsNeverOverflow() {
        assertEquals(30_000, NativePlayerGestures.seekPosition(20_000, -1, true));
        assertEquals(30_000, NativePlayerGestures.seekPosition(20_000, Long.MIN_VALUE + 1, true));
        assertEquals(0, NativePlayerGestures.seekPosition(-1, -1, false));
        assertEquals(10_000, NativePlayerGestures.seekPosition(-1, -1, true));
        assertEquals(Long.MAX_VALUE,
                NativePlayerGestures.seekPosition(Long.MAX_VALUE - 1, -1, true));
    }

    private NativePlayerGestures.Action swipe(NativePlayerGestures.Mode mode, float dx, float dy) {
        gestures.down(150, 160, 0, 300, mode);
        gestures.move(150 + dx, 160 + dy);
        return gestures.up(150 + dx, 160 + dy, 200);
    }
}
