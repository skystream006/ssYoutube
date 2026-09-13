package com.skystream.ssyoutube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Process-wide, opt-in diagnostics. No Activity is retained by the logger. */
final class Logger {
    interface Callback {
        void complete(File file, boolean success);
    }

    interface ReadCallback {
        void complete(String text, boolean success);
    }

    private static Logger instance;
    private final LogStore store;
    private final File exportDirectory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "ssyoutube-log");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean enabled;

    static synchronized Logger get(Context context) {
        if (instance == null) {
            instance = new Logger(context.getApplicationContext());
        }
        return instance;
    }

    private Logger(Context context) {
        store = new LogStore(new File(context.getNoBackupFilesDir(), "logs"));
        exportDirectory = new File(context.getCacheDir(), "shared_logs");
        writer.allowCoreThreadTimeOut(true);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (enabled) {
                    store.append(LogFormat.entry("E", "Uncaught exception", error,
                            error.getStackTrace()));
                }
            } catch (IOException | RuntimeException ignored) {
                // Diagnostics must never prevent the platform crash handler from running.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        if (!value) {
            log("I", "Debug logging disabled", null);
        }
        enabled = value;
        if (value) {
            log("I", "Debug logging enabled", null);
        }
    }

    void log(String level, String message, Throwable error) {
        if (!enabled) {
            return;
        }
        String entry = LogFormat.entry(level, message, error, new Throwable().getStackTrace());
        Log.println("E".equals(level) ? Log.ERROR : Log.DEBUG, "ssYouTube", entry);
        submit(() -> {
            try {
                store.append(entry);
            } catch (IOException | SecurityException ignored) {
                // Logging is best effort and must not interrupt playback.
            }
        }, null);
    }

    void read(ReadCallback callback) {
        submit(() -> {
            try {
                String text = store.read();
                main.post(() -> callback.complete(text, true));
            } catch (IOException | SecurityException error) {
                main.post(() -> callback.complete(null, false));
            }
        }, (file, success) -> callback.complete(null, false));
    }

    void share(Callback callback) {
        submit(() -> {
            try {
                clearExports();
                if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
                    throw new IOException("Cannot create export directory");
                }
                // A fresh URI prevents an earlier recipient's grant from exposing later logs.
                File target = File.createTempFile("ssyoutube-", ".log", exportDirectory);
                File snapshot;
                try {
                    snapshot = store.snapshot(target);
                } catch (IOException | SecurityException error) {
                    target.delete();
                    throw error;
                }
                if (snapshot == null) {
                    target.delete();
                }
                main.post(() -> callback.complete(snapshot, true));
            } catch (IOException | SecurityException error) {
                main.post(() -> callback.complete(null, false));
            }
        }, callback);
    }

    void clear(Callback callback) {
        submit(() -> {
            try {
                store.clear();
                clearExports();
                main.post(() -> callback.complete(null, true));
            } catch (IOException | SecurityException error) {
                main.post(() -> callback.complete(null, false));
            }
        }, callback);
    }

    private void clearExports() throws IOException {
        if (!exportDirectory.exists()) {
            return;
        }
        File[] files = exportDirectory.listFiles();
        if (files == null) {
            throw new IOException("Cannot read export directory");
        }
        for (File file : files) {
            if (!file.delete()) {
                throw new IOException("Cannot delete export");
            }
        }
    }

    private void submit(Runnable task, Callback callback) {
        try {
            writer.execute(task);
        } catch (RejectedExecutionException ignored) {
            if (callback != null) {
                main.post(() -> callback.complete(null, false));
            }
        }
    }
}
