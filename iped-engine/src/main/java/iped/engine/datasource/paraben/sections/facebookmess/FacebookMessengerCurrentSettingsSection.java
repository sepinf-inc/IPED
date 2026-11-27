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
package iped.engine.datasource.paraben.sections.facebookmess;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;

import org.w3c.dom.*;

import java.io.File;
import java.util.*;

public class FacebookMessengerCurrentSettingsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<Element> nodes = index.get("Current Settings");

        if (nodes == null || nodes.isEmpty())
            return;

        Item parent = new Item();

        parent.setName("Facebook Messenger - Current Settings");
        parent.setPath(rootItem.getPath() + "/Applications/Facebook Messenger/Current Settings");
        parent.setParent(rootItem);
        parent.setHasChildren(true);

        parent.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-fb-current-settings"));

        parent.setIdInDataSource("paraben-fb-current-settings");

        parent.getMetadata().set(BasicProps.CATEGORY, "Applications");

        Manager.getInstance().addItemToQueue(parent);
        caseData.incDiscoveredEvidences(1);

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int i = 0; i < rows.getLength(); i++) {

                Element row = (Element) rows.item(i);

                Map<String, String> data = extractRow(row);

                String key = data.get("Key");
                String value = data.get("Value");

                Item item = new Item();

                item.setName(key != null && !key.isEmpty() ? key : "Setting");
                item.setPath(parent.getPath() + "/" + item.getName());
                item.setParent(parent);
                item.setHasChildren(false);

                item.setMediaType(
                        org.apache.tika.mime.MediaType.parse("application/x-paraben-fb-setting"));

                item.setIdInDataSource("paraben-fb-current-" + i);

                String html = buildHtml(data);
                byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                ExportFileTask.getLastInstance()
                        .insertIntoStorage(item, bytes, bytes.length);

                item.setLength((long) bytes.length);

                item.getMetadata().set(BasicProps.CATEGORY, "Settings");
                item.getMetadata().set(BasicProps.NAME, key);
                item.getMetadata().add("paraben:key", key);
                item.getMetadata().add("paraben:value", value);

                Manager.getInstance().addItemToQueue(item);
                caseData.incDiscoveredEvidences(1);
            }
        }
    }

    private Map<String, String> extractRow(Element row) {

        Map<String, String> map = new HashMap<>();

        NodeList cells = row.getElementsByTagName("Cell");

        for (int i = 0; i < cells.getLength(); i++) {

            Element cell = (Element) cells.item(i);

            String col = get(cell, "Column");
            String val = get(cell, "Value");

            map.put(col, val);
        }

        return map;
    }

    private String buildHtml(Map<String, String> data) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Current Setting</h2>");
        html.append("<table border='1'>");

        for (Map.Entry<String, String> e : data.entrySet()) {

            html.append("<tr>")
                    .append("<td><b>").append(normalize(e.getKey())).append("</b></td>")
                    .append("<td>").append(normalize(e.getValue())).append("</td>")
                    .append("</tr>");
        }

        html.append("</table></body></html>");

        return html.toString();
    }

    private String normalize(String v) {
        if (v == null || v.trim().isEmpty())
            return "-";
        return v.trim();
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}