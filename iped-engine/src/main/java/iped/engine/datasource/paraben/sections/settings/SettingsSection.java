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
package iped.engine.datasource.paraben.sections.settings;

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

public class SettingsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        Map<String, String> settings = extractSettings(xmlChain);

        if (settings.isEmpty())
            return;

        Item item = new Item();

        item.setName("System Settings");
        item.setPath(rootItem.getPath() + "/Settings/System Settings");
        item.setParent(rootItem);
        item.setHasChildren(false);

        item.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-settings"));

        item.setIdInDataSource("paraben-settings");

        // HTML
        String html = HtmlTemplate.buildTable("System Settings", settings);
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ExportFileTask.getLastInstance()
                .insertIntoStorage(item, bytes, bytes.length);

        item.setLength((long) bytes.length);

        // =====================================================
        // 🔥 METADATA
        // =====================================================

        item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
        item.getMetadata().set(BasicProps.CATEGORY, "Settings");

        for (Map.Entry<String, String> e : settings.entrySet()) {

            String key = normalizeKey(e.getKey());

            item.getMetadata().add("paraben:setting:" + key, e.getValue());
        }

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(item);
    }

    // =====================================================
    // 🔥 PARSEO
    // =====================================================

    private Map<String, String> extractSettings(List<File> xmlChain) throws Exception {

        Map<String, String> map = new LinkedHashMap<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = get(node, "NodeType");
                String title = get(node, "Title");

                if (!"DSCase.Grid".equals(nodeType))
                    continue;

                if (!"System Settings".equals(title))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    String name = null;
                    String value = null;

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String val = get(cell, "Value");

                        if ("Name".equals(column))
                            name = val;

                        if ("Value".equals(column))
                            value = val;
                    }

                    if (name != null)
                        map.put(name, value != null ? value : "");
                }
            }
        }

        return map;
    }

    // =====================================================
    // UTILS
    // =====================================================

    private String normalizeKey(String k) {
        return k.toLowerCase()
                .replace(" ", "_")
                .replace("(", "")
                .replace(")", "")
                .replace("/", "_");
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}