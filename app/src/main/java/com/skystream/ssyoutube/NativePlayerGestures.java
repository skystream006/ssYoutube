package com.skystream.ssyoutube;

/** Single-pointer video-surface gestures, independent of Android event dispatch. */
final class NativePlayerGestures {
    enum Mode { NORMAL, FULLSCREEN, MINIMIZED }
    enum Action {
        NONE, SINGLE_TAP, SEEK_BACK, SEEK_FORWARD, TOGGLE_PLAY,
        EXPAND, ENTER_FULLSCREEN, EXIT_FULLSCREEN, MINIMIZE, DISMISS
    }

    private final float touchSlop;
    private final float doubleTapSlop;
    private final float swipeDistance;
    private final long doubleTapTimeout;
    private final long tapTimeout;
    private boolean active;
    private boolean moved;
    private boolean doubleTap;
    private float downX;
    private float downY;
    private float width;
    private long downTime;
    private Mode mode;
    private boolean pendingTap;
    private float tapX;
    private float tapY;
    private float tapWidth;
    private long tapUpTime;
    private Mode tapMode;

    NativePlayerGestures(float touchSlop, float doubleTapSlop, float swipeDistance,
            long doubleTapTimeout, long tapTimeout) {
        this.touchSlop = touchSlop;
        this.doubleTapSlop = doubleTapSlop;
        this.swipeDistance = swipeDistance;
        this.doubleTapTimeout = doubleTapTimeout;
        this.tapTimeout = tapTimeout;
    }

    Action down(float x, float y, long time, float surfaceWidth, Mode windowMode) {
        boolean matchesTap = pendingTap && time >= tapUpTime
                && time - tapUpTime <= doubleTapTimeout && windowMode == tapMode
                && surfaceWidth == tapWidth
                && distanceSquared(x - tapX, y - tapY) <= doubleTapSlop * doubleTapSlop
                && tapZone(x, surfaceWidth) == tapZone(tapX, tapWidth);
        Action previous = pendingTap && !matchesTap ? Action.SINGLE_TAP : Action.NONE;
        pendingTap = false;
        active = surfaceWidth > 0;
        moved = false;
        doubleTap = matchesTap;
        downX = x;
        downY = y;
        downTime = time;
        width = surfaceWidth;
        mode = windowMode;
        return previous;
    }

    void move(float x, float y) {
        if (active && distanceSquared(x - downX, y - downY) > touchSlop * touchSlop) {
            moved = true;
        }
    }

    Action up(float x, float y, long time) {
        if (!active) {
            return Action.NONE;
        }
        move(x, y);
        active = false;
        if (moved) {
            doubleTap = false;
            return swipe(x - downX, y - downY, mode, swipeDistance);
        }
        if (time < downTime || time - downTime > tapTimeout) {
            doubleTap = false;
            return Action.NONE;
        }
        if (doubleTap) {
            doubleTap = false;
            // A miniplayer tap always expands rather than seeking in a tiny target.
            return mode == Mode.MINIMIZED ? Action.SINGLE_TAP : tapZone(downX, width);
        }
        pendingTap = true;
        tapX = downX;
        tapY = downY;
        tapWidth = width;
        tapUpTime = time;
        tapMode = mode;
        return Action.NONE;
    }

    Action confirmTap(long time) {
        if (!pendingTap || active || time - tapUpTime < doubleTapTimeout) {
            return Action.NONE;
        }
        pendingTap = false;
        return Action.SINGLE_TAP;
    }

    long tapDelay(long time) {
        return pendingTap ? Math.max(0, doubleTapTimeout - (time - tapUpTime)) : -1;
    }

    void cancel() {
        active = false;
        pendingTap = false;
        doubleTap = false;
        moved = false;
    }

    static Action swipe(float dx, float dy, Mode mode, float minimumDistance) {
        if (Math.abs(dy) < minimumDistance || Math.abs(dy) < Math.abs(dx) * 1.5f) {
            return Action.NONE;
        }
        if (dy < 0) {
            return mode == Mode.MINIMIZED ? Action.EXPAND
                    : mode == Mode.NORMAL ? Action.ENTER_FULLSCREEN : Action.NONE;
        }
        return mode == Mode.FULLSCREEN ? Action.EXIT_FULLSCREEN
                : mode == Mode.MINIMIZED ? Action.DISMISS : Action.MINIMIZE;
    }

    static Action tapZone(float x, float width) {
        if (width <= 0 || x < 0 || x > width) {
            return Action.NONE;
        }
        return x < width / 3f ? Action.SEEK_BACK
                : x > width * 2f / 3f ? Action.SEEK_FORWARD : Action.TOGGLE_PLAY;
    }

    static long seekPosition(long positionMs, long durationMs, boolean forward) {
        long position = Math.max(0, positionMs);
        long target = forward
                ? position > Long.MAX_VALUE - 10_000 ? Long.MAX_VALUE : position + 10_000
                : Math.max(0, position - 10_000);
        return durationMs >= 0 ? Math.min(durationMs, target) : target;
    }

    private static float distanceSquared(float x, float y) {
        return x * x + y * y;
    }
}
