package com.skystream.ssyoutube;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Android-independent calculations used by the statistics overlay. */
public final class StatsValues {
    public static final long UNAVAILABLE = -1;
    public static final long MEASURING = -2;

    private StatsValues() {
    }

    public static long bytesPerSecond(long previous, long current, long elapsedMillis) {
        if (previous < 0 || current < previous || elapsedMillis <= 0) {
            return UNAVAILABLE;
        }
        return (long) ((current - previous) * (1000.0 / elapsedMillis));
    }

    public static final class Rates {
        public final long download;
        public final long upload;

        private Rates(long download, long upload) {
            this.download = download;
            this.upload = upload;
        }
    }

    public static final class RateTracker {
        private long previousTime = UNAVAILABLE;
        private long previousDownload = UNAVAILABLE;
        private long previousUpload = UNAVAILABLE;

        public Rates sample(long download, long upload, long elapsedRealtimeMillis) {
            long downloadRate;
            long uploadRate;
            if (elapsedRealtimeMillis < 0) {
                reset();
                return new Rates(UNAVAILABLE, UNAVAILABLE);
            }
            if (previousTime < 0) {
                downloadRate = download < 0 ? UNAVAILABLE : MEASURING;
                uploadRate = upload < 0 ? UNAVAILABLE : MEASURING;
            } else {
                long elapsed = elapsedRealtimeMillis - previousTime;
                downloadRate = bytesPerSecond(previousDownload, download, elapsed);
                uploadRate = bytesPerSecond(previousUpload, upload, elapsed);
            }
            previousTime = elapsedRealtimeMillis;
            previousDownload = download;
            previousUpload = upload;
            return new Rates(downloadRate, uploadRate);
        }

        public void reset() {
            previousTime = UNAVAILABLE;
            previousDownload = UNAVAILABLE;
            previousUpload = UNAVAILABLE;
        }
    }

    /**
     * Counts data and cache together, ignoring absent optional roots and symbolic links.
     * Any interrupted or failed traversal invalidates the entire result.
     */
    public static long storageBytes(File... roots) {
        if (roots == null || Thread.currentThread().isInterrupted()) {
            return UNAVAILABLE;
        }
        try {
            ArrayDeque<File> pending = new ArrayDeque<>();
            for (File root : roots) {
                if (root != null && root.exists()) {
                    pending.add(root);
                }
            }
            Set<String> visited = new HashSet<>();
            long total = 0;
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    return UNAVAILABLE;
                }
                File file = pending.removeLast();
                if (!file.exists()) {
                    continue;
                }
                File parent = file.getAbsoluteFile().getParentFile();
                File resolvedParent = parent == null ? file.getAbsoluteFile()
                        : new File(parent.getCanonicalFile(), file.getName());
                File canonical = file.getCanonicalFile();
                // Resolve parent aliases (e.g. Android's /data/user/0), not leaf symlinks.
                if (!resolvedParent.equals(canonical) || !visited.add(canonical.getPath())) {
                    continue;
                }
                if (!file.canRead()) {
                    return UNAVAILABLE;
                }
                if (file.isDirectory()) {
                    File[] children = file.listFiles();
                    if (children == null) {
                        return UNAVAILABLE;
                    }
                    for (File child : children) {
                        pending.add(child);
                    }
                } else if (file.isFile()) {
                    long length = file.length();
                    if (length < 0 || Long.MAX_VALUE - total < length) {
                        return UNAVAILABLE;
                    }
                    total += length;
                } else {
                    return UNAVAILABLE;
                }
            }
            return Thread.currentThread().isInterrupted() ? UNAVAILABLE : total;
        } catch (IOException | SecurityException exception) {
            return UNAVAILABLE;
        }
    }
}
