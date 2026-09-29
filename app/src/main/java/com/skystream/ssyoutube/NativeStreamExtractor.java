package com.skystream.ssyoutube;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.List;

/** NewPipe owns YouTube protocol changes, signatures and stream extraction. */
final class NativeStreamExtractor {
    enum Kind { PROGRESSIVE, HLS, DASH }

    static final class Result {
        final Kind kind;
        final String videoUrl;
        final String videoMime;
        final String audioUrl;
        final String audioMime;

        Result(Kind kind, String videoUrl, String videoMime, String audioUrl, String audioMime) {
            this.kind = kind;
            this.videoUrl = videoUrl;
            this.videoMime = videoMime;
            this.audioUrl = audioUrl;
            this.audioMime = audioMime;
        }
    }

    private static final ExtractorDownloader DOWNLOADER = new ExtractorDownloader();
    private static boolean initialized;

    private NativeStreamExtractor() { }

    static Result extract(String videoId, ExtractorDownloader.Cancellation cancellation)
            throws Exception {
        if (!NativePlaybackState.isVideoId(videoId)) {
            throw new IOException("Invalid native video identifier");
        }
        initialize();
        DOWNLOADER.begin(cancellation);
        try {
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + videoId);
            cancellation.check();
            boolean live = info.getStreamType() == StreamType.LIVE_STREAM
                    || info.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
            if (live) {
                Result manifest = manifest(info);
                if (manifest != null) {
                    return manifest;
                }
            }
            VideoStream muxed = bestVideo(info.getVideoStreams());
            if (muxed != null) {
                return new Result(Kind.PROGRESSIVE, muxed.getContent(), mime(muxed), null, null);
            }
            VideoStream video = bestVideo(info.getVideoOnlyStreams());
            AudioStream audio = bestAudio(info.getAudioStreams());
            if (video != null && audio != null) {
                return new Result(Kind.PROGRESSIVE, video.getContent(), mime(video),
                        audio.getContent(), mime(audio));
            }
            Result manifest = manifest(info);
            if (manifest != null) {
                return manifest;
            }
            throw new IOException("No supported native streams");
        } finally {
            DOWNLOADER.end();
        }
    }

    private static synchronized void initialize() {
        if (!initialized) {
            NewPipe.init(DOWNLOADER);
            initialized = true;
        }
    }

    private static Result manifest(StreamInfo info) {
        if (NativeNetworkPolicy.isMediaUrl(info.getHlsUrl())) {
            return new Result(Kind.HLS, info.getHlsUrl(), "application/x-mpegURL", null, null);
        }
        if (NativeNetworkPolicy.isMediaUrl(info.getDashMpdUrl())) {
            return new Result(Kind.DASH, info.getDashMpdUrl(), "application/dash+xml", null, null);
        }
        return null;
    }

    static VideoStream bestVideo(List<VideoStream> streams) {
        VideoStream best = null;
        int bestScore = -1;
        for (VideoStream stream : streams) {
            if (!isProgressive(stream)) {
                continue;
            }
            int height = resolution(stream.getResolution());
            if (height <= 0 || height > 1080) {
                continue;
            }
            String mime = mime(stream);
            if (!"video/mp4".equals(mime) && !"video/webm".equals(mime)) {
                continue;
            }
            String codec = stream.getCodec();
            // An MP4 container alone does not imply H.264: YouTube also serves AV1 in MP4.
            int compatibility = codec != null && codec.startsWith("avc") ? 30_000
                    : codec == null && "video/mp4".equals(mime) ? 20_000
                    : "video/webm".equals(mime) ? 10_000 : 0;
            int score = height + compatibility;
            if (score > bestScore) {
                best = stream;
                bestScore = score;
            }
        }
        return best;
    }

    static AudioStream bestAudio(List<AudioStream> streams) {
        AudioStream best = null;
        int bestScore = -1;
        for (AudioStream stream : streams) {
            if (!isProgressive(stream)) {
                continue;
            }
            String mime = mime(stream);
            boolean mp4 = "audio/mp4".equals(mime);
            if (!mp4 && !"audio/webm".equals(mime) && !"audio/ogg".equals(mime)) {
                continue;
            }
            int score = Math.max(0, Math.min(320, stream.getAverageBitrate()))
                    + (mp4 ? 1_000 : 0);
            if (score > bestScore) {
                best = stream;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isProgressive(Stream stream) {
        return stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && NativeNetworkPolicy.isMediaUrl(stream.getContent());
    }

    private static String mime(Stream stream) {
        return stream.getFormat() == null ? null : stream.getFormat().getMimeType();
    }

    private static int resolution(String value) {
        if (value == null) {
            return 0;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        try {
            return end == 0 ? 0 : Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
