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
    public void headerKeepsTitleLeftAndCompactUpdateControlsRight() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        Element title = byId(layout, "preferences_title");
        Element button = byId(layout, "check_updates_button");
        Element version = byId(layout, "app_version");
        assertHorizontalRow(title, button);
        assertHorizontalRow(button, version);
        Element header = (Element) title.getParentNode();
        assertEquals(layout.getDocumentElement(), header.getParentNode());
        assertEquals("match_parent", header.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("@string/preferences", title.getAttributeNS(ANDROID, "text"));
        assertEquals("start", title.getAttributeNS(ANDROID, "gravity"));
        assertEquals("0dp", title.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("1", title.getAttributeNS(ANDROID, "layout_weight"));
        assertEquals("wrap_content", button.getAttributeNS(ANDROID, "layout_width"));
        assertFalse(button.hasAttributeNS(ANDROID, "layout_weight"));
        assertEquals("0dp", button.getAttributeNS(ANDROID, "minWidth"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("false", button.getAttributeNS(ANDROID, "textAllCaps"));
        assertEquals("wrap_content", version.getAttributeNS(ANDROID, "layout_width"));
        assertFalse(version.hasAttributeNS(ANDROID, "layout_weight"));
    }

    @Test
    public void navigationKeepsButtonsWithoutHeading() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        NodeList labels = layout.getElementsByTagName("TextView");
        for (int i = 0; i < labels.getLength(); i++) {
            Element label = (Element) labels.item(i);
            assertFalse("@string/navigation".equals(label.getAttributeNS(ANDROID, "text")));
        }
        Element navigation = byId(layout, "navigation_bar");
        for (String id : new String[] {"back_button", "forward_button", "refresh_button",
                "home_button"}) {
            assertTrue(isInside(byId(layout, id), navigation));
        }
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
                "related_videos_toggle", "header_toggle", "check_updates_button", "navigation_bar"}) {
            assertFalse(id + " must stay outside Advanced", isInside(byId(layout, id), advanced));
        }
    }

    @Test
    public void hideRelatedVideosIsVisibleWithoutHeading() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        Element toggle = byId(layout, "related_videos_toggle");
        assertEquals("ToggleButton", toggle.getTagName());
        assertEquals("@string/hide_related", toggle.getAttributeNS(ANDROID, "textOn"));
        assertEquals("@string/hide_related", toggle.getAttributeNS(ANDROID, "textOff"));
        for (Node node = toggle; node instanceof Element; node = node.getParentNode()) {
            String visibility = ((Element) node).getAttributeNS(ANDROID, "visibility");
            assertTrue(visibility.isEmpty() || "visible".equals(visibility));
        }
        NodeList labels = layout.getElementsByTagName("TextView");
        for (int i = 0; i < labels.getLength(); i++) {
            Element label = (Element) labels.item(i);
            assertFalse("@string/related_videos".equals(label.getAttributeNS(ANDROID, "text")));
        }
    }

    @Test
    public void visibilityButtonsShareAnAlwaysVisibleRow() throws Exception {
        Document layout = resource("layout/dialog_preferences.xml");
        Element related = byId(layout, "related_videos_toggle");
        Element header = byId(layout, "header_toggle");
        assertHorizontalRow(related, header);
        assertEquals("ToggleButton", header.getTagName());
        assertEquals("@string/hide_header", header.getAttributeNS(ANDROID, "textOn"));
        assertEquals("@string/hide_header", header.getAttributeNS(ANDROID, "textOff"));
        for (Element button : new Element[] {related, header}) {
            assertEquals("0dp", button.getAttributeNS(ANDROID, "layout_width"));
            assertEquals("1", button.getAttributeNS(ANDROID, "layout_weight"));
            assertEquals("48dp", button.getAttributeNS(ANDROID, "minHeight"));
            assertEquals("false", button.getAttributeNS(ANDROID, "textAllCaps"));
            for (Node node = button; node instanceof Element; node = node.getParentNode()) {
                String visibility = ((Element) node).getAttributeNS(ANDROID, "visibility");
                assertTrue(visibility.isEmpty() || "visible".equals(visibility));
            }
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
        assertHorizontalRow(label, spinner);
        assertEquals("48dp", spinner.getAttributeNS(ANDROID, "minHeight"));
    }

    private static void assertHorizontalRow(Element left, Element right) {
        Element row = (Element) left.getParentNode();
        assertEquals(row, right.getParentNode());
        assertEquals("LinearLayout", row.getTagName());
        assertEquals("horizontal", row.getAttributeNS(ANDROID, "orientation"));
        assertEquals("center_vertical", row.getAttributeNS(ANDROID, "gravity"));
        assertTrue((left.compareDocumentPosition(right) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
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
