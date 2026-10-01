package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class MusicServerProtocolTest {
    private static final String ORIGIN = "https://music.example";
    private static final String VIDEO = "a1b2c3d4e5_";
    private static final String OTHER_VIDEO = "f6g7h8i9j0-";
    private static final long NOW = 1_000_000L;

    @Test
    public void normalizesOnlyHttpsOrigins() throws Exception {
        assertEquals(ORIGIN, MusicServerProtocol.normalizeOrigin(" HTTPS://Music.Example:443/ "));
        assertEquals(ORIGIN, MusicServerProtocol.normalizeOrigin(ORIGIN));
        assertEquals(ORIGIN + ":8443",
                MusicServerProtocol.normalizeOrigin(ORIGIN + ":8443/"));
        assertEquals("https://[2001:db8::1]:8443",
                MusicServerProtocol.normalizeOrigin("https://[2001:db8::1]:8443/"));
    }

    @Test
    public void rejectsNonOriginsAndAmbiguousAuthorities() {
        for (String value : new String[] {
                null, "", "music.example", "//music.example", "http://music.example",
                "https:music.example", "https:///music.example", "https://",
                ORIGIN + "/app-login", ORIGIN + "//", ORIGIN + "/%2f", ORIGIN + "/..",
                ORIGIN + "?x=1", ORIGIN + "?", ORIGIN + "#", ORIGIN + "#fragment",
                "https://user@music.example", "https://user:" + "fixture@music.example",
                "https://music.example\\@other.example", "https://music%2eexample",
                ORIGIN + ":", ORIGIN + ":0443", ORIGIN + ":0", ORIGIN + ":65536",
                ORIGIN + ":-1", ORIGIN + "\n", ORIGIN + "\r\nInjected: value"
        }) {
            IOException error = assertThrows(value, IOException.class,
                    () -> MusicServerProtocol.normalizeOrigin(value));
            assertNull(error.getCause());
        }
    }

    @Test
    public void generatesIndependent32ByteVerifierAndStateForEveryLogin() throws Exception {
        MusicServerProtocol.Pending first = MusicServerProtocol.newLogin(ORIGIN + "/", NOW);
        MusicServerProtocol.Pending second = MusicServerProtocol.newLogin(ORIGIN, NOW);
        assertEquals(ORIGIN, first.origin);
        assertEquals(NOW, first.createdAt);
        for (String value : new String[] {
                first.verifier, first.state, second.verifier, second.state
        }) {
            assertEquals(43, value.length());
            assertTrue(value.matches("[A-Za-z0-9_-]+"));
            assertEquals(32, Base64.getUrlDecoder().decode(value).length);
        }
        assertNotEquals(first.verifier, first.state);
        assertNotEquals(first.verifier, second.verifier);
        assertNotEquals(first.state, second.state);
    }

    @Test
    public void authorizationUrlContainsOnlyChallengeStateAndExactRedirect() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(pending.verifier.getBytes(StandardCharsets.US_ASCII)));
        String url = MusicServerProtocol.authorizationUrl(pending);
        assertEquals(ORIGIN + "/app-login"
                + "?redirect_uri=com.ssytdlp.app%3A%2Foauth%2Fcallback"
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256&state=" + pending.state, url);
        assertFalse(url.contains(pending.verifier));
        assertNull(new URI(MusicServerProtocol.REDIRECT_URI).getRawAuthority());
        assertEquals("/oauth/callback", new URI(MusicServerProtocol.REDIRECT_URI).getRawPath());
    }

    @Test
    public void acceptsMatchingCallbackRegardlessOfParameterOrder() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        assertEquals("synthetic_code", MusicServerProtocol.callbackCode(
                callback("code=synthetic_code&state=" + pending.state), pending, NOW));
        assertEquals("synthetic_code", MusicServerProtocol.callbackCode(
                callback("state=" + pending.state + "&code=synthetic%5fcode"),
                pending, NOW + 1));
    }

    @Test
    public void rejectsCallbackSchemesAuthoritiesPathsAndFragments() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        String query = "?code=synthetic_code&state=" + pending.state;
        for (String value : new String[] {
                null, "", "https://music.example/oauth/callback" + query,
                "COM.SSYTDLP.APP:/oauth/callback" + query,
                "com.ssytdlp.app://oauth/callback" + query,
                "com.ssytdlp.app:///oauth/callback" + query,
                "com.ssytdlp.app://user@music.example/oauth/callback" + query,
                "com.ssytdlp.app:oauth/callback" + query,
                "com.ssytdlp.app:/oauth/%63allback" + query,
                "com.ssytdlp.app:/oauth/callback/" + query,
                "com.ssytdlp.app:/oauth/../oauth/callback" + query,
                MusicServerProtocol.REDIRECT_URI + query + "#",
                MusicServerProtocol.REDIRECT_URI + query + "#fragment",
                " " + MusicServerProtocol.REDIRECT_URI + query
        }) {
            assertThrows(IOException.class,
                    () -> MusicServerProtocol.callbackCode(value, pending, NOW));
        }
    }

    @Test
    public void rejectsMissingUnknownDuplicateAndMalformedCallbackParameters() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        String valid = "code=synthetic_code&state=" + pending.state;
        for (String query : new String[] {
                "", "code=synthetic_code", "state=" + pending.state,
                "code=&state=" + pending.state, "code=synthetic_code&state=",
                "code=synthetic_code&state=" + synthetic('C', 43),
                valid + "&state=" + pending.state, valid + "&%73tate=" + pending.state,
                valid + "&code=synthetic_code", valid + "&%63ode=synthetic_code",
                valid + "&origin=https://other.example", valid + "&token=synthetic",
                valid + "&error=access_denied", valid + "&", valid + "&&",
                "code=bad%ZZ&state=" + pending.state,
                "code=bad%ff&state=" + pending.state,
                "code=bad%0a&state=" + pending.state,
                "code=bad+code&state=" + pending.state
        }) {
            IOException error = assertThrows(IOException.class,
                    () -> MusicServerProtocol.callbackCode(callback(query), pending, NOW));
            assertFalse(error.getMessage().contains(pending.state));
            assertFalse(error.getMessage().contains(pending.verifier));
            assertFalse(error.getMessage().contains("synthetic_code"));
            assertNull(error.getCause());
        }
    }

    @Test
    public void rejectsUnsolicitedExpiredAndFutureDatedLogins() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        String callback = callback("code=synthetic_code&state=" + pending.state);
        assertThrows(IOException.class,
                () -> MusicServerProtocol.callbackCode(callback, null, NOW));
        assertThrows(IOException.class,
                () -> MusicServerProtocol.callbackCode(callback, pending, NOW - 1));
        assertThrows(IOException.class, () -> MusicServerProtocol.callbackCode(
                callback, pending, NOW + MusicServerProtocol.LOGIN_MAX_AGE_MS));
        assertEquals("synthetic_code", MusicServerProtocol.callbackCode(
                callback, pending, NOW + MusicServerProtocol.LOGIN_MAX_AGE_MS - 1));
    }

    @Test
    public void validatesRestoredPendingAndSessionValues() throws Exception {
        for (String value : new String[] {null, "", "short", synthetic('A', 129), synthetic('\n', 43)}) {
            assertThrows(IOException.class, () -> new MusicServerProtocol.Pending(
                    ORIGIN, value, synthetic('B', 43), NOW));
            assertThrows(IOException.class, () -> new MusicServerProtocol.Pending(
                    ORIGIN, synthetic('A', 43), value, NOW));
            assertThrows(IOException.class,
                    () -> new MusicServerProtocol.Session(ORIGIN, value, NOW));
        }
        assertThrows(IOException.class, () -> new MusicServerProtocol.Pending(
                ORIGIN, synthetic('A', 43), synthetic('B', 43), -1));
        assertThrows(IOException.class,
                () -> new MusicServerProtocol.Session(ORIGIN, synthetic('B', 43), 0));
        assertThrows(IOException.class, () -> new MusicServerProtocol.Session(
                "http://music.example", synthetic('B', 43), NOW));
        MusicServerProtocol.Session session = new MusicServerProtocol.Session(
                ORIGIN + "/", synthetic('B', 43), NOW);
        assertEquals(ORIGIN, session.origin);
        assertEquals(NOW, session.expiresAt);
    }

    @Test
    public void preservesValidatedPlaylistPagesExactly() {
        for (String url : new String[] {
                "https://music.youtube.com/playlist?list=PL_fixture_123",
                "https://www.youtube.com/watch?v=" + VIDEO + "&list=PL_fixture_123&index=2&t=40#t=8",
                "https://m.youtube.com/playlist?list=RD" + VIDEO,
                "https://youtube.com/playlist?%6cist=PL_fixture_123&si=source%2Bvalue",
                "https://youtu.be/" + VIDEO + "?list=PL_fixture_123"
        }) {
            assertEquals(url, MusicServerProtocol.playlistUrl(url));
        }
    }

    @Test
    public void rejectsInvalidPlaylistUrls() {
        for (String url : new String[] {
                null, "", "https://www.youtube.com/playlist",
                "https://www.youtube.com/playlist?list=",
                "https://www.youtube.com/playlist?list=bad%20id",
                "https://www.youtube.com/playlist?list=bad%2fid",
                "https://www.youtube.com/playlist?list=PL_good&list=PL_other",
                "https://www.youtube.com/playlist?list=PL_good&%6cist=PL_other",
                "https://www.youtube.com/playlist?list=" + synthetic('A', 257),
                "https://www.youtube.com/playlist?list=PL_good&broken=%ff",
                "http://www.youtube.com/playlist?list=PL_good",
                "https://www.youtube.com.evil.example/playlist?list=PL_good",
                "https://other.example@www.youtube.com/playlist?list=PL_good",
                "https://www.youtube.com:8443/playlist?list=PL_good",
                "https://www.youtube.com:/playlist?list=PL_good"
        }) {
            assertNull(url, MusicServerProtocol.playlistUrl(url));
        }
    }

    @Test
    public void preservesExternalPlaylistAndIndexWhenCanonicalizingVideoLinks() {
        for (String canonical : new String[] {
                "https://www.youtube.com/watch?v=" + VIDEO + "&t=30&app=desktop",
                "https://m.youtube.com/watch?v=" + VIDEO + "&t=30"
        }) {
            for (String source : new String[] {
                    "https://youtu.be/" + VIDEO + "?list=PL_fixture&index=12&si=external",
                    "https://www.youtube.com/watch?v=" + VIDEO + "&list=PL_fixture&index=12",
                    "https://music.youtube.com/watch?v=" + VIDEO + "&%6cist=PL_fixture&%69ndex=12"
            }) {
                assertEquals(canonical + "&list=PL_fixture&index=12",
                        MusicServerProtocol.preservePlaylist(canonical, source));
            }
        }
    }

    @Test
    public void preservesPlaylistWithoutUntrustedOrAmbiguousOptionalIndex() {
        String canonical = "https://www.youtube.com/watch?v=" + VIDEO;
        String source = "https://youtu.be/" + VIDEO + "?list=PL_fixture";
        for (String extra : new String[] {
                "", "&index=", "&index=-1", "&index=1.5", "&index=1e2",
                "&index=9999999999", "&index=%2b1", "&index=1%26list%3dPL_other",
                "&index=1&index=2", "&index=1&%69ndex=2"
        }) {
            assertEquals(canonical + "&list=PL_fixture",
                    MusicServerProtocol.preservePlaylist(canonical, source + extra));
        }
        assertEquals(canonical + "&list=PL_fixture&index=0",
                MusicServerProtocol.preservePlaylist(canonical, source + "&index=0"));
    }

    @Test
    public void playlistPreservationIsIdempotentAndKeepsCanonicalQueryAndFragment() {
        String canonical = "https://www.youtube.com/watch?v=" + VIDEO
                + "&t=42&si=canonical%2Bsource#t=45";
        String source = "https://youtu.be/" + VIDEO + "?list=PL_fixture&index=3&t=999#t=999";
        String expected = "https://www.youtube.com/watch?v=" + VIDEO
                + "&t=42&si=canonical%2Bsource&list=PL_fixture&index=3#t=45";
        assertEquals(expected, MusicServerProtocol.preservePlaylist(canonical, source));
        assertEquals(expected, MusicServerProtocol.preservePlaylist(expected, source));
        assertEquals(expected, MusicServerProtocol.preservePlaylist(
                expected.replace("PL_fixture", "PL_old").replace("index=3", "index=9"), source));
        assertEquals("https://www.youtube.com/playlist?list=PL_fixture&index=3#top",
                MusicServerProtocol.preservePlaylist("https://www.youtube.com/playlist#top", source));
    }

    @Test
    public void ignoresMissingInvalidAndUntrustedSourcePlaylistsWhenCanonicalizing() {
        String canonical = "https://www.youtube.com/watch?v=" + VIDEO + "&t=30";
        for (String source : new String[] {
                null, "", "https://youtu.be/" + VIDEO,
                "https://youtu.be/" + VIDEO + "?list=",
                "https://youtu.be/" + VIDEO + "?list=PL_fixture&list=PL_other",
                "https://youtu.be/" + VIDEO + "?list=PL_fixture&%6cist=PL_other",
                "https://youtu.be/" + VIDEO + "?list=PL_fixture%26index%3d2",
                "https://youtu.be/" + VIDEO + "?list=PL_fixture&index=%ff",
                "http://youtu.be/" + VIDEO + "?list=PL_fixture",
                "https://other.example/watch?v=" + VIDEO + "&list=PL_fixture",
                "https://user@www.youtube.com/watch?v=" + VIDEO + "&list=PL_fixture"
        }) {
            assertEquals(canonical, MusicServerProtocol.preservePlaylist(canonical, source));
        }
    }

    @Test
    public void doesNotAttachSourcePlaylistsToInvalidCanonicalDestinations() {
        String source = "https://youtu.be/" + VIDEO + "?list=PL_fixture";
        for (String canonical : new String[] {
                null, "", "https://other.example/", "http://www.youtube.com/watch?v=" + VIDEO,
                "https://user@www.youtube.com/watch?v=" + VIDEO
        }) {
            assertEquals(canonical, MusicServerProtocol.preservePlaylist(canonical, source));
        }
    }

    @Test
    public void stripsPlaylistParametersButPreservesIndependentRawQueryAndFragment() {
        String url = "https://www.youtube.com/watch?list=PL_fixture&v=" + VIDEO
                + "&index=3&t=45&start_radio=1&radio=1&playnext=1"
                + "&si=a%2Bb&feature=share&shuffle=1&playlist=PL_other"
                + "&listType=playlist&list_type=playlist&start=12&end=99&autoplay=0#t=50";
        assertEquals("https://www.youtube.com/watch?v=" + VIDEO
                        + "&t=45&si=a%2Bb&feature=share&start=12&end=99&autoplay=0#t=50",
                MusicServerProtocol.mediaUrl(url, VIDEO));
        assertEquals("https://www.youtube.com/watch?v=" + VIDEO,
                MusicServerProtocol.mediaUrl("https://www.youtube.com/watch?%6cist=PL_fixture"
                        + "&v=" + VIDEO + "&INDEX=2&list=PL_second", null));
    }

    @Test
    public void supportsShortsLiveEmbedAndShortlinks() {
        for (String base : new String[] {
                "https://www.youtube.com/shorts/", "https://m.youtube.com/shorts/",
                "https://www.youtube.com/live/", "https://www.youtube.com/embed/",
                "https://youtu.be/"
        }) {
            assertEquals(base + VIDEO + "?t=30&si=source#t=8",
                    MusicServerProtocol.mediaUrl(base + VIDEO
                            + "?list=PL_fixture&index=2&t=30&si=source#t=8", null));
            assertEquals(base + VIDEO + "/", MusicServerProtocol.mediaUrl(base + VIDEO + "/", VIDEO));
        }
    }

    @Test
    public void usesNativeVideoWhileBrowsingElsewhere() {
        String expected = "https://www.youtube.com/watch?v=" + VIDEO;
        for (String page : new String[] {
                null, "https://www.youtube.com/", "https://www.youtube.com/playlist?list=PL_fixture",
                "https://www.youtube.com/watch?v=" + OTHER_VIDEO,
                "https://www.youtube.com/shorts/" + OTHER_VIDEO,
                "https://other.example/watch?v=" + VIDEO,
                "https://www.youtube.com/watch?v=broken"
        }) {
            assertEquals(expected, MusicServerProtocol.mediaUrl(page, VIDEO));
        }
    }

    @Test
    public void invalidNativeIdNeverReplacesValidPageVideo() {
        String page = "https://www.youtube.com/watch?v=" + VIDEO + "&t=42";
        assertEquals(page, MusicServerProtocol.mediaUrl(page, "invalid"));
        assertEquals(page, MusicServerProtocol.mediaUrl(page, null));
        assertNull(MusicServerProtocol.mediaUrl("https://www.youtube.com/", "invalid"));
    }

    @Test
    public void rejectsNonVideosAndUntrustedVideoUrls() {
        for (String page : new String[] {
                null, "https://www.youtube.com/playlist?list=PL_fixture",
                "https://www.youtube.com/shorts", "https://www.youtube.com/watch",
                "https://www.youtube.com/watch?v=" + VIDEO + "&v=" + VIDEO,
                "https://www.youtube.com/watch?v=" + VIDEO + "&%76=" + VIDEO,
                "https://www.youtube.com/shorts/" + VIDEO + "/extra",
                "https://www.youtube.com/embed/videoseries?list=PL_fixture",
                "http://www.youtube.com/watch?v=" + VIDEO,
                "https://www.youtube.com.evil.example/watch?v=" + VIDEO,
                "https://user@www.youtube.com/watch?v=" + VIDEO,
                "https://www.youtube.com:8443/watch?v=" + VIDEO,
                "https://www.youtube.com/watch?v=" + VIDEO + "&si=%ff"
        }) {
            assertNull(page, MusicServerProtocol.mediaUrl(page, null));
        }
    }

    private static MusicServerProtocol.Pending pending() throws IOException {
        return new MusicServerProtocol.Pending(
                ORIGIN, synthetic('A', 43), synthetic('B', 43), NOW);
    }

    private static String callback(String query) {
        return MusicServerProtocol.REDIRECT_URI + "?" + query;
    }

    private static String synthetic(char value, int length) {
        char[] result = new char[length];
        Arrays.fill(result, value);
        return new String(result);
    }
}
