package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class BackgroundAudioIntegrationTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void backgroundAudioDefaultsOnAndUsesSeparatePersistedControls() throws Exception {
        assertTrue(Preferences.DEFAULT_BACKGROUND_AUDIO);
        assertEquals("(function(enabled){})(true);",
                BackgroundAudioScript.create("function(enabled){}", true));
        assertEquals("(function(enabled){})(false);",
                BackgroundAudioScript.create("function(enabled){}", false));
        assertTrue(BackgroundAudioScript.foreground(false, true).contains("setForeground(false,true)"));
        assertTrue(BackgroundAudioScript.STATE.contains(".state():null"));
        String activity = source("MainActivity.java");
        assertTrue(activity.contains("prefs.getBoolean(KEY_BACKGROUND_AUDIO_ENABLED,"));
        assertTrue(activity.contains("prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO_ENABLED, checked)"));
        assertTrue(activity.contains("backgroundAudioToggle.setChecked(backgroundAudioEnabled)"));
        assertTrue(activity.contains("isTrustedPlaybackMessage(mainFrame,"));
        assertTrue(activity.contains("nativePlayer.onPause(keepPlaying)"));
        assertTrue(activity.contains("nativePlayer.onPause(canKeepBackgroundPlayback())"));
        assertFalse(activity.contains("nativePlayer.onPause();"));
        assertTrue(activity.contains("backgroundHandler.removeCallbacksAndMessages(null)"));
        assertTrue(activity.contains("backgroundPlaybackState.needsProtection("));
    }

    @Test
    public void foregroundServiceIsPrivateMediaPlaybackAndStopsWithTheTask() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document manifest = factory.newDocumentBuilder().parse(new File("src/main/AndroidManifest.xml"));
        NodeList services = manifest.getElementsByTagName("service");
        assertEquals(1, services.getLength());
        Element service = (Element) services.item(0);
        assertEquals(".BackgroundPlaybackService", service.getAttributeNS(ANDROID, "name"));
        assertEquals("false", service.getAttributeNS(ANDROID, "exported"));
        assertEquals("mediaPlayback", service.getAttributeNS(ANDROID, "foregroundServiceType"));
        assertEquals("true", service.getAttributeNS(ANDROID, "stopWithTask"));
        for (String permission : new String[] {"FOREGROUND_SERVICE",
                "FOREGROUND_SERVICE_MEDIA_PLAYBACK", "WAKE_LOCK"}) {
            boolean found = false;
            NodeList permissions = manifest.getElementsByTagName("uses-permission");
            for (int i = 0; i < permissions.getLength(); i++) {
                found |= ("android.permission." + permission).equals(
                        ((Element) permissions.item(i)).getAttributeNS(ANDROID, "name"));
            }
            assertTrue(permission, found);
        }
    }

    @Test
    public void serviceHasMediaControlsAndReleasesItsResourcesWithoutRetainingTheActivity() throws Exception {
        String service = source("BackgroundPlaybackService.java");
        assertTrue(service.contains("Notification.MediaStyle()"));
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK"));
        assertTrue(service.contains("wakeLock.release()"));
        assertTrue(service.contains("session.release()"));
        assertTrue(service.contains("START_NOT_STICKY"));
        assertTrue(service.contains("setPackage(getPackageName())"));
        assertTrue(service.contains("token == intent.getLongExtra(TOKEN, 0)"));
        assertTrue(service.contains("UPDATE.equals(intent.getAction())"));
        assertTrue(source("MainActivity.java").contains(".setAction(BackgroundPlaybackService.STOP)"));
        assertFalse(service.contains("static MainActivity"));
        assertFalse(service.contains("WebView"));
        assertFalse(service.contains("videoId"));
        assertFalse(service.contains("getUrl("));
        assertTrue(source("MainActivity.java").contains(
                "if (BackgroundPlaybackService.OPEN_PLAYER.equals(intent.getAction()))"));
    }

    @Test
    public void holdGestureConsumesTouchButPreservesAccessibleClickAndCleansUpTimers() throws Exception {
        String activity = source("MainActivity.java");
        assertTrue(activity.contains("settingsButton.performClick()"));
        assertTrue(activity.contains("case MotionEvent.ACTION_POINTER_DOWN:"));
        assertTrue(activity.contains("case MotionEvent.ACTION_CANCEL:"));
        assertTrue(activity.contains("settingsButton.setVisibility(View.INVISIBLE)"));
        assertTrue(activity.contains("settingsButton.setVisibility(View.VISIBLE)"));
        assertTrue(activity.contains("settingsHoldHandler.removeCallbacksAndMessages(null)"));
        assertTrue(activity.contains("settingsHold.tick(SystemClock.elapsedRealtime())"));
        String player = source("NativePlayerView.java");
        assertTrue(player.contains("setKeepScreenOn(isPlaying && foreground && !released)"));
        assertTrue(player.contains("state.setLifecycle(false, backgroundAudioAllowed)"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssyoutube", name)), StandardCharsets.UTF_8);
    }
}
