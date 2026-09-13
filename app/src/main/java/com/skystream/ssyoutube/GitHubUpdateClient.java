package com.skystream.ssyoutube;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Blocking, unauthenticated update operations. Callers must use a worker thread. */
public final class GitHubUpdateClient {
    private static final String LATEST_URL =
            "https://api.github.com/repos/skystream006/ssYoutube/releases/latest";
    private static final int TIMEOUT_MILLIS = 15_000;
    private static final int MAX_REDIRECTS = 5;
    private final ConnectionFactory connections;

    interface ConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }

    public GitHubUpdateClient() {
        this(uri -> (HttpURLConnection) uri.toURL().openConnection());
    }

    GitHubUpdateClient(ConnectionFactory connections) {
        this.connections = connections;
    }

    public static final class Release {
        public final String version;
        public final String tag;
        public final String assetName;
        public final String downloadUrl;
        public final long size;
        public final String sha256;

        private Release(String version, String tag, String assetName, String downloadUrl,
                        long size, String sha256) {
            this.version = version;
            this.tag = tag;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public static int compareVersions(String left, String right) {
        return UpdateChecks.compareVersions(left, right);
    }

    static Release release(String tag, String name, String state, String url,
                           long size, String digest) throws IOException {
        String version = UpdateChecks.releaseVersion(tag, false, false);
        String sha256 = UpdateChecks.validateAsset(tag, name, state, url, size, digest);
        return new Release(version, tag, name, url, size, sha256);
    }

    public Release fetchLatest() throws IOException {
        UpdateChecks.checkInterrupted();
        HttpURLConnection connection = open(URI.create(LATEST_URL));
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            UpdateChecks.checkInterrupted();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new IOException("No published GitHub release is available");
            }
            requireOk(status, "Checking for updates");
            long length = contentLength(connection);
            if (length == 0 || length > 1024 * 1024) {
                throw new IOException("Release metadata is empty or too large");
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                UpdateChecks.copy(input, body, 1024 * 1024, length, null);
            }
            return parseRelease(new String(body.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static Release parseRelease(String json) throws IOException {
        try {
            JSONObject object = new JSONObject(json);
            String tag = string(object, "tag_name");
            UpdateChecks.releaseVersion(tag, bool(object, "draft"), bool(object, "prerelease"));
            JSONArray assets = object.getJSONArray("assets");
            IOException invalidAsset = null;
            for (int i = 0; i < assets.length(); i++) {
                UpdateChecks.checkInterrupted();
                try {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = string(asset, "name");
                    String state = string(asset, "state");
                    String url = string(asset, "browser_download_url");
                    Object size = asset.get("size");
                    if (!(size instanceof Integer) && !(size instanceof Long)) {
                        throw new IOException("Release asset size is not an integer");
                    }
                    String digest = asset.isNull("digest") ? null : string(asset, "digest");
                    long bytes = ((Number) size).longValue();
                    return release(tag, name, state, url, bytes, digest);
                } catch (JSONException | IOException e) {
                    invalidAsset = new IOException("Invalid release asset: " + e.getMessage(), e);
                }
            }
            throw new IOException("The latest release has no valid uploaded APK", invalidAsset);
        } catch (JSONException e) {
            throw new IOException("GitHub returned malformed release metadata", e);
        }
    }

    public synchronized File download(Release release, File directory) throws IOException {
        UpdateChecks.checkInterrupted();
        if (release == null || directory == null) {
            throw new IOException("A release and update directory are required");
        }
        URI uri = UpdateChecks.assetUri(release.downloadUrl);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create the update directory");
        }
        File partial = File.createTempFile("update-", ".part", directory);
        File destination = new File(directory, "update.apk");
        boolean published = false;
        try {
            HttpURLConnection connection = openDownload(uri);
            try {
                long length = contentLength(connection);
                if (length != -1 && length != release.size) {
                    throw new IOException("APK response size does not match release metadata");
                }
                MessageDigest digest = sha256();
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    UpdateChecks.copy(input, output, UpdateChecks.MAX_APK_BYTES, release.size, digest);
                    UpdateChecks.verifyDigest(digest, release.sha256);
                    output.getFD().sync();
                }
                UpdateChecks.checkInterrupted();
                // Both files reside in the same directory: rename publishes only a complete APK.
                if (!partial.renameTo(destination)) {
                    throw new IOException("Cannot publish the downloaded APK");
                }
                published = true;
                return destination;
            } finally {
                connection.disconnect();
            }
        } finally {
            if (!published && partial.exists() && !partial.delete()) {
                throw new IOException("Cannot remove the partial update file");
            }
        }
    }

    private HttpURLConnection openDownload(URI uri) throws IOException {
        for (int redirects = 0; ; redirects++) {
            UpdateChecks.checkInterrupted();
            HttpURLConnection connection = open(uri);
            boolean keep = false;
            try {
                int status = connection.getResponseCode();
                UpdateChecks.checkInterrupted();
                if (status == HttpURLConnection.HTTP_OK) {
                    keep = true;
                    return connection;
                }
                if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                    requireOk(status, "Downloading the APK");
                }
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("Too many GitHub APK redirects");
                }
                String location = connection.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    throw new IOException("GitHub APK redirect is missing its destination");
                }
                try {
                    uri = UpdateChecks.redirectUri(uri.resolve(new URI(location)).toString());
                } catch (URISyntaxException | IllegalArgumentException e) {
                    throw new IOException("Invalid GitHub APK redirect", e);
                }
            } finally {
                if (!keep) {
                    connection.disconnect();
                }
            }
        }
    }

    private HttpURLConnection open(URI uri) throws IOException {
        UpdateChecks.checkInterrupted();
        HttpURLConnection connection = connections.open(uri);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "ssYoutube-UpdateClient");
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }

    private static long contentLength(HttpURLConnection connection) throws IOException {
        String header = connection.getHeaderField("Content-Length");
        if (header == null) {
            return -1;
        }
        try {
            long length = Long.parseLong(header);
            if (length < 0) {
                throw new NumberFormatException();
            }
            return length;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid update response Content-Length", e);
        }
    }

    private static void requireOk(int status, String operation) throws IOException {
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException(operation + " failed (GitHub HTTP " + status + ")");
        }
    }

    private static String string(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new JSONException("Expected text for " + key);
        }
        return (String) value;
    }

    private static boolean bool(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Boolean)) {
            throw new JSONException("Expected a boolean for " + key);
        }
        return (Boolean) value;
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 verification is unavailable", e);
        }
    }
}
