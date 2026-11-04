package com.skystream.ssyoutube;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.FilteringMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@UnstableApi
final class ExoPlayback {
    private ExoPlayback() { }

    static ExoPlayer create(Context context) {
        // Errors reach the privacy-filtered app logger instead of URL-bearing Media3 logs.
        Log.setLogLevel(Log.LOG_LEVEL_OFF);
        DefaultTrackSelector tracks = new DefaultTrackSelector(context);
        tracks.setParameters(tracks.buildUponParameters()
                .setMaxVideoSize(Integer.MAX_VALUE, 1080)
                .setExceedVideoConstraintsIfNecessary(false)
                .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_VP9)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowVideoNonSeamlessAdaptiveness(false));
        return new ExoPlayer.Builder(context)
                .setTrackSelector(tracks)
                .setBandwidthMeter(new DefaultBandwidthMeter.Builder(context)
                        .setInitialBitrateEstimate(800_000L).build())
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15_000, 50_000, 750, 2_500)
                        .setTargetBufferBytes(64 * 1024 * 1024)
                        .setPrioritizeTimeOverSizeThresholds(false).build())
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                        true)
                .setHandleAudioBecomingNoisy(true)
                .build();
    }

    static void setStream(ExoPlayer player, NativeStreamExtractor.Result stream, String id,
                          long startMs, String language) throws IOException {
        DataSource.Factory data = new OkHttpDataSource.Factory(NativeMediaHttp.client());
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage(Preferences.normalizeLanguage(language)).build());
        player.setMediaSource(source(stream, id, data), startMs);
    }

    static MediaSource source(NativeStreamExtractor.Result stream, String id,
                              DataSource.Factory data) throws IOException {
        if (stream.dashManifest != null) {
            DashManifest manifest = new DashManifestParser().parse(Uri.EMPTY,
                    new ByteArrayInputStream(stream.dashManifest.getBytes(StandardCharsets.UTF_8)));
            return new DashMediaSource.Factory(data).createMediaSource(manifest,
                    new MediaItem.Builder().setMediaId(id).setUri(Uri.EMPTY)
                            .setMimeType(MimeTypes.APPLICATION_MPD).build());
        }
        MediaItem video = item(id, stream.videoUrl, stream.videoMime);
        if (stream.kind == NativeStreamExtractor.Kind.HLS) {
            return new HlsMediaSource.Factory(data).createMediaSource(video);
        }
        if (stream.kind == NativeStreamExtractor.Kind.DASH) {
            return new DashMediaSource.Factory(data).createMediaSource(video);
        }
        MediaSource source = new ProgressiveMediaSource.Factory(data).createMediaSource(video);
        if (stream.audioUrl != null) {
            MediaSource audio = new ProgressiveMediaSource.Factory(data).createMediaSource(
                    item(id, stream.audioUrl, stream.audioMime));
            source = new MergingMediaSource(
                    new FilteringMediaSource(source, C.TRACK_TYPE_VIDEO), audio);
        }
        return source;
    }

    private static MediaItem item(String id, String url, String mime) throws IOException {
        return new MediaItem.Builder().setMediaId(id)
                .setUri(NativeNetworkPolicy.requireHttps(url, true).toString())
                .setMimeType(mime).build();
    }
}
