package com.skystream.ssyoutube;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class NativeStreamExtractorTest {
    private static final String CDN = "https://rr1.googlevideo.com/videoplayback";

    @Test
    public void extractionLanguageIsRequestScopedAndDefaultsToEnglish() throws Exception {
        StreamExtractor english = NativeStreamExtractor.localizedExtractor("dQw4w9WgXcQ", null);
        StreamExtractor spanish = NativeStreamExtractor.localizedExtractor("dQw4w9WgXcQ", "es");
        assertEquals("es", spanish.getExtractorLocalization().getLanguageCode());
        assertEquals("en", english.getExtractorLocalization().getLanguageCode());
        assertEquals("en", NativeStreamExtractor.localizedExtractor("dQw4w9WgXcQ", "invalid")
                .getExtractorLocalization().getLanguageCode());
    }

    @Test
    public void languageTakesPriorityOverAudioCodecAndBitrate() {
        AudioStream english = audio(CDN + "?en", MediaFormat.WEBMA_OPUS, 64, "en",
                AudioTrackType.DUBBED);
        AudioStream spanish = audio(CDN + "?es", MediaFormat.M4A, 320, "es",
                AudioTrackType.ORIGINAL);
        assertSame(english, NativeStreamExtractor.bestAudio(Arrays.asList(spanish, english)));
        assertSame(spanish, NativeStreamExtractor.bestAudio(Arrays.asList(english, spanish), "es"));
    }

    @Test
    public void languageMatchesRegionalVariantsAndKeepsQualityPreference() {
        AudioStream aac = audio(CDN + "?br", MediaFormat.M4A, 128, "pt-BR",
                AudioTrackType.DUBBED);
        AudioStream opus = audio(CDN + "?pt", MediaFormat.WEBMA_OPUS, 256, "pt-PT",
                AudioTrackType.DUBBED);
        assertSame(aac, NativeStreamExtractor.bestAudio(Arrays.asList(opus, aac), "pt"));
        AudioStream indonesian = audio(CDN + "?id", MediaFormat.M4A, 128, "id-ID", null);
        assertSame(indonesian, NativeStreamExtractor.bestAudio(
                Arrays.asList(aac, indonesian), "id"));
    }

    @Test
    public void unavailableLanguageFallsBackToOriginalThenExistingQualityRules() {
        AudioStream dubbed = audio(CDN + "?dub", MediaFormat.M4A, 320, "es",
                AudioTrackType.DUBBED);
        AudioStream original = audio(CDN + "?original", MediaFormat.WEBMA_OPUS, 64, "de",
                AudioTrackType.ORIGINAL);
        assertSame(original, NativeStreamExtractor.bestAudio(Arrays.asList(dubbed, original), "ja"));
        AudioStream unknown = audio(CDN, MediaFormat.M4A, 128);
        assertSame(unknown, NativeStreamExtractor.bestAudio(Arrays.asList(
                audio(CDN, MediaFormat.WEBMA_OPUS, 256), unknown), "ja"));
        assertNull(NativeStreamExtractor.bestAudio(Collections.emptyList(), "ja"));
    }

    @Test
    public void languagePreferenceNeverBypassesStreamSafetyChecks() {
        AudioStream safe = audio(CDN, MediaFormat.M4A, 128, "es", AudioTrackType.ORIGINAL);
        for (String unsafe : new String[]{"http://rr1.googlevideo.com/audio",
                "https://attacker.example/audio", "file:///audio"}) {
            AudioStream matching = audio(unsafe, MediaFormat.M4A, 320, "en", AudioTrackType.DUBBED);
            assertSame(safe, NativeStreamExtractor.bestAudio(Arrays.asList(matching, safe), "en"));
        }
        AudioStream manifest = new AudioStream.Builder().setId("manifest")
                .setContent(CDN, true).setMediaFormat(MediaFormat.M4A)
                .setAudioLocale(Locale.ENGLISH).setDeliveryMethod(DeliveryMethod.DASH).build();
        assertSame(safe, NativeStreamExtractor.bestAudio(Arrays.asList(manifest, safe), "en"));
    }

    @Test
    public void matchingAudioOverridesMuxedAudioWithoutSacrificingVideoCompatibility() {
        VideoStream muxed = video(CDN + "?muxed", "720p", MediaFormat.MPEG_4);
        AudioStream english = audio(CDN + "?english", MediaFormat.M4A, 128, "en",
                AudioTrackType.DUBBED);
        NativeStreamExtractor.Result result = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed),
                Collections.singletonList(video(CDN + "?video", "1080p", MediaFormat.WEBM)),
                Collections.singletonList(english), false, "en");
        assertEquals(muxed.getContent(), result.videoUrl);
        assertEquals(english.getContent(), result.audioUrl);
        NativeStreamExtractor.Result noAdaptive = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed), Collections.emptyList(),
                Collections.singletonList(english), false, "en");
        assertEquals(english.getContent(), noAdaptive.audioUrl);
        NativeStreamExtractor.Result noMatch = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed), Collections.emptyList(),
                Collections.singletonList(english), false, "fr");
        assertEquals(muxed.getContent(), noMatch.videoUrl);
        assertNull(noMatch.audioUrl);
    }

    @Test
    public void adaptiveVideoUsesTheSelectedAudioLanguage() {
        VideoStream adaptive = video(CDN + "?video", "1080p", MediaFormat.MPEG_4);
        AudioStream spanish = audio(CDN + "?es", MediaFormat.WEBMA_OPUS, 64, "es",
                AudioTrackType.DUBBED);
        AudioStream english = audio(CDN + "?en", MediaFormat.M4A, 320, "en",
                AudioTrackType.ORIGINAL);
        NativeStreamExtractor.Result result = NativeStreamExtractor.selectStreams(
                Collections.emptyList(), Collections.singletonList(adaptive),
                Arrays.asList(english, spanish), false, "es");
        assertEquals(adaptive.getContent(), result.videoUrl);
        assertEquals(spanish.getContent(), result.audioUrl);
    }

    @Test
    public void choosesBoundedResolutionAndCompatibleContainer() {
        VideoStream mp4 = video(CDN, "720p", MediaFormat.MPEG_4);
        assertSame(mp4, NativeStreamExtractor.bestVideo(Arrays.asList(
                video(CDN, "2160p", MediaFormat.MPEG_4),
                video(CDN, "1080p", MediaFormat.WEBM), mp4)));
    }

    @Test
    public void neverSelectsUntrustedOrNonProgressiveContent() {
        for (String unsafe : new String[]{
                "http://rr1.googlevideo.com/video", "https://attacker.example/video",
                "https://viewer@rr1.googlevideo.com/video", "file:///video"
        }) {
            assertNull(NativeStreamExtractor.bestVideo(Collections.singletonList(
                    video(unsafe, "720p", MediaFormat.MPEG_4))));
        }
        VideoStream manifest = new VideoStream.Builder().setId("manifest")
                .setContent(CDN, true).setResolution("720p").setMediaFormat(MediaFormat.MPEG_4)
                .setIsVideoOnly(false).setDeliveryMethod(DeliveryMethod.DASH).build();
        assertNull(NativeStreamExtractor.bestVideo(Collections.singletonList(manifest)));
    }

    @Test
    public void pairsAdaptiveVideoWithPreferredAacAudio() {
        AudioStream aac = audio(CDN, MediaFormat.M4A, 128);
        assertSame(aac, NativeStreamExtractor.bestAudio(Arrays.asList(
                audio(CDN, MediaFormat.WEBMA_OPUS, 256), aac,
                audio("http://rr1.googlevideo.com/audio", MediaFormat.M4A, 320))));
        assertNull(NativeStreamExtractor.bestAudio(Collections.emptyList()));
    }

    @Test
    public void selectsHigherResolutionAdaptiveVideoWithAudio() {
        VideoStream muxed = video(CDN + "?track=muxed", "360p", MediaFormat.MPEG_4);
        VideoStream adaptive = new VideoStream.Builder().setId("adaptive")
                .setContent(CDN + "?track=video", true).setResolution("1080p")
                .setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(true).build();
        AudioStream audio = audio(CDN + "?track=audio", MediaFormat.M4A, 128);
        NativeStreamExtractor.Result result = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed), Collections.singletonList(adaptive),
                Collections.singletonList(audio), false);
        assertEquals(adaptive.getContent(), result.videoUrl);
        assertEquals(audio.getContent(), result.audioUrl);
        assertFalse(result.live);
    }

    @Test
    public void preservesMuxedWhenAdaptiveHasNoAudioOrNoQualityBenefit() {
        VideoStream muxed = video(CDN + "?track=muxed", "720p", MediaFormat.MPEG_4);
        AudioStream audio = audio(CDN + "?track=audio", MediaFormat.M4A, 128);
        NativeStreamExtractor.Result sameQuality = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed),
                Collections.singletonList(video(CDN + "?track=video", "720p", MediaFormat.MPEG_4)),
                Collections.singletonList(audio), false);
        assertEquals(muxed.getContent(), sameQuality.videoUrl);
        assertNull(sameQuality.audioUrl);
        NativeStreamExtractor.Result missingAudio = NativeStreamExtractor.selectStreams(
                Collections.singletonList(muxed),
                Collections.singletonList(video(CDN + "?track=video", "1080p", MediaFormat.MPEG_4)),
                Collections.emptyList(), false);
        assertEquals(muxed.getContent(), missingAudio.videoUrl);
        assertNull(missingAudio.audioUrl);
    }

    @Test
    public void preservesLiveClassificationForSelectedStreams() {
        NativeStreamExtractor.Result result = NativeStreamExtractor.selectStreams(
                Collections.singletonList(video(CDN, "720p", MediaFormat.MPEG_4)),
                Collections.emptyList(), Collections.emptyList(), true);
        assertTrue(result.live);
    }

    @Test
    public void recognizesVideoCodecAnywhereInTrimmedCodecList() {
        assertEquals(NativeStreamExtractor.videoCompatibility("video/mp4", "avc1.640028"),
                NativeStreamExtractor.videoCompatibility(
                        "video/mp4", " mp4a.40.2, AVC1.640028 "));
        assertTrue(NativeStreamExtractor.videoCompatibility("video/mp4", "avc3.640028")
                > NativeStreamExtractor.videoCompatibility("video/mp4", "av01.0.08M.08"));
        assertEquals(NativeStreamExtractor.videoCompatibility("video/mp4", null),
                NativeStreamExtractor.videoCompatibility("video/mp4", " "));
        assertTrue(NativeStreamExtractor.videoCompatibility("video/mp4", null)
                > NativeStreamExtractor.videoCompatibility("video/mp4", "av01.0.08M.08"));
    }

    private static VideoStream video(String url, String resolution, MediaFormat format) {
        return new VideoStream.Builder().setId("video").setContent(url, true)
                .setResolution(resolution).setMediaFormat(format).setIsVideoOnly(false).build();
    }

    private static AudioStream audio(String url, MediaFormat format, int bitrate) {
        return audio(url, format, bitrate, null, null);
    }

    private static AudioStream audio(String url, MediaFormat format, int bitrate, String language,
                                     AudioTrackType type) {
        return new AudioStream.Builder().setId("audio").setContent(url, true)
                .setMediaFormat(format).setAverageBitrate(bitrate)
                .setAudioLocale(language == null ? null : Locale.forLanguageTag(language))
                .setAudioTrackType(type).build();
    }
}
