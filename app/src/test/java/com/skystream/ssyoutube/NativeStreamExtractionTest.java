package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NativeStreamExtractionTest {
    private static final String CDN = "https://rr1.googlevideo.com/videoplayback";
    private static final String HLS = "https://manifest.googlevideo.com/live.m3u8";
    private static final String DASH = "https://manifest.googlevideo.com/live.mpd";

    @Before
    public void initializeDownloaderWithoutFetching() throws Exception {
        NativeStreamExtractor.localizedExtractor("dQw4w9WgXcQ", "en");
    }

    @Test
    public void onDemandReadsOnlyPlaybackFieldsAndDoesNotInspectManifests() throws Exception {
        Fixture extractor = new Fixture();
        extractor.muxed = Collections.singletonList(video());
        extractor.hls = HLS;
        NativeStreamExtractor.Result result = extract(extractor);
        assertEquals(CDN, result.videoUrl);
        assertFalse(result.live);
        assertEquals(Arrays.asList("fetch", "type", "audio", "muxed", "video"), extractor.calls);
    }

    @Test
    public void liveHlsSkipsAllDirectStreamListsAndDash() throws Exception {
        for (StreamType type : new StreamType[]{StreamType.LIVE_STREAM,
                StreamType.AUDIO_LIVE_STREAM}) {
            Fixture extractor = new Fixture();
            extractor.type = type;
            extractor.hls = HLS;
            NativeStreamExtractor.Result result = extract(extractor);
            assertEquals(NativeStreamExtractor.Kind.HLS, result.kind);
            assertEquals(HLS, result.videoUrl);
            assertTrue(result.live);
            assertEquals(Arrays.asList("fetch", "type", "hls"), extractor.calls);
        }
    }

    @Test
    public void unsafeOrMissingLiveHlsFallsBackToDashWithoutDirectStreamWork() throws Exception {
        for (String hls : new String[]{"", "http://rr1.googlevideo.com/live.m3u8",
                "https://untrusted.example/live.m3u8"}) {
            Fixture extractor = new Fixture();
            extractor.type = StreamType.LIVE_STREAM;
            extractor.hls = hls;
            extractor.dash = DASH;
            NativeStreamExtractor.Result result = extract(extractor);
            assertEquals(NativeStreamExtractor.Kind.DASH, result.kind);
            assertEquals(DASH, result.videoUrl);
            assertTrue(result.live);
            assertEquals(Arrays.asList("fetch", "type", "hls", "dash"), extractor.calls);
        }
    }

    @Test
    public void liveWithoutManifestsStillUsesDirectStreams() throws Exception {
        Fixture extractor = new Fixture();
        extractor.type = StreamType.LIVE_STREAM;
        extractor.muxed = Collections.singletonList(video());
        NativeStreamExtractor.Result result = extract(extractor);
        assertEquals(CDN, result.videoUrl);
        assertTrue(result.live);
        assertEquals(Arrays.asList("fetch", "type", "hls", "dash", "audio", "muxed", "video"),
                extractor.calls);
    }

    @Test
    public void onDemandWithoutDirectStreamsFallsBackToManifest() throws Exception {
        Fixture extractor = new Fixture();
        extractor.dash = DASH;
        NativeStreamExtractor.Result result = extract(extractor);
        assertEquals(NativeStreamExtractor.Kind.DASH, result.kind);
        assertFalse(result.live);
        assertEquals(Arrays.asList("fetch", "type", "audio", "muxed", "video", "hls", "dash"),
                extractor.calls);
    }

    @Test
    public void unavailableFormatsAreReportedAndDoNotHidePlayableAlternatives() throws Exception {
        Fixture extractor = new Fixture();
        extractor.audioFailure = new ParsingException("Audio format unavailable");
        extractor.muxed = Collections.singletonList(video());
        List<Exception> warnings = new ArrayList<>();
        NativeStreamExtractor.Result result = NativeStreamExtractor.extract(extractor, "en",
                new ExtractorDownloader.Cancellation(), warnings::add);
        assertEquals(CDN, result.videoUrl);
        assertEquals(Collections.singletonList(extractor.audioFailure), warnings);

        Fixture live = new Fixture();
        live.type = StreamType.LIVE_STREAM;
        live.hlsFailure = new ParsingException("HLS unavailable");
        live.dash = DASH;
        warnings.clear();
        assertEquals(DASH, NativeStreamExtractor.extract(live, "en",
                new ExtractorDownloader.Cancellation(), warnings::add).videoUrl);
        assertEquals(Collections.singletonList(live.hlsFailure), warnings);
    }

    @Test
    public void candidateTimeoutIsReportedAndCanFallBackWhenNotCancelled() throws Exception {
        Fixture extractor = new Fixture();
        extractor.audioIoFailure = new SocketTimeoutException("Audio request timed out");
        extractor.muxed = Collections.singletonList(video());
        List<Exception> warnings = new ArrayList<>();
        assertEquals(CDN, NativeStreamExtractor.extract(extractor, "en",
                new ExtractorDownloader.Cancellation(), warnings::add).videoUrl);
        assertEquals(Collections.singletonList(extractor.audioIoFailure), warnings);
    }

    @Test
    public void unsupportedContentAndPageFailuresAreNotHiddenByFallback() throws Exception {
        Fixture extractor = new Fixture();
        extractor.audioFailure = new ContentNotSupportedException("Unsupported content");
        extractor.muxed = Collections.singletonList(video());
        assertSame(extractor.audioFailure,
                assertThrows(ContentNotSupportedException.class, () -> extract(extractor)));
        assertEquals(Arrays.asList("fetch", "type", "audio"), extractor.calls);

        Fixture failed = new Fixture();
        failed.fetchFailure = new IOException("Page unavailable");
        assertSame(failed.fetchFailure, assertThrows(IOException.class, () -> extract(failed)));
        assertEquals(Collections.singletonList("fetch"), failed.calls);
    }

    @Test
    public void noPlayableStreamsFailsWithoutRepeatingLiveManifestReads() throws Exception {
        Fixture extractor = new Fixture();
        extractor.type = StreamType.LIVE_STREAM;
        assertThrows(IOException.class, () -> extract(extractor));
        assertEquals(Arrays.asList("fetch", "type", "hls", "dash", "audio", "muxed", "video"),
                extractor.calls);
    }

    @Test
    public void invalidStreamTypeDoesNotTryPlayableCandidates() throws Exception {
        Fixture extractor = new Fixture();
        extractor.type = StreamType.NONE;
        extractor.muxed = Collections.singletonList(video());
        assertThrows(IOException.class, () -> extract(extractor));
        assertEquals(Arrays.asList("fetch", "type"), extractor.calls);
    }

    @Test
    public void cancellationStopsBeforeFetchAndImmediatelyAfterFetch() throws Exception {
        ExtractorDownloader.Cancellation cancellation = new ExtractorDownloader.Cancellation();
        cancellation.cancel();
        Fixture before = new Fixture();
        assertThrows(InterruptedIOException.class, () -> NativeStreamExtractor.extract(before,
                "en", cancellation, warning -> fail("Cancellation must not be a warning")));
        assertTrue(before.calls.isEmpty());

        ExtractorDownloader.Cancellation during = new ExtractorDownloader.Cancellation();
        Fixture after = new Fixture();
        after.onFetch = during::cancel;
        assertThrows(InterruptedIOException.class, () -> NativeStreamExtractor.extract(after,
                "en", during, warning -> fail("Cancellation must not be a warning")));
        assertEquals(Collections.singletonList("fetch"), after.calls);
    }

    @Test
    public void cancellationDuringCandidateReadNeverReturnsAStreamOrContinuesFallback()
            throws Exception {
        for (boolean failure : new boolean[]{false, true}) {
            ExtractorDownloader.Cancellation cancellation = new ExtractorDownloader.Cancellation();
            Fixture extractor = new Fixture();
            extractor.onAudio = cancellation::cancel;
            extractor.audioFailure = failure ? new ParsingException("Cancelled request") : null;
            extractor.muxed = Collections.singletonList(video());
            assertThrows(InterruptedIOException.class, () -> NativeStreamExtractor.extract(extractor,
                    "en", cancellation, warning -> fail("Cancellation must not be a warning")));
            assertEquals(Arrays.asList("fetch", "type", "audio"), extractor.calls);
        }
    }

    private static NativeStreamExtractor.Result extract(Fixture extractor) throws Exception {
        return NativeStreamExtractor.extract(extractor, "en",
                new ExtractorDownloader.Cancellation(), warning -> {
                    throw new AssertionError("Unexpected extraction warning", warning);
                });
    }

    private static VideoStream video() {
        return new VideoStream.Builder().setId("muxed").setContent(CDN, true)
                .setResolution("720p").setMediaFormat(MediaFormat.MPEG_4)
                .setIsVideoOnly(false).build();
    }

    private static final class Fixture extends StreamExtractor {
        final List<String> calls = new ArrayList<>();
        StreamType type = StreamType.VIDEO_STREAM;
        List<VideoStream> muxed = Collections.emptyList();
        String hls = "";
        String dash = "";
        IOException fetchFailure;
        IOException audioIoFailure;
        ExtractionException audioFailure;
        ParsingException hlsFailure;
        Runnable onFetch = () -> { };
        Runnable onAudio = () -> { };

        Fixture() throws Exception {
            super(ServiceList.YouTube, ServiceList.YouTube.getStreamLHFactory()
                    .fromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        }

        @Override public void onFetchPage(Downloader downloader) throws IOException {
            calls.add("fetch");
            onFetch.run();
            if (fetchFailure != null) {
                throw fetchFailure;
            }
        }

        @Override public StreamType getStreamType() {
            assertPageFetched();
            calls.add("type");
            return type;
        }

        @Override public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
            calls.add("audio");
            onAudio.run();
            if (audioIoFailure != null) {
                throw audioIoFailure;
            }
            if (audioFailure != null) {
                throw audioFailure;
            }
            return Collections.emptyList();
        }

        @Override public List<VideoStream> getVideoStreams() {
            calls.add("muxed");
            return muxed;
        }

        @Override public List<VideoStream> getVideoOnlyStreams() {
            calls.add("video");
            return Collections.emptyList();
        }

        @Override public String getHlsUrl() throws ParsingException {
            calls.add("hls");
            if (hlsFailure != null) {
                throw hlsFailure;
            }
            return hls;
        }

        @Override public String getDashMpdUrl() {
            calls.add("dash");
            return dash;
        }

        @Override public String getName() {
            throw new AssertionError("Playback must not aggregate metadata");
        }

        @Override public List<Image> getThumbnails() {
            throw new AssertionError("Playback must not aggregate thumbnails");
        }

        @Override public String getUploaderUrl() {
            throw new AssertionError("Playback must not aggregate uploader metadata");
        }

        @Override public String getUploaderName() {
            throw new AssertionError("Playback must not aggregate uploader metadata");
        }
    }
}
