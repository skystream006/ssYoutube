package com.skystream.ssyoutube;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Anonymous, bounded transport used exclusively by the maintained extractor. */
final class ExtractorDownloader extends Downloader {
    private static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private final ThreadLocal<Cancellation> current = new ThreadLocal<>();

    static final class Cancellation {
        private boolean cancelled;
        private HttpURLConnection connection;

        synchronized void cancel() {
            cancelled = true;
            if (connection != null) {
                connection.disconnect();
            }
        }

        synchronized void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Native extraction cancelled");
            }
        }

        synchronized void attach(HttpURLConnection value) throws InterruptedIOException {
            check();
            connection = value;
        }

        synchronized void detach(HttpURLConnection value) {
            if (connection == value) {
                connection = null;
            }
        }
    }

    void begin(Cancellation cancellation) throws IOException {
        cancellation.check();
        current.set(cancellation);
    }

    void end() {
        current.remove();
    }

    @Override
    public Response execute(Request request) throws IOException {
        Cancellation cancellation = current.get();
        if (cancellation == null) {
            throw new IOException("No active native extraction");
        }
        URI uri = NativeNetworkPolicy.requireHttps(request.url(), false);
        URI initialUri = uri;
        String method = request.httpMethod();
        if (!"GET".equals(method) && !"POST".equals(method) && !"HEAD".equals(method)) {
            throw new IOException("Unsupported native request method");
        }
        byte[] body = request.dataToSend();
        if (body != null && body.length > MAX_REQUEST_BYTES) {
            throw new IOException("Native request too large");
        }
        for (int redirects = 0; redirects <= NativeNetworkPolicy.MAX_REDIRECTS; redirects++) {
            cancellation.check();
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                cancellation.attach(connection);
                connection.setConnectTimeout(NativeNetworkPolicy.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(NativeNetworkPolicy.READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod(method);
                applyRequestHeaders(connection, request.headers());
                // Keep only extractor-generated consent, never browser/account cookies or a jar.
                connection.setRequestProperty("Cookie",
                        NativeNetworkPolicy.anonymousConsentCookies(
                                request.headers(), initialUri, uri));
                connection.setRequestProperty("Authorization", "");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                if ("POST".equals(method) && body != null) {
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(body.length);
                    try (java.io.OutputStream output = connection.getOutputStream()) {
                        cancellation.check();
                        output.write(body);
                    }
                }
                int code = connection.getResponseCode();
                cancellation.check();
                if (NativeNetworkPolicy.isRedirect(code)) {
                    uri = NativeNetworkPolicy.redirect(uri,
                            connection.getHeaderField("Location"), false);
                    if (code == 303 || ((code == 301 || code == 302) && "POST".equals(method))) {
                        method = "GET";
                        body = null;
                    }
                    continue;
                }
                if (connection.getContentLength() > MAX_RESPONSE_BYTES) {
                    throw new IOException("Native response too large");
                }
                InputStream input = code >= 400
                        ? connection.getErrorStream() : connection.getInputStream();
                String text = "";
                if (input != null) {
                    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                        input = new GZIPInputStream(input);
                    }
                    try (InputStream response = input;
                         ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = response.read(buffer)) != -1) {
                            cancellation.check();
                            if (bytes.size() > MAX_RESPONSE_BYTES - count) {
                                throw new IOException("Native response too large");
                            }
                            bytes.write(buffer, 0, count);
                        }
                        text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
                return new Response(code, connection.getResponseMessage(),
                        connection.getHeaderFields(), text, uri.toString());
            } finally {
                cancellation.detach(connection);
                connection.disconnect();
            }
        }
        throw new IOException("Too many native redirects");
    }

    static void applyRequestHeaders(HttpURLConnection connection,
                                    Map<String, List<String>> headers) {
        boolean hasUserAgent = false;
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            String name = header.getKey();
            if (name == null || header.getValue() == null
                    || NativeNetworkPolicy.isCredentialHeader(name)
                    || "Host".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                continue;
            }
            boolean first = true;
            for (String value : header.getValue()) {
                if (value == null) {
                    continue;
                }
                if (first) {
                    connection.setRequestProperty(name, value);
                    first = false;
                } else {
                    connection.addRequestProperty(name, value);
                }
                hasUserAgent |= "User-Agent".equalsIgnoreCase(name);
            }
        }
        if (!hasUserAgent) {
            connection.setRequestProperty("User-Agent", NativeNetworkPolicy.USER_AGENT);
        }
    }
}
