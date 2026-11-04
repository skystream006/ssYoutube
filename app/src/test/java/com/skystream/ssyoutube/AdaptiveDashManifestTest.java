package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

public class AdaptiveDashManifestTest {
    static final String CDN = "https://rr1.googlevideo.com/videoplayback";

    @Test
    public void manifestHasRealRangesMultipleQualitiesAndSeparatePreferredAudio() throws Exception {
        Document xml = parse(ladder());
        assertEquals("PT60.125S", xml.getDocumentElement().getAttribute("mediaPresentationDuration"));
        NodeList sets = xml.getElementsByTagName("AdaptationSet");
        assertEquals(2, sets.getLength());
        Element video = (Element) sets.item(0);
        assertEquals(3, video.getElementsByTagName("Representation").getLength());
        Element audio = (Element) sets.item(1);
        assertEquals("en", audio.getAttribute("lang"));
        assertEquals(1, audio.getElementsByTagName("Representation").getLength());
        NodeList ranges = xml.getElementsByTagName("SegmentBase");
        for (int i = 0; i < ranges.getLength(); i++) {
            assertEquals("700-999", ((Element) ranges.item(i)).getAttribute("indexRange"));
            assertEquals("0-699", ((Element) xml.getElementsByTagName("Initialization").item(i))
                    .getAttribute("range"));
        }
        assertEquals(CDN + "?quality=360&token=example",
                xml.getElementsByTagName("BaseURL").item(0).getTextContent());
    }

    @Test
    public void groupsCodecsSeparatelyAndKeepsResolutionCap() throws Exception {
        VideoStream webm = video(720, 1_000_000, "vp09.00.31.08", MediaFormat.WEBM);
        Document xml = parse(AdaptiveDashManifest.create(Arrays.asList(
                video(360, 400_000), video(720, 1_500_000), webm,
                video(2160, 8_000_000)), Collections.singletonList(audio("en")), 60, "en"));
        assertEquals(3, xml.getElementsByTagName("AdaptationSet").getLength());
        NodeList representations = xml.getElementsByTagName("Representation");
        assertEquals(4, representations.getLength());
        for (int i = 0; i < representations.getLength(); i++) {
            assertNotEquals("2160", ((Element) representations.item(i)).getAttribute("height"));
        }
    }

    @Test
    public void languageSelectionUsesExistingPreferenceRules() throws Exception {
        Document xml = parse(AdaptiveDashManifest.create(Arrays.asList(
                video(360, 400_000), video(720, 1_500_000)),
                Arrays.asList(audio("en"), audio("es")), 60, "es"));
        Element audio = (Element) xml.getElementsByTagName("AdaptationSet").item(1);
        assertEquals("es", audio.getAttribute("lang"));
        assertEquals(CDN + "?audio=es",
                audio.getElementsByTagName("BaseURL").item(0).getTextContent());
    }

    @Test
    public void adaptationMustNotDiscardThePreferredLanguageWhenItsIndexIsMissing() {
        AudioStream preferred = new AudioStream.Builder().setId("spanish")
                .setContent(CDN + "?audio=es", true).setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128).setAudioLocale(Locale.forLanguageTag("es")).build();
        assertNull(AdaptiveDashManifest.create(Arrays.asList(
                video(360, 400_000), video(720, 1_500_000)),
                Arrays.asList(audio("en"), preferred), 60, "es"));
    }

    @Test
    public void missingIndexesDurationAudioOrCompatibleLadderCannotPretendToBeAdaptive() {
        VideoStream missingIndex = new VideoStream.Builder().setId("no-index")
                .setContent(CDN, true).setResolution("720p").setIsVideoOnly(true)
                .setMediaFormat(MediaFormat.MPEG_4).build();
        for (java.util.List<VideoStream> videos : Arrays.asList(
                Collections.singletonList(video(360, 400_000)),
                Arrays.asList(video(360, 400_000), missingIndex),
                Arrays.asList(video(360, 400_000),
                        video(720, 1_500_000, "vp09.00.31.08", MediaFormat.WEBM)))) {
            assertNull(AdaptiveDashManifest.create(videos,
                    Collections.singletonList(audio("en")), 60, "en"));
        }
        assertNull(AdaptiveDashManifest.create(Arrays.asList(video(360, 400_000),
                video(720, 1_500_000)), Collections.emptyList(), 60, "en"));
        assertNull(AdaptiveDashManifest.create(Arrays.asList(video(360, 400_000),
                video(720, 1_500_000)), Collections.singletonList(audio("en")), 0, "en"));
    }

    @Test
    public void unsafeUrlsAndInvalidRangesAreExcluded() {
        VideoStream safe = video(360, 400_000);
        VideoStream high = video(720, 1_500_000);
        VideoStream unsafe = new VideoStream.Builder().setId("unsafe")
                .setContent("https://untrusted.example/video", true).setResolution("720p")
                .setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(true)
                .setItagItem(high.getItagItem()).build();
        assertNull(AdaptiveDashManifest.create(Arrays.asList(safe, unsafe),
                Collections.singletonList(audio("en")), 60, "en"));
        high.getItagItem().setIndexStart(1);
        assertNull(AdaptiveDashManifest.create(Arrays.asList(safe, high),
                Collections.singletonList(audio("en")), 60, "en"));
    }

    static String ladder() {
        return AdaptiveDashManifest.create(Arrays.asList(
                video(360, 400_000), video(720, 1_500_000), video(1080, 3_000_000)),
                Collections.singletonList(audio("en")), 60, "en");
    }

    static VideoStream video(int height, int bitrate) {
        return video(height, bitrate, "avc1.64001f", MediaFormat.MPEG_4);
    }

    static VideoStream video(int height, int bitrate, String codec, MediaFormat format) {
        ItagItem itag = new ItagItem(height, ItagItem.ItagType.VIDEO_ONLY,
                format, height + "p", 30);
        indexes(itag, bitrate, codec);
        itag.setWidth(height * 16 / 9);
        itag.setHeight(height);
        return new VideoStream.Builder().setId("video-" + height)
                .setContent(CDN + "?quality=" + height + "&token=example", true)
                .setResolution(height + "p").setIsVideoOnly(true)
                .setMediaFormat(format).setItagItem(itag).build();
    }

    static AudioStream audio(String language) {
        ItagItem itag = new ItagItem(140, ItagItem.ItagType.AUDIO, MediaFormat.M4A, 128);
        indexes(itag, 128_000, "mp4a.40.2");
        return new AudioStream.Builder().setId("audio-" + language)
                .setContent(CDN + "?audio=" + language, true).setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128).setAudioLocale(Locale.forLanguageTag(language))
                .setItagItem(itag).build();
    }

    private static void indexes(ItagItem itag, int bitrate, String codec) {
        itag.setBitrate(bitrate);
        itag.setCodec(codec);
        itag.setInitStart(0);
        itag.setInitEnd(699);
        itag.setIndexStart(700);
        itag.setIndexEnd(999);
        itag.setApproxDurationMs(60_125);
    }

    private static Document parse(String manifest) throws Exception {
        assertNotNull(manifest);
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
