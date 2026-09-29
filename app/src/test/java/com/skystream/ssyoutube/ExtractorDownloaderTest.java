package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtractorDownloaderTest {
    @Test
    public void suppliedClientUserAgentReplacesDefault() throws Exception {
        HttpURLConnection connection = connection();
        ExtractorDownloader.applyRequestHeaders(connection,
                Collections.singletonMap("User-Agent", Collections.singletonList("Extractor iOS")));
        assertEquals(Collections.singletonList("Extractor iOS"),
                connection.getRequestProperties().get("User-Agent"));
    }

    @Test
    public void clientUserAgentNameIsCaseInsensitive() throws Exception {
        HttpURLConnection connection = connection();
        ExtractorDownloader.applyRequestHeaders(connection,
                Collections.singletonMap("user-agent", Collections.singletonList("Extractor Android")));
        assertEquals(Collections.singletonList("Extractor Android"),
                connection.getRequestProperties().get("user-agent"));
        assertNull(connection.getRequestProperties().get("User-Agent"));
    }

    @Test
    public void absentUserAgentGetsSingleDefault() throws Exception {
        HttpURLConnection connection = connection();
        ExtractorDownloader.applyRequestHeaders(connection, Collections.emptyMap());
        assertEquals(Collections.singletonList(NativeNetworkPolicy.USER_AGENT),
                connection.getRequestProperties().get("User-Agent"));
    }

    @Test
    public void preservesMultipleValuesButExcludesCredentialHeaders() throws Exception {
        HttpURLConnection connection = connection();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Arrays.asList("text/html", "application/json"));
        headers.put("Cookie", Collections.singletonList("CONSENT=PENDING+123"));
        headers.put("Authorization", Collections.singletonList("not-an-account-credential"));
        ExtractorDownloader.applyRequestHeaders(connection, headers);
        List<String> accept = connection.getRequestProperties().get("Accept");
        assertEquals(2, accept.size());
        assertTrue(accept.containsAll(headers.get("Accept")));
        assertNull(connection.getRequestProperty("Cookie"));
        assertNull(connection.getRequestProperty("Authorization"));
    }

    private static HttpURLConnection connection() throws Exception {
        return new HttpURLConnection(new URL("https://www.youtube.com/")) {
            @Override
            public void disconnect() { }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() { }
        };
    }
}
