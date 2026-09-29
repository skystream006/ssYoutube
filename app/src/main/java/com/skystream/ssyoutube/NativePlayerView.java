package com.skystream.ssyoutube;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

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
    private final NativePlaybackState state = new NativePlaybackState();
    private final Timeline.Window currentWindow = new Timeline.Window();
    private final ThreadPoolExecutor extractor = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "ssyoutube-extractor");
                thread.setDaemon(true);
                return thread;
            });
    private final ExoPlayer player;
    private final PlayerView playerView;
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
    private boolean released;
    private boolean changingPlayer;
    private boolean isFullscreen;
    private boolean isMinimized;

    NativePlayerView(Context context, Listener listener) {
        super(context);
        setBackgroundColor(Color.BLACK);
        extractor.allowCoreThreadTimeOut(true);
        player = new ExoPlayer.Builder(context).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                .buildUpon().setMaxVideoSize(1920, 1080).build());
        playerView = new PlayerView(context);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        LayoutParams videoLayout = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        videoLayout.topMargin = dp(48);
        addView(playerView, videoLayout);

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
                setKeepScreenOn(isPlaying && !state.lifecyclePaused && !released);
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

    void onPause() {
        if (released || state.lifecyclePaused) {
            return;
        }
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
        isFullscreen = value;
        updateWindowControls();
    }

    void setMinimized(boolean value) {
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
                    requestCancellation.check();
                    main.post(() -> {
                        if (!isCurrent(requestGeneration, id)) {
                            return;
                        }
                        pending = null;
                        cancellation = null;
                        try {
                            MediaSource source = mediaSource(result, id);
                            changingPlayer = true;
                            try {
                                player.setMediaSource(source,
                                        state.useLiveDefaultPosition(result.live)
                                                ? C.TIME_UNSET : state.positionMs);
                                player.setPlayWhenReady(state.shouldPlay());
                                state.prepared = true;
                                player.prepare();
                            } finally {
                                changingPlayer = false;
                            }
                            statusPanel.setVisibility(GONE);
                        } catch (RuntimeException error) {
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

    private MediaSource mediaSource(NativeStreamExtractor.Result result, String id) {
        DataSource.Factory transport = NativeHttpsDataSource::new;
        MediaItem video = new MediaItem.Builder().setMediaId(id).setUri(result.videoUrl)
                .setMimeType(result.videoMime).build();
        if (result.kind == NativeStreamExtractor.Kind.HLS) {
            return new HlsMediaSource.Factory(transport).createMediaSource(video);
        }
        if (result.kind == NativeStreamExtractor.Kind.DASH) {
            return new DashMediaSource.Factory(transport).createMediaSource(video);
        }
        MediaSource videoSource = new ProgressiveMediaSource.Factory(transport)
                .createMediaSource(video);
        if (result.audioUrl == null) {
            return videoSource;
        }
        MediaItem audio = new MediaItem.Builder().setMediaId(id).setUri(result.audioUrl)
                .setMimeType(result.audioMime).build();
        return new MergingMediaSource(true, videoSource,
                new ProgressiveMediaSource.Factory(transport).createMediaSource(audio));
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
        statusText.setText(R.string.native_player_loading);
        progress.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        statusPanel.setVisibility(VISIBLE);
    }

    private void showError(Throwable error) {
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
            player.clearMediaItems();
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
