package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w1920dp-h1080dp")
public class ExoPlaybackTest {
    private static final String ID = "dQw4w9WgXcQ";
    private static final String CDN = AdaptiveDashManifestTest.CDN;
    private static final DataSource.Factory NO_NETWORK = () -> {
        throw new AssertionError("Source construction must not fetch media");
    };

    @Test
    public void parsedDashExposesIndexedRepresentationsToTheChunkEngine() throws Exception {
        DashManifest manifest = manifest();
        assertFalse(manifest.dynamic);
        assertEquals(60_125, manifest.durationMs);
        assertEquals(2, manifest.getPeriod(0).adaptationSets.size());
        for (Representation representation : manifest.getPeriod(0).adaptationSets.get(0)
                .representations) {
            assertEquals("video/avc", representation.format.sampleMimeType);
            assertEquals(0, representation.getInitializationUri().start);
            assertEquals(700, representation.getInitializationUri().length);
            assertEquals(700, representation.getIndexUri().start);
            assertEquals(300, representation.getIndexUri().length);
        }
        NativeStreamExtractor.Result stream = new NativeStreamExtractor.Result(
                NativeStreamExtractor.Kind.DASH, null, null, null, null, false,
                AdaptiveDashManifestTest.ladder());
        assertTrue(ExoPlayback.source(stream, ID, NO_NETWORK) instanceof DashMediaSource);
        assertEquals(ID, ExoPlayback.source(stream, ID, NO_NETWORK).getMediaItem().mediaId);
    }

    @Test
    public void startsLowWaitsForBufferBeforeUpgradingAndDropsWhenBandwidthFalls() throws Exception {
        List<Format> formats = manifest().getPeriod(0).adaptationSets.get(0).representations.stream()
                .map(representation -> representation.format).collect(Collectors.toList());
        MutableBandwidth bandwidth = new MutableBandwidth();
        ExoPlayer player = ExoPlayback.create(RuntimeEnvironment.getApplication());
        try {
            DefaultTrackSelector selector = (DefaultTrackSelector) player.getTrackSelector();
            selector.init(() -> { }, bandwidth);
            ExoTrackSelection selection = selector.selectTracks(
                    new RendererCapabilities[]{new VideoCapabilities()},
                    new TrackGroupArray(new TrackGroup(formats.toArray(new Format[0]))),
                    new MediaPeriodId("fixture"), Timeline.EMPTY).selections[0];
            assertNotNull(selection);
            assertEquals(3, selection.length());
            update(selection, 0);
            assertEquals(360, selection.getSelectedFormat().height);

            bandwidth.bitrate = 10_000_000;
            update(selection, 9_999_000);
            assertEquals("Fast network alone must not trigger an upgrade", 360,
                    selection.getSelectedFormat().height);
            update(selection, 10_000_000);
            assertEquals(1080, selection.getSelectedFormat().height);

            bandwidth.bitrate = 800_000;
            update(selection, 2_000_000);
            assertEquals(360, selection.getSelectedFormat().height);
        } finally {
            player.release();
        }
    }

    @Test
    public void liveManifestsAndFixedFallbackUseTheirCorrectMediaSources() throws Exception {
        assertTrue(ExoPlayback.source(new NativeStreamExtractor.Result(
                NativeStreamExtractor.Kind.HLS, CDN, "application/x-mpegURL", null, null, true),
                ID, NO_NETWORK) instanceof HlsMediaSource);
        assertTrue(ExoPlayback.source(new NativeStreamExtractor.Result(
                NativeStreamExtractor.Kind.DASH, CDN, "application/dash+xml", null, null, true),
                ID, NO_NETWORK) instanceof DashMediaSource);
        assertTrue(ExoPlayback.source(progressive(null), ID, NO_NETWORK)
                instanceof ProgressiveMediaSource);
        assertTrue(ExoPlayback.source(progressive(CDN + "?audio"), ID, NO_NETWORK)
                instanceof MergingMediaSource);
        assertThrows(IOException.class, () -> ExoPlayback.source(new NativeStreamExtractor.Result(
                NativeStreamExtractor.Kind.HLS, "http://rr1.googlevideo.com/live",
                "application/x-mpegURL", null, null, true), ID, NO_NETWORK));
    }

    @Test
    public void playerKeepsAutomaticSelectionCapLanguageAndTimestamp() throws Exception {
        ExoPlayer player = ExoPlayback.create(RuntimeEnvironment.getApplication());
        try {
            assertFalse(player.getTrackSelectionParameters().forceHighestSupportedBitrate);
            assertFalse(player.getTrackSelectionParameters().forceLowestBitrate);
            assertEquals(1080, player.getTrackSelectionParameters().maxVideoHeight);
            ExoPlayback.setStream(player, progressive(null), ID, 42_000, "es");
            assertEquals(Collections.singletonList("es"),
                    player.getTrackSelectionParameters().preferredAudioLanguages);
            assertEquals(ID, player.getCurrentMediaItem().mediaId);
            assertEquals(42_000, player.getCurrentPosition());
            player.clearMediaItems();
            assertEquals(0, player.getMediaItemCount());
        } finally {
            player.release();
        }
    }

    @Test
    public void changingEnginesClearsTheOldVideoAndReleasesCleanly() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                RuntimeEnvironment.getApplication().getPackageName()
                        + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        NativePlayerView view = new NativePlayerView(RuntimeEnvironment.getApplication(),
                PlaybackEngine.EXOPLAYER, new NativePlayerView.Listener() {
                    @Override public void onClose() { }
                    @Override public void onMinimize() { }
                    @Override public void onToggleFullscreen() { }
                });
        try {
            assertEquals(-1, view.getPositionMsForHistory());
            view.play(ID, 42_000);
            assertEquals("Unprepared playback must not report placeholder zero",
                    -1, view.getPositionMsForHistory());
            view.setFullscreen(true);
            view.setMinimized(true);
            Bundle saved = new Bundle();
            view.saveState(saved);
            assertEquals(42_000, saved.getLong("native.position"));
            view.setPlaybackEngine(PlaybackEngine.MPV);
            assertFalse(view.hasVideo());
            view.setPlaybackEngine(PlaybackEngine.WEB);
            assertFalse(view.hasVideo());
            view.setPlaybackEngine(PlaybackEngine.EXOPLAYER);
            view.restoreState(saved);
            assertEquals(ID, view.getVideoId());
            assertEquals(-1, view.getPositionMsForHistory());
            view.stop();
            assertFalse(view.hasVideo());
        } finally {
            view.release();
            assertEquals(-1, view.getPositionMsForHistory());
            view.release();
        }
    }

    private static DashManifest manifest() throws Exception {
        return new DashManifestParser().parse(Uri.EMPTY, new ByteArrayInputStream(
                AdaptiveDashManifestTest.ladder().getBytes(StandardCharsets.UTF_8)));
    }

    private static NativeStreamExtractor.Result progressive(String audio) {
        return new NativeStreamExtractor.Result(NativeStreamExtractor.Kind.PROGRESSIVE,
                CDN, "video/mp4", audio, "audio/mp4", false);
    }

    private static void update(ExoTrackSelection selection, long bufferUs) {
        selection.updateSelectedTrack(0, bufferUs, C.TIME_UNSET, Collections.emptyList(),
                new MediaChunkIterator[]{MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY,
                        MediaChunkIterator.EMPTY});
    }

    private static final class MutableBandwidth implements BandwidthMeter {
        long bitrate = 800_000;
        @Override public long getBitrateEstimate() { return bitrate; }
        @Override public TransferListener getTransferListener() { return null; }
        @Override public void addEventListener(Handler handler, EventListener listener) { }
        @Override public void removeEventListener(EventListener listener) { }
    }

    private static final class VideoCapabilities implements RendererCapabilities {
        @Override public String getName() { return "fixture-video"; }
        @Override public int getTrackType() { return C.TRACK_TYPE_VIDEO; }
        @Override public int supportsFormat(Format format) {
            return RendererCapabilities.create(C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS,
                    TUNNELING_NOT_SUPPORTED);
        }
        @Override public int supportsMixedMimeTypeAdaptation() { return ADAPTIVE_NOT_SUPPORTED; }
    }
}
