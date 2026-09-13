package com.skystream.ssyoutube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PreferencesLayoutTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void appearanceAndSiteModeUseLabeledDropdowns() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        assertDropdown(layout, "theme_spinner", "@array/theme_options");
        assertDropdown(layout, "site_mode_spinner", "@array/site_mode_options");
        assertEquals(0, layout.getElementsByTagName("RadioGroup").getLength());
    }

    @Test
    public void dropdownOptionsKeepTheirSelectionOrder() throws Exception {
        Document strings = resource("values/strings.xml");
        assertOptions(strings, "theme_options",
                "@string/theme_system", "@string/theme_light", "@string/theme_dark");
        assertOptions(strings, "site_mode_options",
                "@string/site_mode_mobile", "@string/site_mode_desktop");
    }

    @Test
    public void advancedSettingsStartCollapsedAndContainOnlyAdvancedControls() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        Element advanced = byId(layout, "advanced_settings");
        assertEquals("gone", advanced.getAttributeNS(ANDROID, "visibility"));
        assertEquals("Button", byId(layout, "advanced_toggle").getTagName());
        assertEquals("@string/expand_advanced",
                byId(layout, "advanced_toggle").getAttributeNS(ANDROID, "contentDescription"));
        for (String id : new String[] {"supported_links_button", "logging_toggle",
                "view_logs_button", "share_log_button", "clear_log_button", "stats_for_nerds_toggle"}) {
            assertTrue(id + " must be inside Advanced", isInside(byId(layout, id), advanced));
        }
        for (String id : new String[] {"theme_spinner", "site_mode_spinner",
                "related_videos_toggle", "check_updates_button", "navigation_bar"}) {
            assertFalse(id + " must stay outside Advanced", isInside(byId(layout, id), advanced));
        }
    }

    private static boolean isInside(Node node, Element parent) {
        for (; node != null; node = node.getParentNode()) {
            if (node == parent) {
                return true;
            }
        }
        return false;
    }

    private static Document resource(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File("src/main/res", path));
    }

    private static Element byId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (("@+id/" + id).equals(element.getAttributeNS(ANDROID, "id"))
                    || ("@id/" + id).equals(element.getAttributeNS(ANDROID, "id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static void assertDropdown(Document layout, String id, String entries) {
        Element spinner = byId(layout, id);
        assertEquals("Spinner", spinner.getTagName());
        assertEquals("dropdown", spinner.getAttributeNS(ANDROID, "spinnerMode"));
        assertEquals(entries, spinner.getAttributeNS(ANDROID, "entries"));
        Element label = null;
        NodeList labels = layout.getElementsByTagName("TextView");
        for (int i = 0; i < labels.getLength(); i++) {
            Element candidate = (Element) labels.item(i);
            if (("@+id/" + id).equals(candidate.getAttributeNS(ANDROID, "labelFor"))) {
                label = candidate;
            }
        }
        assertNotNull("Missing dropdown label: " + id, label);
    }

    private static void assertOptions(Document strings, String name, String... expected) {
        NodeList arrays = strings.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (name.equals(array.getAttribute("name"))) {
                NodeList items = array.getElementsByTagName("item");
                assertEquals(expected.length, items.getLength());
                for (int j = 0; j < expected.length; j++) {
                    assertEquals(expected[j], items.item(j).getTextContent());
                }
                return;
            }
        }
        throw new AssertionError("Missing options: " + name);
    }
}
