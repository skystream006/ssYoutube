package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class UpdateChecksTest {
    private static final String URL =
            "https://github.com/skystream006/ssYoutube/releases/download/v0.10.04/app.apk";

    @Test
    public void comparesNumericVersionsWithoutOverflowOrPaddingBias() {
        assertEquals("0.10.04", UpdateChecks.normalizeVersion("v0.10.04"));
        assertEquals(0, GitHubUpdateClient.compareVersions("v000.010.004", "0.10.4"));
        assertTrue(GitHubUpdateClient.compareVersions("0.10.04", "0.9.99") > 0);
        assertTrue(GitHubUpdateClient.compareVersions("0.10.10", "0.10.9") > 0);
        assertTrue(GitHubUpdateClient.compareVersions("1.0.0", "0.999.999") > 0);
        assertTrue(GitHubUpdateClient.compareVersions("0.0.999999999999999999999", "0.0.10") > 0);
        assertTrue(GitHubUpdateClient.compareVersions("0.10.04", "0.10.05") < 0);
        assertEquals(0, GitHubUpdateClient.compareVersions("0.0.0000", "000.000.0"));
    }

    @Test
    public void rejectsMalformedVersionsOnEitherSide() {
        for (String value : new String[] {null, "", "1", "1.2", "1.2.3.4", " 1.2.3",
                "1.2.3\n", "V1.2.3", "vv1.2.3", "-1.2.3", "1.+2.3", "1.2.3-beta",
                "1.2.3+4", "1.a.3", "١.2.3"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> GitHubUpdateClient.compareVersions(value, "1.2.3"));
            assertThrows(IllegalArgumentException.class,
                    () -> GitHubUpdateClient.compareVersions("1.2.3", value));
        }
    }

    @Test
    public void rejectsUnpublishedAndMalformedReleaseMetadata() {
        assertThrows(IOException.class, () -> UpdateChecks.releaseVersion("1.2.3", true, false));
        assertThrows(IOException.class, () -> UpdateChecks.releaseVersion("1.2.3", false, true));
        assertThrows(IOException.class, () -> UpdateChecks.releaseVersion(null, false, false));
        assertThrows(IOException.class, () -> UpdateChecks.releaseVersion("nightly", false, false));
    }

    @Test
    public void acceptsOnlyUploadedBoundedApkAssetsMatchingTheirRelease() throws Exception {
        assertNull(UpdateChecks.validateAsset("v0.10.04", "app.apk", "uploaded", URL, 1, null));
        assertNull(UpdateChecks.validateAsset("v0.10.04", "app.apk", "uploaded", URL,
                UpdateChecks.MAX_APK_BYTES, ""));
        for (long size : new long[] {-1, 0, UpdateChecks.MAX_APK_BYTES + 1, Long.MAX_VALUE}) {
            assertThrows(IOException.class,
                    () -> UpdateChecks.validateAsset("v0.10.04", "app.apk", "uploaded", URL, size, null));
        }
        assertThrows(IOException.class,
                () -> UpdateChecks.validateAsset("v0.10.04", "app.apk", "new", URL, 1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.validateAsset("v0.10.04", "app.zip", "uploaded", URL, 1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.validateAsset("v0.10.05", "app.apk", "uploaded", URL, 1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.validateAsset("v0.10.04", "other.apk", "uploaded", URL, 1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.validateAsset("v0.10.04", "../app.apk", "uploaded", URL, 1, null));
    }

    @Test
    public void rejectsUntrustedAssetUrls() {
        for (String url : new String[] {null, "", URL.replace("https:", "http:"),
                URL.replace("github.com", "github.com.evil.test"),
                URL.replace("github.com", "evil.test@github.com"),
                URL.replace("github.com", "github.com:8443"),
                URL.replace("skystream006", "other"), URL + "?token=value", URL + "#fragment",
                URL.replace("/download/", "/latest/"),
                URL.replace("/app.apk", "/../app.apk"),
                URL.replace("/app.apk", "/%2e%2e/app.apk"),
                URL.replace("/app.apk", "/app%2fother.apk"),
                URL.replace("/app.apk", "/app%5cother.apk"),
                URL.replace("/app.apk", "/app%0a.apk"),
                URL.replace("/app.apk", "/app.apk/extra"),
                URL.replace("app.apk", "app.zip")}) {
            assertThrows(url, IOException.class, () -> UpdateChecks.assetUri(url));
        }
    }

    @Test
    public void permitsSignedRedirectsOnlyToExactGithubReleaseHosts() throws Exception {
        for (String host : new String[] {"release-assets.githubusercontent.com",
                "objects.githubusercontent.com", "github-releases.githubusercontent.com"}) {
            assertEquals(host, UpdateChecks.redirectUri("https://" + host + "/asset?sig=test").getHost());
        }
        assertEquals("github.com", UpdateChecks.redirectUri(URL).getHost());
        for (String url : new String[] {"http://release-assets.githubusercontent.com/a",
                "https://release-assets.githubusercontent.com.evil.test/a",
                "https://evil.test/a", "https://raw.githubusercontent.com/a",
                "https://github.com/other/repo/releases/download/v1.0.0/a.apk",
                "https://user@objects.githubusercontent.com/a",
                "https://objects.githubusercontent.com:123/a",
                "https://objects.githubusercontent.com/a#fragment"}) {
            assertThrows(IOException.class, () -> UpdateChecks.redirectUri(url));
        }
    }

    @Test
    public void boundedCopyRejectsEmptyTruncatedAndOversizedResponses() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(3, UpdateChecks.copy(input("abc"), output, 3, 3, null));
        assertEquals("abc", output.toString("UTF-8"));
        assertEquals(3, UpdateChecks.copy(input("abc"), new ByteArrayOutputStream(), 3, -1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.copy(input(""), new ByteArrayOutputStream(), 3, -1, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.copy(input("ab"), new ByteArrayOutputStream(), 3, 3, null));
        ByteArrayOutputStream tooLong = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> UpdateChecks.copy(input("abcd"), tooLong, 3, -1, null));
        assertEquals(0, tooLong.size());
        assertThrows(IOException.class,
                () -> UpdateChecks.copy(input("abcd"), new ByteArrayOutputStream(), 100, 3, null));
        assertThrows(IOException.class,
                () -> UpdateChecks.copy(input("abc"), new ByteArrayOutputStream(), 3, 0, null));
    }

    @Test
    public void validatesOptionalDigestAndRejectsCorruption() throws Exception {
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertEquals(hash, UpdateChecks.validateAsset("v0.10.04", "app.apk", "uploaded",
                URL, 3, "sha256:" + hash.toUpperCase(java.util.Locale.ROOT)));
        for (String digest : new String[] {"sha256:", "md5:abcd", "sha256:not-a-hash"}) {
            assertThrows(IOException.class,
                    () -> UpdateChecks.validateAsset("v0.10.04", "app.apk", "uploaded", URL, 3, digest));
        }
        MessageDigest good = MessageDigest.getInstance("SHA-256");
        UpdateChecks.copy(input("abc"), new ByteArrayOutputStream(), 3, 3, good);
        UpdateChecks.verifyDigest(good, hash);
        MessageDigest bad = MessageDigest.getInstance("SHA-256");
        UpdateChecks.copy(input("abd"), new ByteArrayOutputStream(), 3, 3, bad);
        assertThrows(IOException.class, () -> UpdateChecks.verifyDigest(bad, hash));
    }

    @Test
    public void cancellationPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class,
                    () -> UpdateChecks.copy(input("abc"), new ByteArrayOutputStream(), 3, 3, null));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
