package com.skystream.ssyoutube;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only extracted HTTPS media, never page input or command text, reaches libmpv. */
final class MpvPlaybackRequest {
    interface CommandRunner {
        boolean run(String... command);
    }

    final String url;
    private final String audioUrl;
    final Map<String, String> options = new LinkedHashMap<>();

    MpvPlaybackRequest(String videoUrl, String audioUrl, long startMs, String certificates)
            throws IOException {
        this(videoUrl, audioUrl, startMs, certificates, Preferences.DEFAULT_LANGUAGE);
    }

    MpvPlaybackRequest(String videoUrl, String audioUrl, long startMs, String certificates,
                       String language) throws IOException {
        url = NativeNetworkPolicy.requireHttps(videoUrl, true).toString();
        this.audioUrl = audioUrl == null ? null
                : NativeNetworkPolicy.requireHttps(audioUrl, true).toString();
        options.put("config", "no");
        options.put("load-scripts", "no");
        options.put("ytdl", "no");
        options.put("msg-level", "all=no");
        options.put("terminal", "no");
        options.put("input-default-bindings", "no");
        options.put("input-vo-keyboard", "no");
        options.put("osc", "no");
        options.put("osd-level", "0");
        options.put("sub-auto", "no");
        options.put("audio-file-auto", "no");
        options.put("alang", Preferences.normalizeLanguage(language));
        options.put("cover-art-auto", "no");
        options.put("cookies", "no");
        options.put("http-header-fields", "");
        options.put("user-agent", NativeNetworkPolicy.USER_AGENT);
        options.put("tls-verify", "yes");
        options.put("tls-ca-file", certificates);
        options.put("network-timeout", "20");
        // Force FFmpeg demuxing and disallow local files/cleartext in nested manifests.
        options.put("demuxer", "lavf");
        String protocols = "protocol_whitelist=%20%https,tls,tcp,crypto";
        options.put("demuxer-lavf-o", protocols);
        options.put("stream-lavf-o", protocols);
        options.put("demuxer-max-bytes", "64MiB");
        options.put("demuxer-max-back-bytes", "16MiB");
        options.put("cache", "yes");
        options.put("keep-open", "yes");
        options.put("idle", "yes");
        options.put("vo", "null");
        options.put("gpu-context", "android");
        options.put("opengl-es", "yes");
        options.put("hwdec", "mediacodec,mediacodec-copy");
        options.put("ao", "audiotrack,opensles");
        if (startMs >= 0) {
            options.put("start", seconds(startMs));
        }
    }

    /** Run after initialization, on the fresh handle owned by this request. */
    void load(CommandRunner runner) throws IOException {
        // List-operation suffixes are CLI syntax, not libmpv option names.
        // append treats the complete URL as one filename, including commas and colons.
        if (audioUrl != null && !runner.run("change-list", "audio-files", "append", audioUrl)) {
            throw new IOException("mpv could not configure the audio stream");
        }
        if (!runner.run("loadfile", url, "replace")) {
            throw new IOException("mpv could not open the stream");
        }
    }

    static String seconds(long milliseconds) {
        long value = Math.max(0, milliseconds);
        return value / 1000 + "." + String.format(java.util.Locale.ROOT, "%03d", value % 1000);
    }

    static long milliseconds(Double seconds) {
        if (seconds == null || seconds.isNaN() || seconds.isInfinite() || seconds < 0
                || seconds > Long.MAX_VALUE / 1000.0) {
            return -1;
        }
        long milliseconds = (long) (seconds * 1000);
        // Media3 timelines also represent these values in microseconds.
        return milliseconds > Long.MAX_VALUE / 1000 ? -1 : milliseconds;
    }
}
