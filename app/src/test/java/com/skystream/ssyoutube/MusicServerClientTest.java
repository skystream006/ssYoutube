package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Transport/schema tests use a JSON seam; Android's actual JSON codec needs device coverage. */
public class MusicServerClientTest {
    private static final String ORIGIN = "https://music.example:8443";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=a1b2c3d4e5_";
    private static final String CODE = "synthetic_authorization_code";
    private CookieHandler originalCookies;

    @Before
    public void isolateCookies() {
        originalCookies = CookieHandler.getDefault();
        CookieHandler.setDefault(null);
    }

    @After
    public void restoreCookies() {
        CookieHandler.setDefault(originalCookies);
        Thread.interrupted();
    }

    @Test
    public void exchangesOnlyAtPendingOriginWithExactPayloadAndNoAuthentication() throws Exception {
        MusicServerProtocol.Pending pending = pending();
        long expiry = System.currentTimeMillis() + 60_000;
        FakeConnection connection = new FakeConnection(200, sessionReply(expiry));
        connection.json.response = sessionResponse(expiry);
        List<URI> requests = new ArrayList<>();
        MusicServerProtocol.Session session = client(connection, requests).exchange(pending, CODE);

        assertEquals(Arrays.asList(URI.create(ORIGIN + "/api/auth/app/token")), requests);
        Map<?, ?> payload = connection.json.encodedValues;
        assertEquals(3, payload.size());
        assertEquals(CODE, payload.get("code"));
        assertEquals(pending.verifier, payload.get("codeVerifier"));
        assertEquals(MusicServerProtocol.REDIRECT_URI, payload.get("redirectUri"));
        assertEquals(FakeJson.ENCODED_BODY, connection.requestBody());
        assertEquals(sessionReply(expiry), connection.json.parsedBody);
        assertFalse(connection.requestBody().contains(pending.state));
        assertNull(connection.getRequestProperty("Authorization"));
        assertEquals(ORIGIN, session.origin);
        assertEquals(token(), session.token);
        assertEquals(expiry, session.expiresAt);
        assertTransportPolicy(connection);
    }

    @Test
    public void postsVideoJobsWithOnlyBearerAuthenticationAndChosenUrl() throws Exception {
        for (String url : new String[] {
                VIDEO_URL + "&t=30&si=a%2Bb",
                "https://music.youtube.com/playlist?list=PL_fixture_123",
                VIDEO_URL + "&list=PL_fixture_123&index=2"
        }) {
            FakeConnection connection = new FakeConnection(202, "{\"id\":\"fixture\",\"status\":\"queued\"}");
            List<URI> requests = new ArrayList<>();
            client(connection, requests).createJob(session(), url);

            assertEquals(Arrays.asList(URI.create(ORIGIN + "/api/jobs")), requests);
            Map<?, ?> payload = connection.json.encodedValues;
            assertEquals(2, payload.size());
            assertEquals(url, payload.get("url"));
            assertEquals("video", payload.get("downloadType"));
            assertEquals(FakeJson.ENCODED_BODY, connection.requestBody());
            assertEquals("Bearer " + token(), connection.getRequestProperty("Authorization"));
            assertFalse(connection.requestBody().contains(token()));
            assertNull(connection.getRequestProperty("X-PAT"));
            assertTransportPolicy(connection);
        }
    }

    @Test
    public void boundsKnownAndUnknownResponseSizesAndClosesStreams() throws Exception {
        for (String length : new String[] {
                "0", Integer.toString(MusicServerClient.MAX_RESPONSE_BYTES + 1),
                "-1", "invalid", "999999999999999999999", "2, 2"
        }) {
            FakeConnection connection = new FakeConnection(200, sessionReply(future()));
            connection.headers.put("Content-Length", length);
            assertThrows(IOException.class, () -> client(connection).exchange(pending(), CODE));
            assertFalse(connection.read);
            assertTrue(connection.disconnected);
        }
        FakeConnection oversized = new FakeConnection(200, new byte[MusicServerClient.MAX_RESPONSE_BYTES + 20]);
        assertThrows(IOException.class, () -> client(oversized).exchange(pending(), CODE));
        assertTrue(oversized.bytesRead <= MusicServerClient.MAX_RESPONSE_BYTES + 1);
        assertTrue(oversized.inputClosed);
        assertTrue(oversized.disconnected);
    }

    @Test
    public void acceptsReplyAtSizeLimitAndChecksContentLength() throws Exception {
        String body = sessionReply(future());
        String padded = body + synthetic(' ', MusicServerClient.MAX_RESPONSE_BYTES - body.length());
        FakeConnection exact = new FakeConnection(200, padded);
        exact.headers.put("Content-Length", Integer.toString(MusicServerClient.MAX_RESPONSE_BYTES));
        assertEquals(token(), client(exact).exchange(pending(), CODE).token);
        assertTrue(exact.inputClosed);
        for (String length : new String[] {"1", Integer.toString(body.length() + 1)}) {
            FakeConnection wrong = new FakeConnection(200, body);
            wrong.headers.put("Content-Length", length);
            assertThrows(IOException.class, () -> client(wrong).exchange(pending(), CODE));
            assertTrue(wrong.inputClosed);
            assertTrue(wrong.disconnected);
        }
    }

    @Test
    public void propagatesHttpStatusWithoutReadingBodyFollowingRedirectOrRetrying() throws Exception {
        for (int status : new int[] {200, 201, 204, 301, 302, 303, 307, 308, 400, 401, 403, 409, 429, 500}) {
            FakeConnection connection = new FakeConnection(status, "server details " + token());
            connection.headers.put("Location", "https://other.example/collect");
            List<URI> requests = new ArrayList<>();
            MusicServerClient.HttpFailure error = assertThrows(MusicServerClient.HttpFailure.class,
                    () -> client(connection, requests).createJob(session(), VIDEO_URL));
            assertEquals(status, error.status);
            assertEquals("Music server request failed (HTTP " + status + ")", error.getMessage());
            assertNull(error.getCause());
            assertFalse(connection.read);
            assertFalse(connection.errorRead);
            assertEquals(1, requests.size());
            assertFalse(connection.getInstanceFollowRedirects());
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void requiresExactly200ForExchange() throws Exception {
        for (int status : new int[] {201, 202, 204, 302, 400, 401, 403, 429, 500}) {
            FakeConnection connection = new FakeConnection(status, sessionReply(future()));
            MusicServerClient.HttpFailure error = assertThrows(MusicServerClient.HttpFailure.class,
                    () -> client(connection).exchange(pending(), CODE));
            assertEquals(status, error.status);
            assertFalse(connection.read);
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void rejectsMissingExpiredAndWronglyTypedDecodedSessions() throws Exception {
        for (Object value : new Object[] {null, "invalid", new ArrayList<>(), new HashMap<>()}) {
            FakeConnection connection = new FakeConnection(200, "{}");
            connection.json.response.put("session", value);
            assertThrows(IOException.class, () -> client(connection).exchange(pending(), CODE));
            assertTrue(connection.disconnected);
        }
        for (Object[] field : new Object[][] {
                {"tokenType", "Basic"}, {"tokenType", 12}, {"token", 12},
                {"token", "short"}, {"token", token() + "\r\nHeader: value"},
                {"expiresAt", Instant.ofEpochMilli(1).toString()},
                {"expiresAt", Instant.ofEpochMilli(System.currentTimeMillis() - 1000).toString()},
                {"expiresAt", 123}, {"expiresAt", "bad"}
        }) {
            FakeConnection connection = new FakeConnection(200, "{}");
            Map<String, Object> fields = sessionFields(future());
            fields.put((String) field[0], field[1]);
            connection.json.response.put("session", fields);
            IOException error = assertThrows(IOException.class,
                    () -> client(connection).exchange(pending(), CODE));
            assertFalse(error.getMessage().contains(token()));
            assertNull(error.getCause());
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void rejectsExcessivelyNestedAndInvalidUtf8Replies() throws Exception {
        String nested = "{\"unused\":" + synthetic('[', 100) + "0" + synthetic(']', 100) + "}";
        for (byte[] body : new byte[][] {
                nested.getBytes(StandardCharsets.UTF_8),
                new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xff, '"', '}'}
        }) {
            FakeConnection connection = new FakeConnection(200, body);
            assertThrows(IOException.class, () -> client(connection).exchange(pending(), CODE));
            assertTrue(connection.inputClosed);
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void rejectsNonJsonAndCompressedSuccessesBeforeReading() throws Exception {
        for (String type : new String[] {"text/html", "text/plain", null}) {
            FakeConnection connection = new FakeConnection(200, sessionReply(future()));
            connection.headers.put("Content-Type", type);
            assertThrows(IOException.class, () -> client(connection).exchange(pending(), CODE));
            assertFalse(connection.read);
            assertTrue(connection.disconnected);
        }
        FakeConnection compressed = new FakeConnection(200, sessionReply(future()));
        compressed.headers.put("Content-Encoding", "gzip");
        assertThrows(IOException.class, () -> client(compressed).exchange(pending(), CODE));
        assertFalse(compressed.read);
        assertTrue(compressed.disconnected);
    }

    @Test
    public void propagatesJsonDecoderFailuresWithoutLeakingResponseContent() throws Exception {
        for (int status : new int[] {200, 202}) {
            FakeConnection connection = new FakeConnection(status, "invalid JSON " + token());
            connection.json.reject = true;
            IOException error = assertThrows(IOException.class, () -> {
                if (status == 200) {
                    client(connection).exchange(pending(), CODE);
                } else {
                    client(connection).createJob(session(), VIDEO_URL);
                }
            });
            assertEquals("Unable to complete the music server request", error.getMessage());
            assertNull(error.getCause());
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void rejectsExpiredSessionsInvalidTargetsAndStaleLoginsBeforeNetworking() throws Exception {
        MusicServerClient noNetwork = new MusicServerClient(uri -> {
            throw new AssertionError("Invalid input must not open a connection");
        }, new FakeJson());
        MusicServerProtocol.Session expired = new MusicServerProtocol.Session(ORIGIN, token(), 1);
        MusicServerClient.HttpFailure error = assertThrows(MusicServerClient.HttpFailure.class,
                () -> noNetwork.createJob(expired, VIDEO_URL));
        assertEquals(401, error.status);
        assertThrows(IOException.class, () -> noNetwork.createJob(null, VIDEO_URL));
        for (String url : new String[] {null, "", "https://other.example/watch?v=a1b2c3d4e5_",
                "http://www.youtube.com/watch?v=a1b2c3d4e5_"}) {
            assertThrows(IOException.class, () -> noNetwork.createJob(session(), url));
        }
        assertThrows(IOException.class, () -> noNetwork.exchange(null, CODE));
        assertThrows(IOException.class, () -> noNetwork.exchange(new MusicServerProtocol.Pending(
                ORIGIN, synthetic('A', 43), synthetic('B', 43), 0), CODE));
        assertThrows(IOException.class, () -> noNetwork.exchange(pending(), "\r\ninvalid"));
    }

    @Test
    public void refusesAmbientCookieHandlersWithoutChangingGlobalState() throws Exception {
        CookieHandler cookies = new CookieManager();
        CookieHandler.setDefault(cookies);
        MusicServerClient noNetwork = new MusicServerClient(uri -> {
            throw new AssertionError("Cookie-enabled connection must not be opened");
        }, new FakeJson());
        assertThrows(IOException.class, () -> noNetwork.exchange(pending(), CODE));
        assertThrows(IOException.class, () -> noNetwork.createJob(session(), VIDEO_URL));
        assertEquals(cookies, CookieHandler.getDefault());
    }

    @Test
    public void checksCookiesAgainAfterOpeningConnection() throws Exception {
        FakeConnection connection = new FakeConnection(200, sessionReply(future()));
        MusicServerClient client = new MusicServerClient(uri -> {
            CookieHandler.setDefault(new CookieManager());
            return connection;
        }, connection.json);
        assertThrows(IOException.class, () -> client.exchange(pending(), CODE));
        assertFalse(connection.wrote);
        assertTrue(connection.disconnected);
    }

    @Test
    public void sanitizesTransportErrorsIncludingTheirCauses() throws Exception {
        MusicServerClient failingOpen = new MusicServerClient(uri -> {
            throw new IOException("sensitive " + token(), new IOException(CODE));
        }, new FakeJson());
        IOException open = assertThrows(IOException.class,
                () -> failingOpen.exchange(pending(), CODE));
        assertEquals("Unable to complete the music server request", open.getMessage());
        assertNull(open.getCause());

        FakeConnection failingRead = new FakeConnection(200, sessionReply(future()));
        failingRead.failOnRead = true;
        IOException read = assertThrows(IOException.class,
                () -> client(failingRead).exchange(pending(), CODE));
        assertEquals("Unable to complete the music server request", read.getMessage());
        assertNull(read.getCause());
        assertTrue(failingRead.inputClosed);
        assertTrue(failingRead.disconnected);
    }

    @Test
    public void interruptionBeforeOpeningOrDuringReadCancelsWithoutLeakingDetails() throws Exception {
        Thread.currentThread().interrupt();
        MusicServerClient noNetwork = new MusicServerClient(uri -> {
            throw new AssertionError("Cancelled request must not open a connection");
        }, new FakeJson());
        assertThrows(InterruptedIOException.class, () -> noNetwork.exchange(pending(), CODE));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();

        FakeConnection connection = new FakeConnection(200, sessionReply(future()));
        connection.interruptOnRead = true;
        assertThrows(InterruptedIOException.class, () -> client(connection).exchange(pending(), CODE));
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(connection.inputClosed);
        assertTrue(connection.disconnected);
    }

    private static void assertTransportPolicy(FakeConnection connection) {
        assertEquals("POST", connection.getRequestMethod());
        assertFalse(connection.getInstanceFollowRedirects());
        assertFalse(connection.getUseCaches());
        assertTrue(connection.getDoOutput());
        assertEquals(MusicServerClient.TIMEOUT_MS, connection.getConnectTimeout());
        assertEquals(MusicServerClient.TIMEOUT_MS, connection.getReadTimeout());
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"));
        assertEquals("application/json", connection.getRequestProperty("Accept"));
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"));
        assertEquals("no-store", connection.getRequestProperty("Cache-Control"));
        assertNull(connection.getRequestProperty("Cookie"));
        assertNull(connection.getRequestProperty("Cookie2"));
        assertEquals(connection.request.size(), connection.fixedLength());
        assertTrue(connection.outputClosed);
        assertTrue(connection.inputClosed);
        assertTrue(connection.disconnected);
    }

    private static MusicServerClient client(FakeConnection connection) {
        return client(connection, new ArrayList<>());
    }

    private static MusicServerClient client(FakeConnection connection, List<URI> requests) {
        return new MusicServerClient(uri -> {
            assertTrue("Unexpected retry or redirect", requests.isEmpty());
            requests.add(uri);
            return connection;
        }, connection.json);
    }

    private static MusicServerProtocol.Pending pending() throws IOException {
        return new MusicServerProtocol.Pending(
                ORIGIN, synthetic('A', 43), synthetic('B', 43), System.currentTimeMillis());
    }

    private static MusicServerProtocol.Session session() throws IOException {
        return new MusicServerProtocol.Session(ORIGIN, token(), future());
    }

    private static long future() {
        return System.currentTimeMillis() + 60_000;
    }

    private static String token() {
        return synthetic('T', 43);
    }

    private static String sessionReply(long expiry) {
        return "{\"session\":{\"token\":\"" + token() + "\",\"tokenType\":\"Bearer\","
                + "\"expiresAt\":\"" + Instant.ofEpochMilli(expiry)
                + "\"},\"user\":{\"id\":\"fixture\"}}";
    }

    private static Map<String, Object> sessionResponse(long expiry) {
        Map<String, Object> response = new HashMap<>();
        response.put("session", sessionFields(expiry));
        return response;
    }

    private static Map<String, Object> sessionFields(long expiry) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("token", token());
        fields.put("tokenType", "Bearer");
        fields.put("expiresAt", Instant.ofEpochMilli(expiry).toString());
        return fields;
    }

    private static String synthetic(char value, int length) {
        char[] result = new char[length];
        Arrays.fill(result, value);
        return new String(result);
    }

    private static final class FakeJson implements MusicServerClient.JsonCodec {
        static final String ENCODED_BODY = "{\"fixture\":\"encoded request\"}";
        final Map<String, String> encodedValues = new HashMap<>();
        Map<String, Object> response = sessionResponse(future());
        String parsedBody;
        boolean reject;

        @Override
        public String encode(Map<String, String> body) {
            encodedValues.putAll(body);
            return ENCODED_BODY;
        }

        @Override
        public Map<?, ?> parse(String body) throws IOException {
            parsedBody = body;
            if (reject) {
                throw new IOException("decoder echoed " + token());
            }
            return response;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final int status;
        final byte[] body;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        final FakeJson json = new FakeJson();
        boolean wrote;
        boolean read;
        boolean errorRead;
        boolean disconnected;
        boolean outputClosed;
        boolean inputClosed;
        boolean failOnRead;
        boolean interruptOnRead;
        int bytesRead;

        FakeConnection(int status, String body) throws IOException {
            this(status, body.getBytes(StandardCharsets.UTF_8));
        }

        FakeConnection(int status, byte[] body) throws IOException {
            super(new URL(ORIGIN));
            this.status = status;
            this.body = body;
            headers.put("Content-Type", "application/json; charset=utf-8");
        }

        String requestBody() {
            return new String(request.toByteArray(), StandardCharsets.UTF_8);
        }

        int fixedLength() {
            return fixedContentLength;
        }

        @Override
        public OutputStream getOutputStream() {
            wrote = true;
            return new OutputStream() {
                @Override
                public void write(int value) {
                    request.write(value);
                }

                @Override
                public void write(byte[] value, int offset, int count) {
                    request.write(value, offset, count);
                }

                @Override
                public void close() {
                    outputClosed = true;
                }
            };
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public String getHeaderField(String name) {
            return headers.get(name);
        }

        @Override
        public InputStream getInputStream() {
            read = true;
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    byte[] value = new byte[1];
                    return read(value, 0, 1) < 0 ? -1 : value[0] & 0xff;
                }

                @Override
                public int read(byte[] value, int offset, int count) throws IOException {
                    if (failOnRead) {
                        throw new IOException("sensitive " + token());
                    }
                    if (interruptOnRead) {
                        Thread.currentThread().interrupt();
                    }
                    int result = input.read(value, offset, count);
                    bytesRead += Math.max(result, 0);
                    return result;
                }

                @Override
                public void close() throws IOException {
                    inputClosed = true;
                    input.close();
                }
            };
        }

        @Override
        public InputStream getErrorStream() {
            errorRead = true;
            return new ByteArrayInputStream(body);
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() { }
    }
}
