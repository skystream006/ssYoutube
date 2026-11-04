package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;

public class PlaybackEngineTest {
    @Test
    public void legacyChoicesAndDefaultArePreserved() {
        assertEquals(PlaybackEngine.MPV, PlaybackEngine.fromPreference(null, true));
        assertEquals(PlaybackEngine.WEB, PlaybackEngine.fromPreference(null, false));
        assertEquals(PlaybackEngine.MPV, PlaybackEngine.fromPreference("invalid", true));
    }

    @Test
    public void savedEngineOverridesLegacyFlagAndBothNativePlayersDisableWebAds() {
        for (PlaybackEngine engine : PlaybackEngine.values()) {
            for (boolean oldMpv : new boolean[]{true, false}) {
                assertEquals(engine, PlaybackEngine.fromPreference(engine.preference, oldMpv));
            }
            assertEquals(engine == PlaybackEngine.WEB,
                    Preferences.isAdBlockingEnabled(engine.isNative(), true));
            assertEquals(engine == PlaybackEngine.WEB,
                    Preferences.isPatchedAdBlockingEnabled(engine.isNative(), true));
            assertEquals(engine.isNative(), !NativePlaybackScript.forPreference(
                    engine.isNative()).isEmpty());
        }
    }

    @Test
    public void spinnerOrderMatchesStablePreferenceValues() throws Exception {
        NodeList arrays = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/values/strings.xml"))
                .getElementsByTagName("string-array");
        Element choices = null;
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if ("playback_engine_options".equals(array.getAttribute("name"))) {
                choices = array;
            }
        }
        assertNotNull(choices);
        NodeList items = choices.getElementsByTagName("item");
        assertEquals(PlaybackEngine.values().length, items.getLength());
        for (PlaybackEngine engine : PlaybackEngine.values()) {
            assertEquals("@string/playback_" + engine.preference,
                    items.item(engine.ordinal()).getTextContent());
        }
    }

    @Test
    public void switchingStopsOldPlaybackAndReloadsSuppressionHooks() throws Exception {
        String activity = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssyoutube/MainActivity.java")), StandardCharsets.UTF_8);
        int start = activity.indexOf("private void setPlaybackEngine(");
        String setter = activity.substring(start, activity.indexOf("\n    }", start));
        assertTrue(setter.contains("closeNativePlayback(false)"));
        assertTrue(setter.contains("putString(KEY_PLAYBACK_ENGINE, engine.preference)"));
        assertTrue(setter.contains("nativePlayer.setPlaybackEngine(engine)"));
        assertTrue(setter.contains("updateNativePlaybackScript(webView)"));
        assertTrue(setter.contains("updateAdBlocking(webView)"));
        assertTrue(setter.contains("webView.reload()"));
        assertFalse(setter.contains("putBoolean(KEY_ADBLOCKING_ENABLED"));
        assertFalse(setter.contains("putBoolean(KEY_PATCHED_ADBLOCKING_ENABLED"));
    }
}
