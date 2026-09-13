package com.skystream.ssyoutube;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded private log storage, serialized with crash writes and export operations. */
final class LogStore {
    static final int MAX_BYTES = 512 * 1024;
    private final File directory;

    LogStore(File directory) {
        this.directory = directory;
    }

    synchronized void append(String entry) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create log directory");
        }
        File active = new File(directory, "ssyoutube.log");
        File previous = new File(directory, "ssyoutube-previous.log");
        byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Log entry too large");
        }
        if (active.length() + bytes.length > MAX_BYTES) {
            delete(previous);
            if (active.exists() && !active.renameTo(previous)) {
                throw new IOException("Cannot rotate log");
            }
        }
        try (FileOutputStream output = new FileOutputStream(active, true)) {
            output.write(bytes);
        }
    }

    synchronized File snapshot(File destination) throws IOException {
        File active = new File(directory, "ssyoutube.log");
        File previous = new File(directory, "ssyoutube-previous.log");
        if (active.length() == 0 && previous.length() == 0) {
            return null;
        }
        File parent = destination.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create export directory");
        }
        try (FileOutputStream output = new FileOutputStream(destination)) {
            for (File source : new File[] {previous, active}) {
                if (!source.exists()) {
                    continue;
                }
                try (FileInputStream input = new FileInputStream(source)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        return destination;
    }

    synchronized void clear() throws IOException {
        delete(new File(directory, "ssyoutube.log"));
        delete(new File(directory, "ssyoutube-previous.log"));
    }

    private static void delete(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot delete log");
        }
    }
}
