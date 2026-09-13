package com.skystream.ssyoutube;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Locale;

final class UpdateChecks {
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    private static final String RELEASE_PATH = "/skystream006/ssYoutube/releases/download/";

    private UpdateChecks() {
    }

    static String normalizeVersion(String version) {
        if (version == null || !version.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Expected a numeric three-part release version");
        }
        return version.startsWith("v") ? version.substring(1) : version;
    }

    static int compareVersions(String left, String right) {
        String[] a = normalizeVersion(left).split("\\.");
        String[] b = normalizeVersion(right).split("\\.");
        for (int i = 0; i < a.length; i++) {
            String x = a[i].replaceFirst("^0+(?!$)", "");
            String y = b[i].replaceFirst("^0+(?!$)", "");
            int result = Integer.compare(x.length(), y.length());
            if (result == 0) {
                result = x.compareTo(y);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    static String releaseVersion(String tag, boolean draft, boolean prerelease) throws IOException {
        if (draft || prerelease) {
            throw new IOException("The latest release is not a published stable release");
        }
        try {
            return normalizeVersion(tag);
        } catch (IllegalArgumentException e) {
            throw new IOException("The latest release has an invalid version tag", e);
        }
    }

    static String validateAsset(String tag, String name, String state, String url,
                                long size, String digest) throws IOException {
        if (!"uploaded".equals(state) || name == null
                || !name.toLowerCase(Locale.ROOT).endsWith(".apk")
                || name.length() <= 4 || name.contains("/") || name.contains("\\")
                || hasControlCharacter(name)) {
            throw new IOException("Release asset is not an uploaded APK");
        }
        if (size <= 0 || size > MAX_APK_BYTES) {
            throw new IOException("APK size must be between 1 byte and 100 MiB");
        }
        URI uri = assetUri(url);
        String expected = RELEASE_PATH + tag + "/" + name;
        if (!expected.equals(uri.getPath())) {
            throw new IOException("APK download URL does not match its release tag and filename");
        }
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        if (!digest.matches("sha256:[0-9a-fA-F]{64}")) {
            throw new IOException("Release asset has an unsupported or malformed digest");
        }
        return digest.substring("sha256:".length()).toLowerCase(Locale.ROOT);
    }

    static URI assetUri(String url) throws IOException {
        URI uri = httpsUri(url);
        String rawPath = uri.getRawPath();
        if (!"github.com".equalsIgnoreCase(uri.getHost()) || uri.getRawQuery() != null
                || rawPath == null || !rawPath.startsWith(RELEASE_PATH)) {
            throw new IOException("APK download URL is not a repository release URL");
        }
        String[] parts = rawPath.substring(RELEASE_PATH.length()).split("/", -1);
        String lowerPath = rawPath.toLowerCase(Locale.ROOT);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()
                || !parts[1].toLowerCase(Locale.ROOT).endsWith(".apk")
                || lowerPath.contains("%2f") || lowerPath.contains("%5c")
                || lowerPath.contains("%2e") || lowerPath.contains("%00")
                || lowerPath.contains("%0a") || lowerPath.contains("%0d")
                || uri.getPath().contains("\\") || !uri.normalize().equals(uri)) {
            throw new IOException("APK download URL has an unexpected release path");
        }
        return uri;
    }

    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    static URI redirectUri(String url) throws IOException {
        URI uri = httpsUri(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("github.com".equals(host)) {
            return assetUri(url);
        }
        // Signed CDN URLs require query parameters; initial browser URLs must not have them.
        if (!"release-assets.githubusercontent.com".equals(host)
                && !"objects.githubusercontent.com".equals(host)
                && !"github-releases.githubusercontent.com".equals(host)) {
            throw new IOException("APK redirect points outside GitHub release hosts");
        }
        return uri;
    }

    private static URI httpsUri(String url) throws IOException {
        try {
            URI uri = new URI(url == null ? "" : url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("Update URLs must use HTTPS without credentials or fragments");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid update URL", e);
        }
    }

    static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Update operation cancelled");
        }
    }

    static long copy(InputStream input, OutputStream output, long limit,
                     long expectedSize, MessageDigest digest) throws IOException {
        if (limit <= 0 || expectedSize == 0 || expectedSize > limit || expectedSize < -1) {
            throw new IOException("Invalid update download size");
        }
        byte[] buffer = new byte[16 * 1024];
        long count = 0;
        while (true) {
            checkInterrupted();
            int read = input.read(buffer);
            checkInterrupted();
            if (read == -1) {
                break;
            }
            if (read > limit - count || (expectedSize >= 0 && read > expectedSize - count)) {
                throw new IOException("Update response exceeds the expected or allowed size");
            }
            output.write(buffer, 0, read);
            if (digest != null) {
                digest.update(buffer, 0, read);
            }
            count += read;
        }
        if (count == 0 || (expectedSize >= 0 && count != expectedSize)) {
            throw new IOException("Update response is empty or incomplete");
        }
        return count;
    }

    static void verifyDigest(MessageDigest digest, String expected) throws IOException {
        if (expected == null) {
            return;
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) {
            actual.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        if (!expected.equals(actual.toString())) {
            throw new IOException("Downloaded APK failed SHA-256 verification");
        }
    }
}
