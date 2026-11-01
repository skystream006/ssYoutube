package com.skystream.ssyoutube;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Process/CPU protection for the activity's player; never retains an activity or media URL. */
public final class BackgroundPlaybackService extends Service {
    static final String EVENT = "com.skystream.ssyoutube.BACKGROUND_PLAYBACK_EVENT";
    static final String TOKEN = "token";
    static final String STATE = "state";
    static final String PLAYING = "playing";
    static final String START = "start";
    static final String UPDATE = "update";
    static final String PAUSE = "pause";
    static final String STOP = "stop";
    static final String STOPPED = "stopped";
    static final String UNAVAILABLE = "unavailable";
    static final String REPLACED = "replaced";
    static final String OPEN_PLAYER = "com.skystream.ssyoutube.OPEN_PLAYER";
    private static final String CHANNEL = "background_playback";
    private static final int NOTIFICATION_ID = 2;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private long token;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (PAUSE.equals(intent.getAction()) || STOP.equals(intent.getAction())) {
            if (token != 0 && token == intent.getLongExtra(TOKEN, 0)) {
                if (PAUSE.equals(intent.getAction())) {
                    requestPause();
                } else {
                    stopSelf(startId);
                }
            } else if (token == 0) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        long requestedToken = intent.getLongExtra(TOKEN, 0);
        if (START.equals(intent.getAction())) {
            if (token != 0 && token != requestedToken) {
                sendEvent(REPLACED);
            }
            token = requestedToken;
        } else if (UPDATE.equals(intent.getAction())) {
            if (token != requestedToken) {
                if (token == 0) {
                    token = requestedToken;
                    sendEvent(UNAVAILABLE);
                    stopSelf(startId);
                }
                return START_NOT_STICKY;
            }
        } else {
            Logger.get(this).log("W", "Unknown background playback service command", null);
            if (token == 0) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        try {
            startPlaybackProtection(intent.getBooleanExtra(PLAYING, false));
        } catch (IllegalStateException | SecurityException error) {
            Logger.get(this).log("E", "Background playback service unavailable", error);
            sendEvent(UNAVAILABLE);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("WakelockTimeout") // Released with the foreground service, including task removal.
    private void startPlaybackProtection(boolean playing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    getString(R.string.background_audio_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        if (session == null) {
            session = new MediaSession(this, "ssYouTube background audio");
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onPause() {
                    requestPause();
                }

                @Override
                public void onStop() {
                    requestPause();
                }
            });
            session.setActive(true);
        }
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_BUFFERING,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1 : 0)
                .build());
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setAction(OPEN_PLAYER)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 1,
                new Intent(this, BackgroundPlaybackService.class).setAction(PAUSE)
                        .putExtra(TOKEN, token),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.background_audio_notification))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.pause_audio), pause)
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0))
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (wakeLock == null) {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ssyoutube:background-audio");
            wakeLock.acquire();
        }
    }

    private void requestPause() {
        sendEvent(PAUSE);
        stopSelf();
    }

    private void sendEvent(String state) {
        sendBroadcast(new Intent(EVENT).setPackage(getPackageName())
                .putExtra(TOKEN, token).putExtra(STATE, state));
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        requestPause();
    }

    @Override
    public void onDestroy() {
        sendEvent(STOPPED);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        stopForeground(true);
        super.onDestroy();
    }
}
