package com.skystream.ssyoutube;

import static com.skystream.ssyoutube.SettingsButtonHold.Action.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsButtonHoldTest {
    @Test
    public void requiresThreeContinuousSecondsThenHidesForExactlyFiveSeconds() {
        SettingsButtonHold hold = new SettingsButtonHold();
        hold.down(100);
        assertEquals(3000, hold.delay(100));
        assertEquals(NONE, hold.tick(3099));
        assertEquals(1, hold.delay(3099));
        assertEquals(HIDE, hold.tick(3100));
        assertEquals(5000, hold.delay(3100));
        assertEquals(NONE, hold.up(3101));
        assertEquals(NONE, hold.tick(8099));
        assertEquals(SHOW, hold.tick(8100));
        assertEquals(-1, hold.delay(8100));
        assertEquals(NONE, hold.tick(9000));
    }

    @Test
    public void shortAndNearlyThreeSecondPressesStillClickExactlyOnce() {
        for (long duration : new long[] {20, 500, 2999}) {
            SettingsButtonHold hold = new SettingsButtonHold();
            hold.down(10);
            assertEquals(CLICK, hold.up(10 + duration));
            assertEquals(NONE, hold.up(4000));
            assertEquals(NONE, hold.tick(5000));
        }
    }

    @Test
    public void releaseAtThresholdHandlesDelayedTimerWithoutClicking() {
        SettingsButtonHold hold = new SettingsButtonHold();
        hold.down(0);
        assertEquals(HIDE, hold.up(3000));
        assertEquals(5000, hold.delay(3000));
        assertEquals(NONE, hold.up(3001));
    }

    @Test
    public void cancellationMovementAndMultiplePointersDoNotAccumulateHoldTime() {
        SettingsButtonHold hold = new SettingsButtonHold();
        hold.down(0);
        hold.cancelPress();
        assertEquals(NONE, hold.tick(4000));
        assertEquals(NONE, hold.up(4000));
        hold.down(5000);
        assertEquals(NONE, hold.tick(7999));
        assertEquals(HIDE, hold.tick(8000));
    }

    @Test
    public void cancellationAfterHidingKeepsTheRestoreDeadlineAndAllowsAnotherHold() {
        SettingsButtonHold hold = new SettingsButtonHold();
        hold.down(0);
        assertEquals(HIDE, hold.tick(3000));
        hold.cancelPress();
        hold.down(4000);
        assertEquals(NONE, hold.up(7000));
        assertEquals(SHOW, hold.tick(9000));
        hold.down(10000);
        assertEquals(HIDE, hold.tick(13000));
        assertEquals(SHOW, hold.tick(18000));
    }

    @Test
    public void recreationPreservesRemainingTimeWithoutHidingIndefinitelyAfterAClockReset() {
        SettingsButtonHold original = new SettingsButtonHold();
        original.down(0);
        original.tick(3000);
        SettingsButtonHold restored = new SettingsButtonHold();
        assertTrue(restored.restore(original.hiddenUntil(), 6000));
        assertEquals(2000, restored.delay(6000));
        assertEquals(SHOW, restored.tick(8000));
        assertFalse(restored.restore(original.hiddenUntil(), 9000));
        assertEquals(-1, restored.delay(9000));
        assertTrue(restored.restore(1_000_000, 1000));
        assertEquals(5000, restored.delay(1000));
    }
}
