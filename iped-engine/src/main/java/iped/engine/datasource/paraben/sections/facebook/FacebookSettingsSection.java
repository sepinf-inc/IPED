/**
 * Paraben Data Parser Integration for IPED
 *
 * Developed by: Ariel E Aburo
 * Contact: infmpfch@gmail.com
 *
 * Description:
 * Implements parsing of data extracted via Paraben tools,
 * converting XML and associated database content into
 * IPED-compatible structures.
 *
 * This module supports multiple artifacts such as:
 * - Conversations
 * - Messages
 * - Attachments
 * - Device and user data and more.
 *
 * Notes:
 * - Uses XML as primary source
 * - Designed to be extensible for additional Paraben artifacts
 */
package iped.engine.datasource.paraben.sections.facebook;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.util.HtmlTemplate;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;

public class FacebookSettingsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<SettingRow> rows = extractSettings(xmlChain);

        boolean hasUsefulData = false;

        for (SettingRow r : rows) {
            if (r.key != null && !r.key.trim().isEmpty()
                    && r.value != null && !r.value.trim().isEmpty()) {
                hasUsefulData = true;
                break;
            }
        }

        if (!hasUsefulData)
            return;

        Map<String, String> htmlData = new LinkedHashMap<>();

        for (SettingRow r : rows) {
            if (r.key != null && !r.key.trim().isEmpty()
                    && r.value != null && !r.value.trim().isEmpty()) {

                htmlData.put(r.key, r.value);
            }
        }

        if (htmlData.isEmpty())
            return;

        Item item = new Item();

        item.setName("Facebook Settings");
        item.setPath(rootItem.getPath() + "/Applications/Facebook/Settings");
        item.setParent(rootItem);
        item.setHasChildren(false);

        item.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-settings"));

        item.setIdInDataSource("paraben-facebook-settings");

        String html = HtmlTemplate.buildTable("Facebook Settings", htmlData);
        byte[] bytes = html.getBytes("UTF-8");

        ExportFileTask.getLastInstance()
                .insertIntoStorage(item, bytes, bytes.length);

        item.setLength((long) bytes.length);

        item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
        item.getMetadata().set(BasicProps.CATEGORY, "Applications");

        for (SettingRow r : rows) {

            if (r.key == null || r.value == null)
                continue;

            String normKey = normalizeKey(r.key);

            item.getMetadata().add("facebook:setting:" + normKey, r.value);

            if (r.key.contains("logged_in_user_scoped")) {
                String userId = extractUserId(r.key);
                if (userId != null) {
                    item.getMetadata().add("facebook:user_id", userId);
                }
            }

            if (r.key.contains("impression-time-tracker")) {
                item.getMetadata().add("facebook:last_activity", r.value);
            }

            if (r.key.contains("__prefs_data_migrated__")) {
                item.getMetadata().add("facebook:prefs_migrated", r.value);
            }
        }

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(item);
    }

    private List<SettingRow> extractSettings(List<File> xmlChain) throws Exception {

        List<SettingRow> list = new ArrayList<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = getAttr(node, "xsi:type");
                String title = get(node, "Title");

                if (!"GridValue".equals(nodeType))
                    continue;

                if (!"Current Settings".equalsIgnoreCase(title))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    SettingRow data = new SettingRow();

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");

                        switch (column) {
                            case "Key":
                                data.key = value;
                                break;
                            case "Value":
                                data.value = value;
                                break;
                            case "Type":
                                data.type = value;
                                break;
                        }
                    }

                    if (data.key != null && !data.key.trim().isEmpty()
                            && data.value != null && !data.value.trim().isEmpty()) {

                        list.add(data);
                    }
                }
            }
        }

        return list;
    }

    private String extractUserId(String key) {
        try {
            String[] parts = key.split("/");
            for (String p : parts) {
                if (p.matches("\\d{5,}")) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeKey(String k) {
        return k.toLowerCase()
                .replace("/", "_")
                .replace("-", "_");
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private String getAttr(Element el, String attr) {
        return el.hasAttribute(attr) ? el.getAttribute(attr) : "";
    }

    private static class SettingRow {
        String key;
        String value;
        String type;
    }
}