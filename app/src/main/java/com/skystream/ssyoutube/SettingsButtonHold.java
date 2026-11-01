package com.skystream.ssyoutube;

/** Timing only; the activity owns touch dispatch and cancellable callbacks. */
final class SettingsButtonHold {
    static final long HOLD_MS = 3_000;
    static final long HIDE_MS = 5_000;
    enum Action { NONE, CLICK, HIDE, SHOW }

    private long pressedAt = -1;
    private long hiddenUntil = -1;

    void down(long now) {
        if (hiddenUntil < 0) {
            pressedAt = now;
        }
    }

    void cancelPress() {
        pressedAt = -1;
    }

    long hiddenUntil() {
        return hiddenUntil;
    }

    boolean restore(long deadline, long now) {
        pressedAt = -1;
        hiddenUntil = deadline > now ? Math.min(deadline, now + HIDE_MS) : -1;
        return hiddenUntil >= 0;
    }

    Action up(long now) {
        if (pressedAt < 0) {
            return Action.NONE;
        }
        Action action = tick(now);
        pressedAt = -1;
        return action == Action.HIDE ? action : Action.CLICK;
    }

    Action tick(long now) {
        if (hiddenUntil >= 0 && now >= hiddenUntil) {
            hiddenUntil = -1;
            return Action.SHOW;
        }
        if (pressedAt >= 0 && now - pressedAt >= HOLD_MS) {
            pressedAt = -1;
            hiddenUntil = now + HIDE_MS;
            return Action.HIDE;
        }
        return Action.NONE;
    }

    long delay(long now) {
        if (hiddenUntil >= 0) {
            return Math.max(0, hiddenUntil - now);
        }
        return pressedAt < 0 ? -1 : Math.max(0, pressedAt + HOLD_MS - now);
    }
}
