package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreferencesTest {

    @Test
    public void mobileModeUsesMobileSite() {
        assertEquals("https://m.youtube.com/", Preferences.homeUrl(false));
        assertTrue(Preferences.userAgent(false).contains("Mobile"));
    }

    @Test
    public void desktopModeUsesDesktopSite() {
        assertEquals("https://www.youtube.com/", Preferences.homeUrl(true));
        assertFalse(Preferences.userAgent(true).contains("Mobile"));
    }

    @Test
    public void siteModeKeepsCurrentYouTubePage() {
        assertEquals("https://www.youtube.com/watch?v=abc&list=def",
                Preferences.siteModeUrl("https://m.youtube.com/watch?v=abc&list=def", true));
        assertEquals("https://www.youtube.com/watch?v=abc",
                Preferences.siteModeUrl("https://youtube.com/watch?v=abc", true));
        assertEquals("https://m.youtube.com/feed/subscriptions",
                Preferences.siteModeUrl("https://www.youtube.com/feed/subscriptions", false));
    }

    @Test
    public void siteModeFallsBackToHomeWithoutCurrentPage() {
        assertEquals("https://m.youtube.com/", Preferences.siteModeUrl(null, false));
        assertEquals("https://www.youtube.com/", Preferences.siteModeUrl("about:blank", true));
        assertEquals("https://m.youtube.com/", Preferences.siteModeUrl("https://", false));
    }

    @Test
    public void siteModeLeavesOtherHostsOnCurrentPage() {
        assertEquals("https://accounts.google.com/ServiceLogin",
                Preferences.siteModeUrl("https://accounts.google.com/ServiceLogin", true));
    }

    @Test
    public void homePageIsRecognised() {
        assertTrue(Preferences.isHomePage("https://m.youtube.com/"));
        assertTrue(Preferences.isHomePage("https://www.youtube.com"));
        assertTrue(Preferences.isHomePage("https://www.youtube.com/?gl=US"));
    }

    @Test
    public void otherPagesAreNotHome() {
        assertFalse(Preferences.isHomePage("https://m.youtube.com/watch?v=abc"));
        assertFalse(Preferences.isHomePage("https://m.youtube.com/feed/subscriptions"));
        assertFalse(Preferences.isHomePage("https://accounts.google.com/"));
        assertFalse(Preferences.isHomePage(null));
    }

    @Test
    public void watchPagesAreVideoPages() {
        assertTrue(Preferences.isVideoPage("https://www.youtube.com/watch?v=abc"));
        assertTrue(Preferences.isVideoPage("https://m.youtube.com/watch?v=abc&list=def"));
        assertTrue(Preferences.isVideoPage("https://WWW.YOUTUBE.COM/watch?v=abc#t=10"));
    }

    @Test
    public void otherPagesAreNotVideoPages() {
        assertFalse(Preferences.isVideoPage("https://www.youtube.com/"));
        assertFalse(Preferences.isVideoPage("https://www.youtube.com/feed/subscriptions"));
        assertFalse(Preferences.isVideoPage("https://example.com/watch?v=abc"));
        assertFalse(Preferences.isVideoPage(null));
    }
}
