package com.skystream.ssyoutube;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Blocking, non-retrying music server requests. Call only from a worker thread. */
public final class MusicServerClient {
    static final int TIMEOUT_MS = 15_000;
    static final int MAX_REQUEST_BYTES = 32 * 1024;
    static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final long REQUEST_LIMIT_NANOS = 30_000_000_000L;
    private final ConnectionFactory connections;
    private final JsonCodec json;

    public interface ConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }

    interface JsonCodec {
        String encode(Map<String, String> body) throws IOException;
        Map<?, ?> parse(String body) throws IOException;
    }

    public MusicServerClient() {
        this(uri -> (HttpURLConnection) uri.toURL().openConnection());
    }

    public MusicServerClient(ConnectionFactory connections) {
        this(connections, new AndroidJson());
    }

    MusicServerClient(ConnectionFactory connections, JsonCodec json) {
        this.connections = connections;
        this.json = json;
    }

    public static final class HttpFailure extends IOException {
        public final int status;

        HttpFailure(int status) {
            super("Music server request failed (HTTP " + status + ")");
            this.status = status;
        }
    }

    public MusicServerProtocol.Session exchange(MusicServerProtocol.Pending pending, String code)
            throws IOException {
        MusicServerProtocol.requirePending(pending, System.currentTimeMillis());
        MusicServerProtocol.requireCode(code);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("codeVerifier", pending.verifier);
        body.put("redirectUri", MusicServerProtocol.REDIRECT_URI);
        Map<?, ?> response = post(pending.origin, "/api/auth/app/token", null, json.encode(body), 200);
        try {
            Object rawSession = response.get("session");
            if (!(rawSession instanceof Map)) {
                throw new IOException("Invalid music server session response");
            }
            Map<?, ?> session = (Map<?, ?>) rawSession;
            if (!"Bearer".equals(session.get("tokenType"))
                    || !(session.get("token") instanceof String)
                    || !(session.get("expiresAt") instanceof String)) {
                throw new IOException("Invalid music server session response");
            }
            long expiresAt = Instant.parse((String) session.get("expiresAt")).toEpochMilli();
            if (expiresAt <= System.currentTimeMillis()) {
                throw new IOException("Music server returned an expired session");
            }
            return new MusicServerProtocol.Session(
                    pending.origin, (String) session.get("token"), expiresAt);
        } catch (RuntimeException error) {
            throw new IOException("Invalid music server session response");
        }
    }

    public void createJob(MusicServerProtocol.Session session, String url) throws IOException {
        if (session == null || session.expiresAt <= System.currentTimeMillis()) {
            throw new HttpFailure(401);
        }
        String target = MusicServerProtocol.playlistUrl(url);
        if (target == null) {
            target = MusicServerProtocol.mediaUrl(url, null);
        }
        if (target == null) {
            throw new IOException("Choose a YouTube video or playlist");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("url", target);
        body.put("downloadType", "video");
        post(session.origin, "/api/jobs", session.token, json.encode(body), 202);
    }

    private Map<?, ?> post(String origin, String path, String token, String json, int expected)
            throws IOException {
        HttpURLConnection connection = null;
        long started = System.nanoTime();
        try {
            checkRequest(started);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_REQUEST_BYTES) {
                throw new IOException("Music server request is too large");
            }
            requireNoCookieHandler();
            URI uri = URI.create(MusicServerProtocol.normalizeOrigin(origin) + path);
            connection = connections.open(uri);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-store");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            requireNoCookieHandler();
            checkRequest(started);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            checkRequest(started);
            int status = connection.getResponseCode();
            checkRequest(started);
            if (status != expected) {
                throw new HttpFailure(status);
            }
            return parseResponse(readResponse(connection, started));
        } catch (HttpFailure error) {
            throw error;
        } catch (InterruptedIOException error) {
            throw new InterruptedIOException("Music server request timed out or was cancelled");
        } catch (IOException | RuntimeException error) {
            // Network and parser exception messages/causes can contain URLs or credentials.
            throw new IOException("Unable to complete the music server request");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void requireNoCookieHandler() throws IOException {
        // URLConnection has no per-request cookie opt-out. Do not mutate process-wide handlers.
        if (CookieHandler.getDefault() != null) {
            throw new IOException("Cookie-enabled networking is not supported");
        }
    }

    private static byte[] readResponse(HttpURLConnection connection, long started)
            throws IOException {
        String type = connection.getHeaderField("Content-Type");
        if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())) {
            throw new IOException("Invalid music server response type");
        }
        String encoding = connection.getHeaderField("Content-Encoding");
        if (encoding != null && !"identity".equalsIgnoreCase(encoding)) {
            throw new IOException("Unexpected music server response encoding");
        }
        long expected = -1;
        String length = connection.getHeaderField("Content-Length");
        if (length != null) {
            if (!length.matches("[0-9]{1,10}")) {
                throw new IOException("Invalid music server response length");
            }
            expected = Long.parseLong(length);
            if (expected == 0 || expected > MAX_RESPONSE_BYTES) {
                throw new IOException("Music server response is too large or empty");
            }
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            while (true) {
                checkRequest(started);
                int count = input.read(buffer, 0,
                        Math.min(buffer.length, MAX_RESPONSE_BYTES + 1 - result.size()));
                checkRequest(started);
                if (count == -1) {
                    break;
                }
                if (count > MAX_RESPONSE_BYTES - result.size()) {
                    throw new IOException("Music server response is too large");
                }
                result.write(buffer, 0, count);
            }
        }
        if (result.size() == 0 || (expected != -1 && result.size() != expected)) {
            throw new IOException("Incomplete music server response");
        }
        return result.toByteArray();
    }

    private Map<?, ?> parseResponse(byte[] body) throws IOException {
        String json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body)).toString();
        // Bound nesting before the recursive JSON parser sees untrusted input.
        int depth = 0;
        boolean string = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (string) {
                if (escape) {
                    escape = false;
                } else if (value == '\\') {
                    escape = true;
                } else if (value == '"') {
                    string = false;
                }
            } else if (value == '"') {
                string = true;
            } else if ((value == '{' || value == '[') && ++depth > 32) {
                throw new IOException("Music server response is too complex");
            } else if (value == '}' || value == ']') {
                depth--;
            }
        }
        return this.json.parse(json);
    }

    private static final class AndroidJson implements JsonCodec {
        @Override
        public String encode(Map<String, String> body) throws IOException {
            try {
                JSONObject object = new JSONObject();
                for (Map.Entry<String, String> field : body.entrySet()) {
                    object.put(field.getKey(), field.getValue());
                }
                String encoded = object.toString();
                if (encoded == null) {
                    throw new IOException("Unable to encode the music server request");
                }
                return encoded;
            } catch (JSONException | RuntimeException error) {
                throw new IOException("Unable to encode the music server request");
            }
        }

        @Override
        public Map<?, ?> parse(String body) throws IOException {
            try {
                JSONTokener tokener = new JSONTokener(body);
                Object parsed = tokener.nextValue();
                if (!(parsed instanceof JSONObject) || tokener.nextClean() != 0) {
                    throw new IOException("Invalid music server response");
                }
                Map<String, Object> result = new LinkedHashMap<>();
                Object session = ((JSONObject) parsed).opt("session");
                if (session instanceof JSONObject) {
                    JSONObject fields = (JSONObject) session;
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("token", fields.opt("token"));
                    values.put("tokenType", fields.opt("tokenType"));
                    values.put("expiresAt", fields.opt("expiresAt"));
                    result.put("session", values);
                }
                return result;
            } catch (JSONException | RuntimeException error) {
                throw new IOException("Invalid music server response");
            }
        }
    }

    private static void checkRequest(long started) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()
                || System.nanoTime() - started >= REQUEST_LIMIT_NANOS) {
            throw new InterruptedIOException("Music server request timed out or was cancelled");
        }
    }
}
