package com.skystream.ssyoutube;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;

/**
 * libmpv owns decoding and network playback. Media3 is only the existing controls/timeline adapter.
 * Each source owns its native handle, so callbacks from a replaced source cannot affect its successor.
 */
@UnstableApi
final class MpvPlayer extends SimpleBasePlayer implements SurfaceHolder.Callback {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioManager.OnAudioFocusChangeListener focusListener;
    private final BroadcastReceiver noisyReceiver;
    private final Runnable poll = this::poll;
    private MPV mpv;
    private Observer observer;
    private SurfaceHolder holder;
    private Surface surface;
    private Object output;
    private MpvPlaybackRequest request;
    private MediaItem item;
    private boolean live;
    private boolean loaded;
    private boolean positionKnown;
    private boolean waitingForRestart;
    private boolean playWhenReady;
    private boolean focusHeld;
    private boolean focusSuppressed;
    private boolean released;
    private boolean seekable;
    private boolean buffering;
    private boolean ended;
    private boolean firstFrame;
    private long positionMs;
    private long durationMs = C.TIME_UNSET;
    private int playbackState = STATE_IDLE;
    private int playReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private PlaybackParameters parameters = PlaybackParameters.DEFAULT;
    private PlaybackException error;

    MpvPlayer(Context context) {
        super(Looper.getMainLooper());
        this.context = context.getApplicationContext();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        focusListener = change -> main.post(() -> {
            if (released || !focusHeld) {
                return;
            }
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                focusSuppressed = false;
                applyPause();
                invalidateState();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                pauseForAudioLoss();
            } else {
                focusSuppressed = true;
                applyPause();
                invalidateState();
            }
        });
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    pauseForAudioLoss();
                }
            }
        };
        ContextCompat.registerReceiver(this.context, noisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Copy the library's CA bundle off the UI thread; never disable TLS verification. */
    static synchronized String prepareCertificates(Context context) throws IOException {
        File certificates = new File(context.getCacheDir(), "mpv-0.1.12-cacert.pem");
        if (!certificates.isFile() || certificates.length() == 0) {
            File temporary = File.createTempFile("mpv-ca-", ".pem", context.getCacheDir());
            try {
                try (InputStream source = context.getAssets().open("cacert.pem");
                     FileOutputStream target = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = source.read(buffer)) != -1) {
                        target.write(buffer, 0, count);
                    }
                }
                if (!temporary.renameTo(certificates)) {
                    throw new IOException("Cannot prepare mpv certificates");
                }
            } finally {
                temporary.delete();
            }
        }
        return certificates.getAbsolutePath();
    }

    void setStream(NativeStreamExtractor.Result stream, String id, long startMs,
            String certificates) throws IOException {
        verifyApplicationThread();
        clearStream();
        request = new MpvPlaybackRequest(stream.videoUrl, stream.audioUrl, startMs, certificates);
        item = new MediaItem.Builder().setMediaId(id).build();
        live = stream.live;
        positionMs = Math.max(0, startMs);
        invalidateState();
    }

    void clearStream() {
        verifyApplicationThread();
        closeNativePlayer();
        request = null;
        item = null;
        loaded = false;
        positionKnown = false;
        waitingForRestart = false;
        seekable = false;
        ended = false;
        buffering = false;
        error = null;
        playbackState = STATE_IDLE;
        positionMs = 0;
        durationMs = C.TIME_UNSET;
        videoSize = VideoSize.UNKNOWN;
        abandonFocus();
        invalidateState();
    }

    @Override
    protected State getState() {
        Commands.Builder commands = new Commands.Builder().addAll(
                COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_RELEASE,
                COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_TRACKS,
                COMMAND_GET_METADATA, COMMAND_SET_VIDEO_SURFACE, COMMAND_SET_SPEED_AND_PITCH);
        if (positionKnown && seekable) {
            commands.addAll(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION,
                    COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD);
        }
        State.Builder state = new State.Builder()
                .setAvailableCommands(commands.build())
                .setPlayWhenReady(playWhenReady, playReason)
                .setPlaybackSuppressionReason(focusSuppressed
                        ? PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                        : PLAYBACK_SUPPRESSION_REASON_NONE)
                .setPlaybackState(playbackState)
                .setPlayerError(error)
                .setIsLoading(playbackState == STATE_BUFFERING)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setContentPositionMs(positionMs)
                .setContentBufferedPositionMs(PositionSupplier.getConstant(positionMs))
                .setVideoSize(videoSize)
                .setPlaybackParameters(parameters)
                .setNewlyRenderedFirstFrame(firstFrame);
        firstFrame = false;
        if (item != null) {
            MediaItemData.Builder data = new MediaItemData.Builder(item.mediaId)
                    .setMediaItem(item)
                    .setIsPlaceholder(!positionKnown)
                    .setIsSeekable(positionKnown && seekable)
                    .setIsDynamic(live)
                    .setDurationUs(durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs * 1000);
            if (live) {
                data.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().build());
            }
            if (loaded && videoSize.width > 0 && videoSize.height > 0) {
                TrackGroup video = new TrackGroup(new Format.Builder()
                        .setSampleMimeType("video/unknown")
                        .setWidth(videoSize.width).setHeight(videoSize.height).build());
                data.setTracks(new Tracks(ImmutableList.of(new Tracks.Group(video, false,
                        new int[]{C.FORMAT_HANDLED}, new boolean[]{true}))));
            }
            state.setPlaylist(ImmutableList.of(data.build())).setCurrentMediaItemIndex(0);
        }
        return state.build();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        if (request != null && mpv == null) {
            try {
                MPV instance = new MPV();
                mpv = instance;
                instance.create(context);
                IOException configurationError = null;
                for (Map.Entry<String, String> option : request.options.entrySet()) {
                    if (instance.setOptionString(option.getKey(), option.getValue()) < 0) {
                        configurationError = new IOException("Unsupported mpv playback option");
                    }
                }
                instance.setOptionString("pause", "yes");
                instance.init();
                if (configurationError != null) {
                    throw configurationError;
                }
                observer = new Observer(instance);
                instance.addObserver(observer);
                instance.setPropertyDouble("speed", parameters.speed);
                playbackState = STATE_BUFFERING;
                waitingForRestart = true;
                attachSurface();
                request.load(command -> instance.commandNode(command) != null);
                updateFocus();
                applyPause();
                main.post(poll);
            } catch (IOException | RuntimeException | LinkageError failure) {
                fail(failure);
            }
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean play) {
        playWhenReady = play;
        playReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
        updateFocus();
        applyPause();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters value) {
        parameters = new PlaybackParameters(value.speed);
        if (mpv != null) {
            mpv.setPropertyDouble("speed", value.speed);
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int index, long position, int command) {
        if (mpv != null && loaded && seekable) {
            long target = position == C.TIME_UNSET ? 0 : Math.max(0, position);
            if (durationMs != C.TIME_UNSET) {
                target = Math.min(target, durationMs);
            }
            mpv.command("seek", MpvPlaybackRequest.seconds(target), "absolute+exact");
            positionMs = target;
            ended = false;
            waitingForRestart = true;
            buffering = true;
            playbackState = STATE_BUFFERING;
            updateFocus();
            applyPause();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        closeNativePlayer();
        loaded = false;
        playbackState = STATE_IDLE;
        abandonFocus();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        released = true;
        clearOutput();
        closeNativePlayer();
        abandonFocus();
        context.unregisterReceiver(noisyReceiver);
        main.removeCallbacksAndMessages(null);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
        clearOutput();
        output = videoOutput;
        if (videoOutput instanceof SurfaceView) {
            holder = ((SurfaceView) videoOutput).getHolder();
        } else if (videoOutput instanceof SurfaceHolder) {
            holder = (SurfaceHolder) videoOutput;
        } else if (videoOutput instanceof Surface) {
            surface = (Surface) videoOutput;
        }
        if (holder != null) {
            holder.addCallback(this);
            surface = holder.getSurface();
        }
        attachSurface();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleClearVideoOutput(Object videoOutput) {
        if (videoOutput == null || videoOutput == output) {
            clearOutput();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        attachSurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mpv != null) {
            mpv.setPropertyString("android-surface-size", width + "x" + height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        detachSurface();
        surface = null;
    }

    private void attachSurface() {
        if (mpv != null && surface != null && surface.isValid()) {
            mpv.attachSurface(surface);
            mpv.setPropertyString("vo", "gpu");
            mpv.setPropertyString("force-window", "yes");
            if (holder != null) {
                mpv.setPropertyString("android-surface-size",
                        holder.getSurfaceFrame().width() + "x" + holder.getSurfaceFrame().height());
            }
        }
    }

    private void detachSurface() {
        if (mpv != null) {
            mpv.setPropertyString("force-window", "no");
            mpv.setPropertyString("vo", "null");
            mpv.detachSurface();
        }
    }

    private void clearOutput() {
        detachSurface();
        if (holder != null) {
            holder.removeCallback(this);
        }
        holder = null;
        surface = null;
        output = null;
    }

    private void poll() {
        if (released || mpv == null || error != null) {
            return;
        }
        if (loaded) {
            long position = MpvPlaybackRequest.milliseconds(mpv.getPropertyDouble("time-pos"));
            long duration = MpvPlaybackRequest.milliseconds(mpv.getPropertyDouble("duration"));
            if (position >= 0) {
                positionMs = position;
                positionKnown = true;
            }
            durationMs = duration < 0 ? C.TIME_UNSET : duration;
            seekable = Boolean.TRUE.equals(mpv.getPropertyBoolean("seekable"));
            buffering = waitingForRestart || !positionKnown
                    || Boolean.TRUE.equals(mpv.getPropertyBoolean("paused-for-cache"))
                    || Boolean.TRUE.equals(mpv.getPropertyBoolean("seeking"));
            ended = Boolean.TRUE.equals(mpv.getPropertyBoolean("eof-reached"));
            Integer width = mpv.getPropertyInt("dwidth");
            Integer height = mpv.getPropertyInt("dheight");
            if (width != null && height != null && width > 0 && height > 0) {
                videoSize = new VideoSize(width, height);
            }
            playbackState = ended ? STATE_ENDED : buffering ? STATE_BUFFERING : STATE_READY;
            if (ended) {
                abandonFocus();
            }
            invalidateState();
        }
        main.postDelayed(poll, 250);
    }

    private void updateFocus() {
        if (playWhenReady && mpv != null && !focusHeld) {
            focusHeld = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            focusSuppressed = !focusHeld;
        } else if (!playWhenReady) {
            abandonFocus();
        }
    }

    private void abandonFocus() {
        if (focusHeld) {
            audio.abandonAudioFocus(focusListener);
        }
        focusHeld = false;
        focusSuppressed = false;
    }

    private void applyPause() {
        if (mpv != null) {
            mpv.setPropertyBoolean("pause", !playWhenReady || focusSuppressed);
        }
    }

    private void pauseForAudioLoss() {
        if (!released) {
            playWhenReady = false;
            playReason = PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS;
            abandonFocus();
            applyPause();
            invalidateState();
        }
    }

    private void fail(Throwable failure) {
        playbackState = STATE_IDLE;
        error = new PlaybackException("mpv playback failed", failure,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        applyPauseAfterError();
        invalidateState();
    }

    private void applyPauseAfterError() {
        if (mpv != null && mpv.isInitialized()) {
            mpv.setPropertyBoolean("pause", true);
        }
        abandonFocus();
    }

    private void closeNativePlayer() {
        main.removeCallbacks(poll);
        if (mpv != null) {
            MPV old = mpv;
            if (observer != null) {
                old.removeObserver(observer);
            }
            detachSurface();
            mpv = null;
            observer = null;
            old.destroy();
        }
    }

    private final class Observer implements MPV.EventObserver {
        private final MPV source;

        Observer(MPV source) {
            this.source = source;
        }

        @Override
        public void event(int id, MPVNode data) {
            // Never call JNI or wait for the UI thread from a native callback.
            main.post(() -> {
                if (released || mpv != source) {
                    return;
                }
                if (id == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                    loaded = true;
                    main.removeCallbacks(poll);
                    poll();
                } else if (id == MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART) {
                    waitingForRestart = false;
                    firstFrame = true;
                    main.removeCallbacks(poll);
                    poll();
                } else if (id == MPV.mpvEvent.MPV_EVENT_END_FILE) {
                    MPVNode reason = data.get("reason");
                    if (reason != null && "error".equals(reason.asString())) {
                        fail(new IOException("mpv network playback failed"));
                    }
                }
            });
        }

        @Override public void eventProperty(String name) { }
        @Override public void eventProperty(String name, long value) { }
        @Override public void eventProperty(String name, boolean value) { }
        @Override public void eventProperty(String name, String value) { }
        @Override public void eventProperty(String name, double value) { }
        @Override public void eventProperty(String name, MPVNode value) { }
    }
}
