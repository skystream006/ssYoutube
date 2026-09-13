package com.skystream.ssyoutube;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;

public class GitHubUpdateClientTest {
    private static final String URL =
            "https://github.com/skystream006/ssYoutube/releases/download/v0.10.04/app.apk";
    private File directory;

    @Before
    public void createDirectory() throws IOException {
        File build = new File("build");
        assertTrue(build.isDirectory() || build.mkdirs());
        directory = Files.createTempDirectory(build.toPath(), "update-client-test-").toFile();
    }

    @After
    public void removeDirectory() throws IOException {
        Thread.interrupted();
        for (File file : directory.listFiles()) {
            Files.delete(file.toPath());
        }
        Files.delete(directory.toPath());
    }

    @Test
    public void downloadsToFixedNameWithTimeoutsAndReplacesPreviousCompleteFile() throws Exception {
        Files.write(new File(directory, "update.apk").toPath(), new byte[] {9});
        FakeConnection connection = new FakeConnection(200, new byte[] {1, 2, 3});
        GitHubUpdateClient client = client(connection);
        GitHubUpdateClient.Release release = release(null);
        assertEquals("0.10.04", release.version);
        File result = client.download(release, directory);
        assertEquals(new File(directory, "update.apk"), result);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(result.toPath()));
        assertEquals(1, directory.list().length);
        assertTrue(connection.disconnected);
        assertFalse(connection.getInstanceFollowRedirects());
        assertTrue(connection.getConnectTimeout() > 0);
        assertTrue(connection.getReadTimeout() > 0);
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"));
        assertEquals(null, connection.getRequestProperty("Authorization"));
    }

    @Test
    public void followsAllowedSignedRedirectAndClosesBothConnections() throws Exception {
        FakeConnection redirect = new FakeConnection(302, new byte[0]);
        redirect.location = "https://release-assets.githubusercontent.com/asset?signature=test";
        FakeConnection data = new FakeConnection(200, new byte[] {1, 2, 3});
        client(redirect, data).download(release(null), directory);
        assertTrue(redirect.disconnected);
        assertTrue(data.disconnected);
    }

    @Test
    public void rejectsUnknownRedirectBeforeOpeningItAndCleansPartialFile() throws Exception {
        FakeConnection redirect = new FakeConnection(302, new byte[0]);
        redirect.location = "https://evil.test/asset.apk";
        assertThrows(IOException.class, () -> client(redirect).download(release(null), directory));
        assertTrue(redirect.disconnected);
        assertEquals(0, directory.list().length);
    }

    @Test
    public void boundsRedirectCount() throws Exception {
        FakeConnection[] redirects = new FakeConnection[6];
        for (int i = 0; i < redirects.length; i++) {
            redirects[i] = new FakeConnection(307, new byte[0]);
            redirects[i].location = URL;
        }
        assertThrows(IOException.class, () -> client(redirects).download(release(null), directory));
        for (FakeConnection redirect : redirects) {
            assertTrue(redirect.disconnected);
        }
        assertEquals(0, directory.list().length);
    }

    @Test
    public void removesFailedDownloadsAndKeepsPreviousVerifiedApk() throws Exception {
        File existing = new File(directory, "update.apk");
        Files.write(existing.toPath(), new byte[] {9});
        for (byte[] body : new byte[][] {new byte[0], {1, 2}, {1, 2, 3, 4}}) {
            FakeConnection data = new FakeConnection(200, body);
            assertThrows(IOException.class, () -> client(data).download(release(null), directory));
            assertTrue(data.disconnected);
            assertEquals(1, directory.list().length);
            assertArrayEquals(new byte[] {9}, Files.readAllBytes(existing.toPath()));
        }
    }

    @Test
    public void rejectsMismatchedOrInvalidContentLengthBeforeReading() throws Exception {
        for (String header : new String[] {"0", "4", "-1", "bogus", "99999999999999999999"}) {
            FakeConnection data = new FakeConnection(200, new byte[] {1, 2, 3});
            data.length = header;
            assertThrows(IOException.class, () -> client(data).download(release(null), directory));
            assertFalse(data.read);
            assertTrue(data.disconnected);
            assertEquals(0, directory.list().length);
        }
    }

    @Test
    public void verifiesDigestBeforePublishing() throws Exception {
        String abc = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        client(new FakeConnection(200, new byte[] {'a', 'b', 'c'})).download(release(abc), directory);
        Files.delete(new File(directory, "update.apk").toPath());
        FakeConnection data = new FakeConnection(200, new byte[] {'a', 'b', 'd'});
        assertThrows(IOException.class, () -> client(data).download(release(abc), directory));
        assertTrue(data.disconnected);
        assertEquals(0, directory.list().length);
    }

    @Test
    public void interruptionDuringDownloadCleansPartialAndDisconnects() throws Exception {
        FakeConnection data = new FakeConnection(200, new byte[] {1, 2, 3});
        data.interruptOnRead = true;
        assertThrows(InterruptedIOException.class,
                () -> client(data).download(release(null), directory));
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(data.disconnected);
        assertEquals(0, directory.list().length);
    }

    @Test
    public void reportsAbsentReleaseAndHttpErrorsWithoutParsingAndroidJson() throws Exception {
        for (int status : new int[] {404, 403, 429, 500, 302}) {
            FakeConnection connection = new FakeConnection(status, new byte[0]);
            IOException error = assertThrows(IOException.class, () -> client(connection).fetchLatest());
            assertTrue(status == 404 ? error.getMessage().contains("No published")
                    : error.getMessage().contains(Integer.toString(status)));
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void boundsMetadataBeforeParsingAndroidJson() throws Exception {
        for (String length : new String[] {"0", "1048577"}) {
            FakeConnection connection = new FakeConnection(200, new byte[0]);
            connection.length = length;
            assertThrows(IOException.class, () -> client(connection).fetchLatest());
            assertFalse(connection.read);
            assertTrue(connection.disconnected);
        }
        FakeConnection tooLarge = new FakeConnection(200, new byte[1048577]);
        assertThrows(IOException.class, () -> client(tooLarge).fetchLatest());
        assertTrue(tooLarge.disconnected);
    }

    private static GitHubUpdateClient.Release release(String digest) throws IOException {
        return GitHubUpdateClient.release("v0.10.04", "app.apk", "uploaded", URL, 3, digest);
    }

    private static GitHubUpdateClient client(FakeConnection... connections) {
        Queue<FakeConnection> queue = new ArrayDeque<>();
        java.util.Collections.addAll(queue, connections);
        return new GitHubUpdateClient(uri -> {
            assertFalse("Unexpected network request", queue.isEmpty());
            return queue.remove();
        });
    }

    private static final class FakeConnection extends HttpURLConnection {
        final int status;
        final byte[] body;
        String location;
        String length;
        boolean disconnected;
        boolean read;
        boolean interruptOnRead;

        FakeConnection(int status, byte[] body) throws IOException {
            super(new URL(URL));
            this.status = status;
            this.body = body;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public String getHeaderField(String name) {
            return "Location".equals(name) ? location : "Content-Length".equals(name) ? length : null;
        }

        @Override
        public InputStream getInputStream() {
            read = true;
            return new ByteArrayInputStream(body) {
                @Override
                public synchronized int read(byte[] buffer, int offset, int count) {
                    if (interruptOnRead) {
                        Thread.currentThread().interrupt();
                    }
                    return super.read(buffer, offset, count);
                }
            };
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
        public void connect() {
        }
    }
}
