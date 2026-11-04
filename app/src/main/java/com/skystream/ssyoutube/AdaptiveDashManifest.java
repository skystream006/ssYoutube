package com.skystream.ssyoutube;

import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sideloaded DASH uses YouTube's real byte-range indexes, not whole-file quality swaps. */
final class AdaptiveDashManifest {
    private AdaptiveDashManifest() { }

    static String create(List<VideoStream> videos, List<AudioStream> audios,
                         long durationSeconds, String language) {
        if (durationSeconds <= 0 || durationSeconds > Long.MAX_VALUE / 1000) {
            return null;
        }
        Map<String, List<VideoStream>> groups = new LinkedHashMap<>();
        long durationMs = durationSeconds * 1000;
        for (VideoStream video : videos) {
            String family = videoFamily(video.getCodec());
            if (!video.isVideoOnly() || !indexed(video) || family == null
                    || video.getWidth() <= 0 || video.getHeight() <= 0
                    || video.getHeight() > 1080) {
                continue;
            }
            String mime = video.getFormat().getMimeType();
            if (!"video/mp4".equals(mime) && !"video/webm".equals(mime)) {
                continue;
            }
            groups.computeIfAbsent(mime + ":" + family, key -> new ArrayList<>()).add(video);
            durationMs = Math.max(durationMs, video.getItagItem().getApproxDurationMs());
        }
        boolean hasLadder = false;
        for (List<VideoStream> group : groups.values()) {
            int height = group.get(0).getHeight();
            hasLadder |= group.stream().anyMatch(video -> video.getHeight() != height);
        }
        AudioStream audio = NativeStreamExtractor.bestAudio(audios, language);
        if (!hasLadder || audio == null || !indexed(audio)) {
            return null;
        }
        durationMs = Math.max(durationMs, audio.getItagItem().getApproxDurationMs());
        StringBuilder xml = new StringBuilder(
                "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" "
                + "profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" minBufferTime=\"PT1.5S\" "
                + "mediaPresentationDuration=\"PT").append(durationMs / 1000).append('.')
                .append(String.format(java.util.Locale.ROOT, "%03d", durationMs % 1000))
                .append("S\"><Period start=\"PT0S\">");
        int groupId = 0;
        int representationId = 0;
        for (List<VideoStream> group : groups.values()) {
            xml.append("<AdaptationSet id=\"").append(groupId++)
                    .append("\" contentType=\"video\" segmentAlignment=\"true\">");
            for (VideoStream video : group) {
                representation(xml, video, representationId++);
                xml.append(" width=\"").append(video.getWidth()).append("\" height=\"")
                        .append(video.getHeight()).append('"');
                if (video.getFps() > 0) {
                    xml.append(" frameRate=\"").append(video.getFps()).append('"');
                }
                segments(xml, video);
            }
            xml.append("</AdaptationSet>");
        }
        xml.append("<AdaptationSet id=\"").append(groupId).append("\" contentType=\"audio\"");
        if (audio.getAudioLocale() != null) {
            xml.append(" lang=\"").append(escape(audio.getAudioLocale().toLanguageTag())).append('"');
        }
        xml.append('>');
        representation(xml, audio, representationId);
        segments(xml, audio);
        return xml.append("</AdaptationSet></Period></MPD>").toString();
    }

    private static boolean indexed(Stream stream) {
        ItagItem itag = stream.getItagItem();
        return stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && NativeNetworkPolicy.isMediaUrl(stream.getContent()) && stream.getFormat() != null
                && itag != null && itag.getBitrate() > 0 && itag.getCodec() != null
                && !itag.getCodec().isEmpty() && itag.getInitStart() >= 0
                && itag.getInitEnd() > itag.getInitStart()
                && itag.getIndexStart() > itag.getInitEnd()
                && itag.getIndexEnd() > itag.getIndexStart();
    }

    private static String videoFamily(String codec) {
        if (codec == null) {
            return null;
        }
        for (String family : new String[]{"avc1", "avc3", "vp9", "vp09", "av01"}) {
            if (codec.equals(family) || codec.startsWith(family + ".")) {
                return family;
            }
        }
        return null;
    }

    private static void representation(StringBuilder xml, Stream stream, int id) {
        ItagItem itag = stream.getItagItem();
        xml.append("<Representation id=\"").append(id).append("\" mimeType=\"")
                .append(escape(stream.getFormat().getMimeType())).append("\" codecs=\"")
                .append(escape(itag.getCodec())).append("\" bandwidth=\"")
                .append(itag.getBitrate()).append('"');
    }

    private static void segments(StringBuilder xml, Stream stream) {
        ItagItem itag = stream.getItagItem();
        xml.append("><BaseURL>").append(escape(stream.getContent()))
                .append("</BaseURL><SegmentBase indexRange=\"")
                .append(itag.getIndexStart()).append('-').append(itag.getIndexEnd())
                .append("\"><Initialization range=\"").append(itag.getInitStart()).append('-')
                .append(itag.getInitEnd()).append("\"/></SegmentBase></Representation>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
