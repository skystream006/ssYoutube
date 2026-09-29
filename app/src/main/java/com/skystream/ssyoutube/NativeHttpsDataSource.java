package com.skystream.ssyoutube;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Streaming transport that validates CDN hosts before each request, not after redirects. */
@UnstableApi
final class NativeHttpsDataSource extends BaseDataSource {
    private static final long MAX_MEDIA_BYTES = 16L * 1024 * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 4L * 1024 * 1024;
    private HttpURLConnection connection;
    private InputStream input;
    private Uri openedUri;
    private long remaining;
    private long transferred;
    private long responseLimit;
    private boolean opened;

    NativeHttpsDataSource() {
        super(true);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        transferInitializing(dataSpec);
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null) {
            throw new IOException("Unsupported native media request");
        }
        URI uri = NativeNetworkPolicy.requireHttps(dataSpec.uri.toString(), true);
        try {
            for (int redirects = 0; redirects <= NativeNetworkPolicy.MAX_REDIRECTS; redirects++) {
                checkInterrupted();
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(NativeNetworkPolicy.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(NativeNetworkPolicy.READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", NativeNetworkPolicy.USER_AGENT);
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Cookie", "");
                connection.setRequestProperty("Authorization", "");
                if (dataSpec.position != 0 || dataSpec.length != C.LENGTH_UNSET) {
                    String range = "bytes=" + dataSpec.position + "-";
                    if (dataSpec.length != C.LENGTH_UNSET) {
                        if (dataSpec.length > Long.MAX_VALUE - dataSpec.position) {
                            throw new IOException("Invalid native media range");
                        }
                        range += dataSpec.position + dataSpec.length - 1;
                    }
                    connection.setRequestProperty("Range", range);
                }
                int code = connection.getResponseCode();
                checkInterrupted();
                if (NativeNetworkPolicy.isRedirect(code)) {
                    uri = NativeNetworkPolicy.redirect(uri,
                            connection.getHeaderField("Location"), true);
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (code < 200 || code > 299) {
                    throw new IOException("Native media request failed");
                }
                long contentLength = contentLength(connection);
                long skip = code == HttpURLConnection.HTTP_OK ? dataSpec.position : 0;
                if (contentLength != C.LENGTH_UNSET && contentLength < skip) {
                    throw new EOFException("Invalid native media range");
                }
                remaining = dataSpec.length != C.LENGTH_UNSET ? dataSpec.length
                        : contentLength == C.LENGTH_UNSET ? C.LENGTH_UNSET : contentLength - skip;
                if (remaining < 0 && remaining != C.LENGTH_UNSET) {
                    throw new EOFException("Invalid native media range");
                }
                String contentType = connection.getContentType();
                String type = contentType == null ? "" : contentType.toLowerCase(Locale.US);
                responseLimit = type.contains("mpegurl") || type.contains("dash+xml")
                        || uri.getPath().contains("/manifest/")
                        ? MAX_MANIFEST_BYTES : MAX_MEDIA_BYTES;
                if (remaining > responseLimit || skip > responseLimit) {
                    throw new IOException("Native media response too large");
                }
                input = connection.getInputStream();
                openedUri = Uri.parse(uri.toString());
                transferred = 0;
                opened = true;
                transferStarted(dataSpec);
                skipFully(skip);
                return remaining;
            }
            throw new IOException("Too many native media redirects");
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        checkInterrupted();
        if (length == 0) {
            return 0;
        }
        if (remaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        if (input == null) {
            throw new IOException("Native media source is closed");
        }
        int requested = remaining == C.LENGTH_UNSET
                ? length : (int) Math.min(remaining, length);
        int count = input.read(buffer, offset, requested);
        checkInterrupted();
        if (count == -1) {
            if (remaining != C.LENGTH_UNSET && remaining > 0) {
                throw new EOFException("Incomplete native media response");
            }
            return C.RESULT_END_OF_INPUT;
        }
        transferred += count;
        if (transferred > responseLimit) {
            throw new IOException("Native media response too large");
        }
        if (remaining != C.LENGTH_UNSET) {
            remaining -= count;
        }
        bytesTransferred(count);
        return count;
    }

    @Override
    public Uri getUri() {
        return openedUri;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return connection == null ? Collections.emptyMap() : connection.getHeaderFields();
    }

    @Override
    public void close() throws IOException {
        try {
            if (input != null) {
                input.close();
            }
        } finally {
            input = null;
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
            openedUri = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    private void skipFully(long count) throws IOException {
        byte[] buffer = new byte[8192];
        while (count > 0) {
            checkInterrupted();
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, count));
            if (read == -1) {
                throw new EOFException("Incomplete native media range");
            }
            count -= read;
            transferred += read;
            bytesTransferred(read);
        }
    }

    private static long contentLength(HttpURLConnection connection) {
        try {
            String header = connection.getHeaderField("Content-Length");
            long value = header == null ? C.LENGTH_UNSET : Long.parseLong(header);
            return value >= 0 ? value : C.LENGTH_UNSET;
        } catch (NumberFormatException error) {
            return C.LENGTH_UNSET;
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Native media request cancelled");
        }
    }
}
