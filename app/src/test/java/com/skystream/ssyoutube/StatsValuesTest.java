package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StatsValuesTest {
    private Path directory;

    @Before
    public void createDirectory() throws IOException {
        directory = Files.createDirectory(
                new File(".stats-values-test-" + UUID.randomUUID()).toPath()).toAbsolutePath();
    }

    @After
    public void removeDirectory() throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            Path[] entries = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path entry : entries) {
                Files.delete(entry);
            }
        }
    }

    @Test
    public void ratesUseActualElapsedTime() {
        assertEquals(600, StatsValues.bytesPerSecond(100, 1000, 1500));
        assertEquals(1800, StatsValues.bytesPerSecond(100, 1000, 500));
        assertEquals(0, StatsValues.bytesPerSecond(100, 100, 1000));
        assertEquals(0, StatsValues.bytesPerSecond(0, 1, 2000));
    }

    @Test
    public void ratesRejectUnsupportedCountersResetsAndInvalidTime() {
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.bytesPerSecond(-1, 100, 1000));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.bytesPerSecond(100, -1, 1000));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.bytesPerSecond(100, 99, 1000));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.bytesPerSecond(0, 100, 0));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.bytesPerSecond(0, 100, -1000));
    }

    @Test
    public void ratesDoNotOverflowForLargeCounters() {
        assertEquals(Long.MAX_VALUE, StatsValues.bytesPerSecond(0, Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, StatsValues.bytesPerSecond(0, Long.MAX_VALUE, 1000));
        assertEquals(1000, StatsValues.bytesPerSecond(Long.MAX_VALUE - 1000,
                Long.MAX_VALUE, 1000));
    }

    @Test
    public void trackerStartsMeasuringThenSamplesBothDirections() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        assertRates(StatsValues.MEASURING, StatsValues.MEASURING, tracker.sample(100, 200, 0));
        assertRates(600, 1200, tracker.sample(1000, 2000, 1500));
        assertRates(0, 0, tracker.sample(1000, 2000, 2000));
    }

    @Test
    public void trackerRebaselinesAfterCounterResetIndependently() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        tracker.sample(1000, 1000, 0);
        assertRates(StatsValues.UNAVAILABLE, 100, tracker.sample(10, 1100, 1000));
        assertRates(100, StatsValues.UNAVAILABLE, tracker.sample(110, 0, 2000));
        assertRates(100, 50, tracker.sample(210, 50, 3000));
    }

    @Test
    public void trackerDoesNotTurnUnsupportedCountersIntoTraffic() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        assertRates(StatsValues.UNAVAILABLE, StatsValues.MEASURING, tracker.sample(-1, 0, 0));
        assertRates(StatsValues.UNAVAILABLE, 100, tracker.sample(-1, 100, 1000));
        assertRates(StatsValues.UNAVAILABLE, StatsValues.UNAVAILABLE,
                tracker.sample(9999, -1, 2000));
        assertRates(100, StatsValues.UNAVAILABLE, tracker.sample(10099, 500, 3000));
        assertRates(100, 100, tracker.sample(10199, 600, 4000));
    }

    @Test
    public void trackerRebaselinesAfterNonIncreasingTime() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        tracker.sample(100, 100, 1000);
        assertRates(StatsValues.UNAVAILABLE, StatsValues.UNAVAILABLE,
                tracker.sample(200, 200, 1000));
        assertRates(100, 100, tracker.sample(300, 300, 2000));
        assertRates(StatsValues.UNAVAILABLE, StatsValues.UNAVAILABLE,
                tracker.sample(400, 400, 1500));
        assertRates(100, 100, tracker.sample(500, 500, 2500));
    }

    @Test
    public void trackerResetExcludesTrafficWhileStopped() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        tracker.sample(0, 0, 0);
        tracker.sample(100, 100, 1000);
        tracker.reset();
        assertRates(StatsValues.MEASURING, StatsValues.MEASURING,
                tracker.sample(100000, 100000, 10000));
        assertRates(100, 200, tracker.sample(100100, 100200, 11000));
    }

    @Test
    public void trackerRejectsNegativeTimeAndCanRecover() {
        StatsValues.RateTracker tracker = new StatsValues.RateTracker();
        tracker.sample(0, 0, 0);
        assertRates(StatsValues.UNAVAILABLE, StatsValues.UNAVAILABLE, tracker.sample(1, 1, -1));
        assertRates(StatsValues.MEASURING, StatsValues.MEASURING, tracker.sample(2, 2, 0));
        assertRates(1, 1, tracker.sample(3, 3, 1000));
    }

    @Test
    public void storageIncludesNestedDataAndCacheOnlyOnce() throws IOException {
        File data = file("data/files/video", 31);
        File cache = file("data/cache/image", 17);
        File external = file("external/files/video", 13);
        assertEquals(61, StatsValues.storageBytes(directory.resolve("data").toFile(),
                data.getParentFile(), cache.getParentFile(), external.getParentFile(),
                directory.toFile(), directory.toFile()));
    }

    @Test
    public void storageDeduplicatesChildrenListedBeforeParents() throws IOException {
        File file = file("cache/image", 31);
        assertEquals(31, StatsValues.storageBytes(file, file.getParentFile(), directory.toFile()));
    }

    @Test
    public void storageAcceptsEmptyMissingAndNullOptionalRoots() {
        assertEquals(0, StatsValues.storageBytes());
        assertEquals(0, StatsValues.storageBytes(directory.toFile(), null,
                directory.resolve("missing").toFile()));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes((File[]) null));
    }

    @Test
    public void storageExcludesFileDirectoryAndBrokenSymbolicLinks() throws IOException {
        File data = file("data/video", 23);
        file("outside/video", 101);
        Path root = directory.resolve("data");
        Files.createSymbolicLink(root.resolve("file-link"), data.toPath());
        Files.createSymbolicLink(root.resolve("directory-link"), directory.resolve("outside"));
        Files.createSymbolicLink(root.resolve("cycle"), root);
        Files.createSymbolicLink(root.resolve("broken"), directory.resolve("missing"));
        assertEquals(23, StatsValues.storageBytes(root.toFile(), root.resolve("file-link").toFile(),
                root.resolve("directory-link").toFile()));
    }

    @Test
    public void storageAcceptsAnAliasedParentLikeAndroidDataDirectories() throws IOException {
        file("real/app/data", 23);
        Files.createSymbolicLink(directory.resolve("alias"), directory.resolve("real"));
        assertEquals(23, StatsValues.storageBytes(directory.resolve("alias/app").toFile(),
                directory.resolve("real/app").toFile()));
    }

    @Test
    public void storageRejectsUnreadableDirectoriesRatherThanPartialTotals() throws IOException {
        File good = file("good", 17);
        File unreadable = new SyntheticFile("unreadable", 0) {
            @Override
            public boolean canRead() {
                return false;
            }
        };
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(good, unreadable));
    }

    @Test
    public void storageRejectsFailedDirectoryListing() {
        File failed = new SyntheticFile("failed", 0) {
            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public File[] listFiles() {
                return null;
            }
        };
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(failed));
    }

    @Test
    public void storageRejectsCanonicalPathAndSecurityErrors() {
        File canonicalFailure = new SyntheticFile("canonical-failure", 0) {
            @Override
            public File getCanonicalFile() throws IOException {
                throw new IOException("unavailable");
            }
        };
        File securityFailure = new SyntheticFile("security-failure", 0) {
            @Override
            public boolean exists() {
                throw new SecurityException("unavailable");
            }
        };
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(canonicalFailure));
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(securityFailure));
    }

    @Test
    public void storageRejectsOverflow() {
        assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(
                new SyntheticFile("huge", Long.MAX_VALUE), new SyntheticFile("one", 1)));
    }

    @Test
    public void storageRejectsInterruptionWithoutClearingFlag() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(directory.toFile()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void storageRejectsInterruptionDuringTraversal() {
        File interrupted = new SyntheticFile("interrupted", 10) {
            @Override
            public long length() {
                Thread.currentThread().interrupt();
                return 10;
            }
        };
        try {
            assertEquals(StatsValues.UNAVAILABLE, StatsValues.storageBytes(interrupted));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private File file(String name, int bytes) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[bytes]);
        return path.toFile();
    }

    private static void assertRates(long download, long upload, StatsValues.Rates rates) {
        assertEquals(download, rates.download);
        assertEquals(upload, rates.upload);
    }

    private class SyntheticFile extends File {
        private final long bytes;

        SyntheticFile(String name, long bytes) {
            super(directory.resolve(name).toString());
            this.bytes = bytes;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public File getCanonicalFile() throws IOException {
            return this;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long length() {
            return bytes;
        }
    }
}
