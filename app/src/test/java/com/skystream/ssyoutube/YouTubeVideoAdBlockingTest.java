package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Test;

/** Executes the shipped script, not a Java reimplementation of its filtering behavior. */
public class YouTubeVideoAdBlockingTest {
    @Test
    public void videoAdScriptPreservesContentAndBrowserContracts() throws Exception {
        Path output = Files.createTempFile("ssyoutube-video-ad-tests", ".log");
        Process process = null;
        try {
            try {
                process = new ProcessBuilder("node", "--test",
                        "src/test/js/youtube_video_adblock.test.cjs")
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            } catch (IOException error) {
                Assume.assumeNoException("Node.js is needed to execute the bundled page script", error);
            }
            assertTrue("Video-ad script tests timed out", process.waitFor(30, TimeUnit.SECONDS));
            String report = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertEquals(report, 0, process.exitValue());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(output);
        }
    }
}
