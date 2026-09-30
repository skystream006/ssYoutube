package com.skystream.ssyoutube;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One native player; fullscreen and miniplayer only change its surrounding layout. */
@OptIn(markerClass = UnstableApi.class)
final class NativePlayerView extends FrameLayout {
    interface Listener {
        void onClose();
        void onMinimize();
        void onToggleFullscreen();
    }

    private static final String STATE_VIDEO = "native.video";
    private static final String STATE_POSITION = "native.position";
    private static final String STATE_WANTS_PLAY = "native.wantsPlay";
    private static final String STATE_EXPLICIT_START = "native.explicitStart";
    private static final String STATE_HAS_POSITION = "native.hasPosition";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private final Runnable confirmSurfaceTap = this::confirmSurfaceTap;
    private final Runnable hideGestureFeedback = this::hideGestureFeedback;
    private final NativePlaybackState state = new NativePlaybackState();
    private final Timeline.Window currentWindow = new Timeline.Window();
    private final ThreadPoolExecutor extractor = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "ssyoutube-extractor");
                thread.setDaemon(true);
                return thread;
            });
    private final MpvPlayer player;
    private final PlayerView playerView;
    private final Listener listener;
    private final NativePlayerGestures gestures;
    private final TextView gestureFeedback;
    private final LinearLayout statusPanel;
    private final ProgressBar progress;
    private final TextView statusText;
    private final TextView heading;
    private final Button retry;
    private final ImageButton minimize;
    private final ImageButton fullscreen;
    private Future<?> pending;
    private ExtractorDownloader.Cancellation cancellation;
    private long generation;
    private long historyContinuityToken;
    private boolean released;
    private boolean changingPlayer;
    private boolean isFullscreen;
    private boolean isMinimized;

    NativePlayerView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        ViewConfiguration configuration = ViewConfiguration.get(context);
        gestures = new NativePlayerGestures(configuration.getScaledTouchSlop(),
                configuration.getScaledDoubleTapSlop(), dp(48),
                ViewConfiguration.getDoubleTapTimeout(), ViewConfiguration.getLongPressTimeout());
        setBackgroundColor(Color.BLACK);
        extractor.allowCoreThreadTimeOut(true);
        player = new MpvPlayer(context);
        playerView = new SurfacePlayerView(context);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        LayoutParams videoLayout = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        videoLayout.topMargin = dp(48);
        addView(playerView, videoLayout);

        gestureFeedback = new TextView(context);
        gestureFeedback.setTextColor(Color.WHITE);
        gestureFeedback.setTextSize(18);
        gestureFeedback.setGravity(Gravity.CENTER);
        gestureFeedback.setPadding(dp(12), dp(8), dp(12), dp(8));
        gestureFeedback.setBackgroundColor(0xC0000000);
        gestureFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gestureFeedback.setVisibility(GONE);
        addView(gestureFeedback, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        statusPanel = new LinearLayout(context);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        statusPanel.setPadding(dp(16), dp(52), dp(16), dp(12));
        statusPanel.setBackgroundColor(0xE6000000);
        progress = new ProgressBar(context);
        statusPanel.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40)));
        statusText = new TextView(context);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusPanel.addView(statusText, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        retry = new Button(context);
        retry.setText(R.string.native_player_retry);
        retry.setOnClickListener(view -> retry());
        statusPanel.addView(retry);
        addView(statusPanel, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        statusPanel.setVisibility(GONE);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xA6000000);
        heading = new TextView(context);
        heading.setText(R.string.native_player_title);
        heading.setTextColor(Color.WHITE);
        heading.setSingleLine(true);
        heading.setPadding(dp(12), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        minimize = button(context, android.R.drawable.ic_menu_revert,
                R.string.native_player_minimize);
        minimize.setOnClickListener(view -> listener.onMinimize());
        toolbar.addView(minimize, new LinearLayout.LayoutParams(dp(48), dp(48)));
        fullscreen = button(context, android.R.drawable.ic_menu_crop,
                R.string.native_player_fullscreen);
        fullscreen.setOnClickListener(view -> listener.onToggleFullscreen());
        toolbar.addView(fullscreen, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton close = button(context, android.R.drawable.ic_menu_close_clear_cancel,
                R.string.native_player_close);
        close.setOnClickListener(view -> listener.onClose());
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addView(toolbar, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP));

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (!changingPlayer && !released && !state.lifecyclePaused && hasVideo()) {
                    state.wantsPlay = playWhenReady;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                historyContinuityToken++;
                setKeepScreenOn(isPlaying && !state.lifecyclePaused && !released);
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition, int reason) {
                historyContinuityToken++;
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
                historyContinuityToken++;
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && !changingPlayer) {
                    state.wantsPlay = false;
                    setKeepScreenOn(false);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                MediaItem item = player.getCurrentMediaItem();
                if (!released && !changingPlayer && hasVideo()
                        && item != null && state.videoId.equals(item.mediaId)) {
                    capturePosition();
                    showError(error);
                }
            }
        });
    }

    void play(String videoId, long startPositionMs) {
        if (released) {
            return;
        }
        NativePlaybackState.Selection selection = state.select(videoId, startPositionMs);
        if (selection == NativePlaybackState.Selection.IGNORE) {
            return;
        }
        if (selection == NativePlaybackState.Selection.SEEK) {
            if (state.prepared) {
                player.seekTo(state.positionMs);
            }
            return;
        }
        cancelExtraction();
        clearPlayer();
        showLoading();
        startExtraction();
    }

    boolean hasVideo() {
        return !released && state.videoId != null;
    }

    String getVideoId() {
        return state.videoId;
    }

    boolean isPlayingForHistory() {
        return !released && !state.lifecyclePaused && state.prepared && !state.failed
                && player.isPlaying() && hasResolvedVideo();
    }

    long getPositionMsForHistory() {
        return player.getCurrentPosition();
    }

    long getDurationMsForHistory() {
        return player.getDuration();
    }

    float getPlaybackSpeedForHistory() {
        return player.getPlaybackParameters().speed;
    }

    long getHistoryContinuityToken() {
        return historyContinuityToken;
    }

    void onPause() {
        if (released || state.lifecyclePaused) {
            return;
        }
        clearGestureUi();
        capturePosition();
        state.lifecyclePaused = true;
        cancelExtraction();
        setPlayerPlayWhenReady(false);
        setKeepScreenOn(false);
    }

    void onResume() {
        if (released) {
            return;
        }
        state.lifecyclePaused = false;
        if (state.prepared) {
            setPlayerPlayWhenReady(state.shouldPlay());
        } else {
            startExtraction();
        }
    }

    void saveState(Bundle outState) {
        capturePosition();
        outState.putString(STATE_VIDEO, state.videoId);
        outState.putLong(STATE_POSITION, state.positionMs);
        outState.putBoolean(STATE_WANTS_PLAY, state.wantsPlay);
        outState.putLong(STATE_EXPLICIT_START, state.explicitStartMs);
        outState.putBoolean(STATE_HAS_POSITION, state.hasPosition);
    }

    void restoreState(Bundle savedState) {
        if (released || savedState == null) {
            return;
        }
        String id = savedState.getString(STATE_VIDEO);
        if (!NativePlaybackState.isVideoId(id)) {
            return;
        }
        stop();
        state.restore(id, savedState.getLong(STATE_POSITION, 0),
                savedState.getLong(STATE_EXPLICIT_START, 0),
                savedState.getBoolean(STATE_WANTS_PLAY, true),
                savedState.getBoolean(STATE_HAS_POSITION, true));
        showLoading();
        // Only identifiers and positions survive recreation; expiring stream URLs never do.
        startExtraction();
    }

    void setFullscreen(boolean value) {
        if (isFullscreen != value) {
            clearGestureUi();
        }
        isFullscreen = value;
        updateWindowControls();
    }

    void setMinimized(boolean value) {
        if (isMinimized != value) {
            clearGestureUi();
        }
        isMinimized = value;
        heading.setVisibility(value ? INVISIBLE : VISIBLE);
        minimize.setVisibility(value ? GONE : VISIBLE);
        statusText.setVisibility(value ? GONE : VISIBLE);
        statusPanel.setPadding(dp(value ? 4 : 16), dp(48), dp(value ? 4 : 16), 0);
        playerView.setShowFastForwardButton(!value);
        playerView.setShowRewindButton(!value);
        updateWindowControls();
    }

    void stop() {
        if (released) {
            return;
        }
        clearGestureUi();
        cancelExtraction();
        clearPlayer();
        state.stop();
        statusPanel.setVisibility(GONE);
        setKeepScreenOn(false);
    }

    void release() {
        if (released) {
            return;
        }
        stop();
        released = true;
        main.removeCallbacksAndMessages(null);
        extractor.shutdownNow();
        playerView.setPlayer(null);
        player.release();
    }

    private void startExtraction() {
        if (released || pending != null || !state.shouldExtract()) {
            return;
        }
        showLoading();
        final long requestGeneration = ++generation;
        final String id = state.videoId;
        final ExtractorDownloader.Cancellation requestCancellation =
                new ExtractorDownloader.Cancellation();
        cancellation = requestCancellation;
        try {
            pending = extractor.submit(() -> {
                try {
                    NativeStreamExtractor.Result result =
                            NativeStreamExtractor.extract(id, requestCancellation);
                    String certificates = MpvPlayer.prepareCertificates(getContext());
                    requestCancellation.check();
                    main.post(() -> {
                        if (!isCurrent(requestGeneration, id)) {
                            return;
                        }
                        pending = null;
                        cancellation = null;
                        try {
                            changingPlayer = true;
                            try {
                                player.setStream(result, id,
                                        state.useLiveDefaultPosition(result.live)
                                                ? C.TIME_UNSET : state.positionMs, certificates);
                                player.setPlayWhenReady(state.shouldPlay());
                                state.prepared = true;
                                player.prepare();
                            } finally {
                                changingPlayer = false;
                            }
                            if (player.getPlayerError() != null) {
                                showError(player.getPlayerError());
                            } else {
                                statusPanel.setVisibility(GONE);
                            }
                        } catch (java.io.IOException | RuntimeException error) {
                            showError(error);
                        }
                    });
                } catch (Exception error) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    main.post(() -> {
                        if (isCurrent(requestGeneration, id)) {
                            pending = null;
                            cancellation = null;
                            showError(error);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException error) {
            cancellation = null;
            showError(error);
        }
    }

    private boolean isCurrent(long requestGeneration, String id) {
        return !released && !state.lifecyclePaused && generation == requestGeneration
                && id.equals(state.videoId);
    }

    private void cancelExtraction() {
        generation++;
        if (cancellation != null) {
            cancellation.cancel();
            cancellation = null;
        }
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        extractor.purge();
        main.removeCallbacksAndMessages(null);
    }

    private void retry() {
        if (!hasVideo()) {
            return;
        }
        cancelExtraction();
        clearPlayer();
        state.failed = false;
        state.prepared = false;
        state.wantsPlay = true;
        startExtraction();
    }

    private void showLoading() {
        clearGestureUi();
        statusText.setText(R.string.native_player_loading);
        progress.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        statusPanel.setVisibility(VISIBLE);
    }

    private void showError(Throwable error) {
        clearGestureUi();
        state.failed = true;
        state.prepared = false;
        setPlayerPlayWhenReady(false);
        setKeepScreenOn(false);
        statusText.setText(R.string.native_player_error);
        progress.setVisibility(GONE);
        retry.setVisibility(VISIBLE);
        statusPanel.setVisibility(VISIBLE);
        Logger.get(getContext()).log("E", "Native playback failed", error);
    }

    private void capturePosition() {
        if (!released && state.prepared) {
            Timeline timeline = player.getCurrentTimeline();
            int index = player.getCurrentMediaItemIndex();
            boolean resolved = index >= 0 && index < timeline.getWindowCount()
                    && !timeline.getWindow(index, currentWindow).isPlaceholder;
            state.capturePosition(player.getCurrentPosition(), resolved);
        }
    }

    private void clearPlayer() {
        changingPlayer = true;
        try {
            player.stop();
            player.clearStream();
        } finally {
            changingPlayer = false;
        }
        setKeepScreenOn(false);
    }

    private void setPlayerPlayWhenReady(boolean playWhenReady) {
        changingPlayer = true;
        try {
            player.setPlayWhenReady(playWhenReady);
        } finally {
            changingPlayer = false;
        }
    }

    private boolean hasResolvedVideo() {
        MediaItem item = player.getCurrentMediaItem();
        Timeline timeline = player.getCurrentTimeline();
        int index = player.getCurrentMediaItemIndex();
        return state.videoId != null && item != null && state.videoId.equals(item.mediaId)
                && index >= 0 && index < timeline.getWindowCount()
                && !timeline.getWindow(index, currentWindow).isPlaceholder
                && state.videoId.equals(currentWindow.mediaItem.mediaId);
    }

    private NativePlayerGestures.Mode gestureMode() {
        return isMinimized ? NativePlayerGestures.Mode.MINIMIZED
                : isFullscreen ? NativePlayerGestures.Mode.FULLSCREEN
                : NativePlayerGestures.Mode.NORMAL;
    }

    private void performGesture(NativePlayerGestures.Action action) {
        if (!hasVideo() || state.lifecyclePaused || statusPanel.getVisibility() == VISIBLE) {
            return;
        }
        switch (action) {
            case SINGLE_TAP:
                playerView.performClick();
                break;
            case EXPAND:
            case ENTER_FULLSCREEN:
            case EXIT_FULLSCREEN:
                listener.onToggleFullscreen();
                break;
            case MINIMIZE:
                listener.onMinimize();
                break;
            case DISMISS:
                listener.onClose();
                break;
            case SEEK_BACK:
            case SEEK_FORWARD:
                boolean forward = action == NativePlayerGestures.Action.SEEK_FORWARD;
                int direction = forward ? 1 : -1;
                if (state.prepared && !state.failed && hasResolvedVideo()
                        && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                    player.seekTo(NativePlayerGestures.seekPosition(player.getCurrentPosition(),
                            player.getDuration(), forward));
                    showGestureFeedback(forward ? R.string.native_player_seek_forward
                            : R.string.native_player_seek_back, direction);
                } else {
                    showGestureFeedback(R.string.native_player_seek_unavailable, direction);
                }
                break;
            case TOGGLE_PLAY:
                if (state.prepared && !state.failed && hasResolvedVideo()
                        && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                    boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
                    boolean play = ended || !player.getPlayWhenReady();
                    if (ended && player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
                        player.seekToDefaultPosition();
                    }
                    state.wantsPlay = play;
                    player.setPlayWhenReady(play);
                    showGestureFeedback(play ? R.string.native_player_playing
                            : R.string.native_player_paused, 0);
                }
                break;
            default:
                break;
        }
    }

    private void showGestureFeedback(int message, int direction) {
        int relativeDirection = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -direction : direction;
        int gravity = Gravity.CENTER_VERTICAL | (relativeDirection < 0 ? Gravity.START
                : relativeDirection > 0 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        LayoutParams layout = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, gravity);
        layout.topMargin = dp(48);
        layout.leftMargin = dp(12);
        layout.rightMargin = dp(12);
        gestureFeedback.setLayoutParams(layout);
        gestureFeedback.setMaxWidth(Math.max(dp(80), playerView.getWidth() / 2 - dp(24)));
        gestureFeedback.setText(message);
        gestureFeedback.setVisibility(VISIBLE);
        gestureHandler.removeCallbacks(hideGestureFeedback);
        gestureHandler.postDelayed(hideGestureFeedback, 800);
    }

    private void hideGestureFeedback() {
        gestureFeedback.setVisibility(GONE);
    }

    private void confirmSurfaceTap() {
        performGesture(gestures.confirmTap(SystemClock.uptimeMillis()));
    }

    private void cancelGestureTracking() {
        gestures.cancel();
        gestureHandler.removeCallbacks(confirmSurfaceTap);
    }

    private void clearGestureUi() {
        cancelGestureTracking();
        gestureHandler.removeCallbacks(hideGestureFeedback);
        hideGestureFeedback();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Cancel before FrameLayout can split pointers between the toolbar and video.
        if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancelGestureTracking();
        } else if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && (event.getY() < playerView.getTop()
                || statusPanel.getVisibility() == VISIBLE)) {
            cancelGestureTracking();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        clearGestureUi();
        super.onDetachedFromWindow();
    }

    private final class SurfacePlayerView extends PlayerView {
        private final Rect controlBounds = new Rect();
        private final int[] rootLocation = new int[2];
        private boolean surfaceTouch;

        SurfacePlayerView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (oldWidth > 0 || oldHeight > 0) {
                clearGestureUi();
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getRootView().getLocationOnScreen(rootLocation);
                surfaceTouch = hasVideo() && !state.lifecyclePaused
                        && statusPanel.getVisibility() != VISIBLE
                        && !touchesControl(findViewById(androidx.media3.ui.R.id.exo_controller),
                                (int) event.getRawX() - rootLocation[0],
                                (int) event.getRawY() - rootLocation[1]);
                if (!surfaceTouch) {
                    cancelGestureTracking();
                }
            }
            if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                cancelGestureTracking();
            }
            // Children get first refusal: buttons, menus and the scrubber retain native dispatch.
            return super.dispatchTouchEvent(event);
        }

        private boolean touchesControl(View view, int x, int y) {
            if (view == null || view.getVisibility() != VISIBLE || view.getAlpha() == 0
                    || !view.getGlobalVisibleRect(controlBounds) || !controlBounds.contains(x, y)) {
                return false;
            }
            if (view instanceof TimeBar || view instanceof TextView || view.isClickable()
                    || view.isLongClickable() || (view.isFocusable() && !(view instanceof ViewGroup))) {
                return true;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    if (touchesControl(group.getChildAt(i), x, y)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!surfaceTouch) {
                return super.onTouchEvent(event);
            }
            if (event.getPointerCount() > 1) {
                cancelGestureTracking();
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureHandler.removeCallbacks(confirmSurfaceTap);
                    performGesture(gestures.down(event.getX(), event.getY(),
                            event.getEventTime(), getWidth(), gestureMode()));
                    break;
                case MotionEvent.ACTION_MOVE:
                    gestures.move(event.getX(), event.getY());
                    break;
                case MotionEvent.ACTION_UP:
                    performGesture(gestures.up(event.getX(), event.getY(), event.getEventTime()));
                    long delay = gestures.tapDelay(SystemClock.uptimeMillis());
                    if (delay >= 0) {
                        gestureHandler.postDelayed(confirmSurfaceTap, delay);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cancelGestureTracking();
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            boolean handled = super.performClick();
            if (isMinimized && hasVideo()) {
                listener.onToggleFullscreen();
                return true;
            }
            return handled;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (isMinimized) {
                info.setContentDescription(getContext().getString(R.string.native_player_expand));
            }
        }
    }

    private void updateWindowControls() {
        fullscreen.setContentDescription(getContext().getString(isMinimized
                ? R.string.native_player_expand : isFullscreen
                ? R.string.native_player_exit_fullscreen : R.string.native_player_fullscreen));
    }

    private ImageButton button(Context context, int icon, int description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setColorFilter(Color.WHITE);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(context.getString(description));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
