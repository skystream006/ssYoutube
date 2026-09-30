package com.skystream.ssyoutube;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only extracted HTTPS media, never page input or command text, reaches libmpv. */
final class MpvPlaybackRequest {
    final String url;
    final Map<String, String> options = new LinkedHashMap<>();

    MpvPlaybackRequest(String videoUrl, String audioUrl, long startMs, String certificates)
            throws IOException {
        url = NativeNetworkPolicy.requireHttps(videoUrl, true).toString();
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
        if (audioUrl != null) {
            options.put("audio-files-append",
                    NativeNetworkPolicy.requireHttps(audioUrl, true).toString());
        }
    }

    String[] loadCommand() {
        return new String[]{"loadfile", url, "replace"};
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
