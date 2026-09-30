package com.skystream.ssyoutube;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** NewPipe owns YouTube protocol changes, signatures and stream extraction. */
final class NativeStreamExtractor {
    enum Kind { PROGRESSIVE, HLS, DASH }

    static final class Result {
        final Kind kind;
        final String videoUrl;
        final String videoMime;
        final String audioUrl;
        final String audioMime;
        final boolean live;

        Result(Kind kind, String videoUrl, String videoMime, String audioUrl, String audioMime,
               boolean live) {
            this.kind = kind;
            this.videoUrl = videoUrl;
            this.videoMime = videoMime;
            this.audioUrl = audioUrl;
            this.audioMime = audioMime;
            this.live = live;
        }
    }

    private static final ExtractorDownloader DOWNLOADER = new ExtractorDownloader();
    private static boolean initialized;

    private NativeStreamExtractor() { }

    static StreamExtractor localizedExtractor(String videoId, String language) throws Exception {
        if (!NativePlaybackState.isVideoId(videoId)) {
            throw new IOException("Invalid native video identifier");
        }
        initialize();
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=" + videoId);
        extractor.forceLocalization(new Localization(Preferences.normalizeLanguage(language)));
        return extractor;
    }

    static Result extract(String videoId, String language,
                          ExtractorDownloader.Cancellation cancellation) throws Exception {
        StreamExtractor extractor = localizedExtractor(videoId, language);
        DOWNLOADER.begin(cancellation);
        try {
            StreamInfo info = StreamInfo.getInfo(extractor);
            cancellation.check();
            boolean live = info.getStreamType() == StreamType.LIVE_STREAM
                    || info.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
            if (live) {
                Result manifest = manifest(info, true);
                if (manifest != null) {
                    return manifest;
                }
            }
            Result streams = selectStreams(info.getVideoStreams(),
                    info.getVideoOnlyStreams(), info.getAudioStreams(), live, language);
            if (streams != null) {
                return streams;
            }
            Result manifest = manifest(info, live);
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

    private static Result manifest(StreamInfo info, boolean live) {
        if (NativeNetworkPolicy.isMediaUrl(info.getHlsUrl())) {
            return new Result(Kind.HLS, info.getHlsUrl(), "application/x-mpegURL", null, null, live);
        }
        if (NativeNetworkPolicy.isMediaUrl(info.getDashMpdUrl())) {
            return new Result(Kind.DASH, info.getDashMpdUrl(), "application/dash+xml", null, null, live);
        }
        return null;
    }

    static Result selectStreams(List<VideoStream> muxedStreams, List<VideoStream> videoStreams,
                                List<AudioStream> audioStreams, boolean live) {
        return selectStreams(muxedStreams, videoStreams, audioStreams, live,
                Preferences.DEFAULT_LANGUAGE);
    }

    static Result selectStreams(List<VideoStream> muxedStreams, List<VideoStream> videoStreams,
                                List<AudioStream> audioStreams, boolean live, String language) {
        VideoStream muxed = bestVideo(muxedStreams);
        VideoStream video = bestVideo(videoStreams);
        AudioStream audio = bestAudio(audioStreams, language);
        if (video != null && audio != null && (muxed == null
                || (resolution(video.getResolution()) > resolution(muxed.getResolution())
                && videoCompatibility(mime(video), video.getCodec())
                >= videoCompatibility(mime(muxed), muxed.getCodec())))) {
            return new Result(Kind.PROGRESSIVE, video.getContent(), mime(video),
                    audio.getContent(), mime(audio), live);
        }
        if (muxed != null) {
            // Muxed streams have no audio-language metadata; use a matching separate track.
            if (audio != null && matchesLanguage(audio, Preferences.normalizeLanguage(language))) {
                return new Result(Kind.PROGRESSIVE, muxed.getContent(), mime(muxed),
                        audio.getContent(), mime(audio), live);
            }
            return new Result(Kind.PROGRESSIVE, muxed.getContent(), mime(muxed), null, null, live);
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
            int score = height + videoCompatibility(mime, stream.getCodec());
            if (score > bestScore) {
                best = stream;
                bestScore = score;
            }
        }
        return best;
    }

    static int videoCompatibility(String mime, String codecs) {
        if (codecs != null) {
            for (String token : codecs.split(",")) {
                String codec = token.trim().toLowerCase(Locale.US);
                if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
                    return 30_000;
                }
            }
        }
        // MP4 may contain AV1: only use the container as a hint when the codec is unknown.
        if ((codecs == null || codecs.trim().isEmpty()) && "video/mp4".equals(mime)) {
            return 20_000;
        }
        return "video/webm".equals(mime) ? 10_000 : 0;
    }

    static AudioStream bestAudio(List<AudioStream> streams) {
        return bestAudio(streams, Preferences.DEFAULT_LANGUAGE);
    }

    static AudioStream bestAudio(List<AudioStream> streams, String language) {
        String preferredLanguage = Preferences.normalizeLanguage(language);
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
            if (matchesLanguage(stream, preferredLanguage)) {
                score += 10_000;
            }
            if (stream.getAudioTrackType() == AudioTrackType.ORIGINAL) {
                score += 2_000;
            }
            if (score > bestScore) {
                best = stream;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean matchesLanguage(AudioStream stream, String language) {
        Locale locale = stream.getAudioLocale();
        return locale != null && new Locale(language).getLanguage().equals(locale.getLanguage());
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
