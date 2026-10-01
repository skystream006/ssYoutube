package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MusicServerManifestTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void browserCallbackHasADedicatedBrowsableActivityWithoutEnablingCleartext() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document manifest = factory.newDocumentBuilder()
                .parse(new File("src/main/AndroidManifest.xml"));
        Element app = (Element) manifest.getElementsByTagName("application").item(0);
        assertEquals("false", app.getAttributeNS(ANDROID, "usesCleartextTraffic"));
        NodeList activities = app.getElementsByTagName("activity");
        Element callback = null;
        for (int i = 0; i < activities.getLength(); i++) {
            Element candidate = (Element) activities.item(i);
            if (".MusicServerCallbackActivity".equals(candidate.getAttributeNS(ANDROID, "name"))) {
                callback = candidate;
            }
        }
        assertNotNull(callback);
        assertEquals("true", callback.getAttributeNS(ANDROID, "exported"));
        assertEquals("true", callback.getAttributeNS(ANDROID, "noHistory"));
        NodeList data = callback.getElementsByTagName("data");
        assertEquals(1, data.getLength());
        Element uri = (Element) data.item(0);
        assertEquals("com.ssytdlp.app", uri.getAttributeNS(ANDROID, "scheme"));
        assertEquals("", uri.getAttributeNS(ANDROID, "host"));
        assertEquals("android.intent.action.VIEW", ((Element) callback
                .getElementsByTagName("action").item(0)).getAttributeNS(ANDROID, "name"));
        NodeList categories = callback.getElementsByTagName("category");
        boolean browsable = false;
        for (int i = 0; i < categories.getLength(); i++) {
            browsable |= "android.intent.category.BROWSABLE".equals(
                    ((Element) categories.item(i)).getAttributeNS(ANDROID, "name"));
        }
        assertTrue(browsable);
    }
}
