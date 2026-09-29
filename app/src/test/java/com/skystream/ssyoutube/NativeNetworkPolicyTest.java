package com.skystream.ssyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class NativeNetworkPolicyTest {
    @Test
    public void permitsOnlyAnonymousHttpsMediaHosts() {
        assertTrue(NativeNetworkPolicy.isMediaUrl("https://rr1.googlevideo.com/videoplayback?sig=x"));
        assertTrue(NativeNetworkPolicy.isMediaUrl("https://manifest.googlevideo.com/api/manifest"));
        assertTrue(NativeNetworkPolicy.isMediaUrl("https://www.youtube.com:443/api/manifest"));
        for (String unsafe : new String[]{
                "http://rr1.googlevideo.com/video", "file:///video", "https://user@youtube.com/v",
                "https://viewer@rr1.googlevideo.com/v", "https://youtube.com:444/v",
                "https://googlevideo.com.attacker.example/v", "https://notgooglevideo.com/v",
                "https://127.0.0.1/v", "https://youtube.com./v", "https://youtube.com/v#secret",
                "https://youtube.com\\@attacker.example/v", "https://youtube.com:/v", null
        }) {
            assertFalse(unsafe, NativeNetworkPolicy.isMediaUrl(unsafe));
        }
    }

    @Test
    public void checksEveryRedirectIncludingRelativeLocations() throws Exception {
        URI initial = NativeNetworkPolicy.requireHttps("https://rr1.googlevideo.com/one", true);
        assertTrue(NativeNetworkPolicy.isMediaUrl(
                NativeNetworkPolicy.redirect(initial, "/two?sig=x", true).toString()));
        for (String unsafe : new String[]{
                "http://rr1.googlevideo.com/two", "//attacker.example/two",
                "https://token@rr2.googlevideo.com/two", null
        }) {
            try {
                NativeNetworkPolicy.redirect(initial, unsafe, true);
                fail("Unsafe redirect accepted");
            } catch (IOException expected) {
                // Expected.
            }
        }
    }

    @Test
    public void stripsAllCredentialHeadersCaseInsensitively() {
        assertTrue(NativeNetworkPolicy.isCredentialHeader("cOoKiE"));
        assertTrue(NativeNetworkPolicy.isCredentialHeader("Authorization"));
        assertTrue(NativeNetworkPolicy.isCredentialHeader("Proxy-Authorization"));
        assertFalse(NativeNetworkPolicy.isCredentialHeader("User-Agent"));
    }

    @Test
    public void preservesOnlyAnonymousConsentOnOriginalOrigin() throws Exception {
        URI youtube = NativeNetworkPolicy.requireHttps("https://www.youtube.com/watch", false);
        URI cdn = NativeNetworkPolicy.requireHttps("https://rr1.googlevideo.com/video", false);
        Map<String, List<String>> headers = Collections.singletonMap("cOoKiE", Arrays.asList(
                "CONSENT=PENDING+123; SID=not-an-account-token; SOCS=CAA",
                "CONSENT=PENDING+456; OTHER=discard"));
        assertEquals("CONSENT=PENDING+123; SOCS=CAA",
                NativeNetworkPolicy.anonymousConsentCookies(headers, youtube, youtube));
        assertEquals("", NativeNetworkPolicy.anonymousConsentCookies(headers, youtube, cdn));
        assertEquals("", NativeNetworkPolicy.anonymousConsentCookies(
                Collections.singletonMap("Cookie",
                        Collections.singletonList("CONSENT=bad\r\nX-Header: injected")),
                youtube, youtube));
    }
}
