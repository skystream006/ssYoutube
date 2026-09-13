package com.skystream.ssyoutube;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Lifecycle-controlled sampling; the activity owns overlay visibility. */
public final class StatsMonitor {
    private static final long STORAGE_INTERVAL_MILLIS = 30_000;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();
    private TextView overlay;
    private boolean enabled;
    private boolean started;
    private boolean destroyed;
    private long generation;
    private ScheduledFuture<?> sampling;

    public StatsMonitor(Context context, TextView overlay) {
        this.context = context.getApplicationContext();
        this.overlay = overlay;
    }

    public synchronized void setEnabled(boolean enabled) {
        if (destroyed || this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        updateSampling();
    }

    public synchronized void onStart() {
        if (destroyed || started) {
            return;
        }
        started = true;
        updateSampling();
    }

    public synchronized void onStop() {
        if (destroyed || !started) {
            return;
        }
        started = false;
        updateSampling();
    }

    public synchronized void onDestroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        stopSampling();
        executor.shutdownNow();
        overlay = null;
    }

    private void updateSampling() {
        stopSampling();
        if (enabled && started && !destroyed) {
            Session session = new Session(generation);
            publish(session.id, StatsValues.MEASURING, StatsValues.MEASURING,
                    StatsValues.MEASURING, StatsValues.MEASURING);
            sampling = executor.scheduleWithFixedDelay(session, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void stopSampling() {
        generation++;
        if (sampling != null) {
            sampling.cancel(true);
            sampling = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    private synchronized boolean isActive(long id) {
        return !destroyed && enabled && started && generation == id
                && !Thread.currentThread().isInterrupted();
    }

    private void publish(long id, long memory, long download, long upload, long storage) {
        synchronized (this) {
            if (!isActive(id)) {
                return;
            }
            mainHandler.post(() -> {
                synchronized (StatsMonitor.this) {
                    if (isActive(id) && overlay != null) {
                        overlay.setText(context.getString(R.string.stats_overlay_format,
                                formatBytes(memory), formatRate(download), formatRate(upload),
                                formatBytes(storage)));
                    }
                }
            });
        }
    }

    private String formatBytes(long bytes) {
        if (bytes == StatsValues.MEASURING) {
            return context.getString(R.string.stats_measuring);
        }
        if (bytes < 0) {
            return context.getString(R.string.stats_unavailable);
        }
        return Formatter.formatShortFileSize(context, bytes);
    }

    private String formatRate(long bytes) {
        return bytes < 0 ? formatBytes(bytes)
                : context.getString(R.string.stats_rate_format, formatBytes(bytes));
    }

    private long memoryBytes() {
        try {
            Debug.MemoryInfo info = new Debug.MemoryInfo();
            Debug.getMemoryInfo(info);
            int pss = info.getTotalPss();
            return pss > 0 ? pss * 1024L : StatsValues.UNAVAILABLE;
        } catch (RuntimeException exception) {
            return StatsValues.UNAVAILABLE;
        }
    }

    private long storageBytes() {
        try {
            List<File> roots = new ArrayList<>();
            addInternalRoots(roots, context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addInternalRoots(roots, context.createDeviceProtectedStorageContext());
            }
            addRoots(roots, context.getExternalFilesDirs(null));
            addRoots(roots, context.getExternalCacheDirs());
            addRoots(roots, context.getExternalMediaDirs());
            addRoots(roots, context.getObbDirs());
            return StatsValues.storageBytes(roots.toArray(new File[0]));
        } catch (RuntimeException exception) {
            return StatsValues.UNAVAILABLE;
        }
    }

    private static void addInternalRoots(List<File> roots, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            roots.add(context.getDataDir());
        } else {
            String dataDir = context.getApplicationInfo().dataDir;
            if (dataDir != null) {
                roots.add(new File(dataDir));
            }
        }
        roots.add(context.getFilesDir());
        roots.add(context.getCacheDir());
        roots.add(context.getCodeCacheDir());
        roots.add(context.getNoBackupFilesDir());
    }

    private static void addRoots(List<File> roots, File[] directories) {
        if (directories != null) {
            Collections.addAll(roots, directories);
        }
    }

    private final class Session implements Runnable {
        private final long id;
        private final StatsValues.RateTracker rates = new StatsValues.RateTracker();
        private long storage = StatsValues.MEASURING;
        private long lastStorageTime = -1;

        private Session(long id) {
            this.id = id;
        }

        @Override
        public void run() {
            if (!isActive(id)) {
                return;
            }
            long memory = memoryBytes();
            if (!isActive(id)) {
                return;
            }
            long download = StatsValues.UNAVAILABLE;
            long upload = StatsValues.UNAVAILABLE;
            try {
                download = TrafficStats.getUidRxBytes(Process.myUid());
                upload = TrafficStats.getUidTxBytes(Process.myUid());
            } catch (RuntimeException ignored) {
                // Unsupported or denied UID counters remain unavailable.
            }
            long now = SystemClock.elapsedRealtime();
            StatsValues.Rates current = rates.sample(download, upload, now);
            if (!isActive(id)) {
                return;
            }
            publish(id, memory, current.download, current.upload, storage);
            if (lastStorageTime < 0 || now - lastStorageTime >= STORAGE_INTERVAL_MILLIS) {
                storage = storageBytes();
                lastStorageTime = SystemClock.elapsedRealtime();
                publish(id, memory, current.download, current.upload, storage);
            }
        }
    }
}
