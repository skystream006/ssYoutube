package com.skystream.ssyoutube;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Collects only observed playback ranges; seeks and time spent paused are not watch time. */
final class NativeWatchHistoryState {
    static final long SAMPLE_MS = 1000;
    static final long REPORT_MS = 10000;

    static final class Report {
        final String videoId;
        final long session;
        final String starts;
        final String ends;
        final long positionMs;
        final long durationMs;
        final boolean playing;

        Report(String videoId, long session, String starts, String ends, long positionMs,
                long durationMs, boolean playing) {
            this.videoId = videoId;
            this.session = session;
            this.starts = starts;
            this.ends = ends;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.playing = playing;
        }
    }

    private String videoId;
    private long session;
    private long previousPosition;
    private long previousTime;
    private float previousSpeed;
    private boolean wasPlaying;
    private long lastReportTime;
    private long continuityToken;
    private final List<long[]> ranges = new ArrayList<>();

    Report sample(String id, long position, long duration, boolean playing, float speed,
                  long now, boolean flush) {
        if (!NativePlaybackState.isVideoId(id) || position < 0) {
            reset();
            return null;
        }
        if (!id.equals(videoId)) {
            reset();
            videoId = id;
            session++;
            lastReportTime = now;
        } else if (wasPlaying && playing) {
            long elapsed = now - previousTime;
            long delta = position - previousPosition;
            // A delayed sample or a position jump cannot prove that the intervening video played.
            if (elapsed > 0 && elapsed <= 2 * SAMPLE_MS && delta > 0
                    && delta <= elapsed * previousSpeed + 250) {
                if (!ranges.isEmpty() && ranges.get(ranges.size() - 1)[1] == previousPosition) {
                    ranges.get(ranges.size() - 1)[1] = position;
                } else {
                    ranges.add(new long[] {previousPosition, position});
                }
            }
        }
        previousPosition = position;
        previousTime = now;
        previousSpeed = Float.isNaN(speed) || Float.isInfinite(speed) || speed <= 0 ? 1 : speed;
        wasPlaying = playing;
        if (!playing || flush || now - lastReportTime >= REPORT_MS) {
            lastReportTime = now;
            if (ranges.isEmpty()) {
                return null;
            }
            StringBuilder starts = new StringBuilder();
            StringBuilder ends = new StringBuilder();
            for (long[] range : ranges) {
                if (starts.length() > 0) {
                    starts.append(',');
                    ends.append(',');
                }
                starts.append(seconds(range[0]));
                ends.append(seconds(range[1]));
            }
            ranges.clear();
            return new Report(id, session, starts.toString(), ends.toString(),
                    position, Math.max(0, duration), playing);
        }
        return null;
    }

    void reset() {
        videoId = null;
        wasPlaying = false;
        ranges.clear();
    }

    long session() {
        return session;
    }

    void onContinuityToken(long token) {
        if (token != continuityToken) {
            wasPlaying = false;
            continuityToken = token;
        }
    }

    static String seconds(long milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds / 1000.0);
    }
}
