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

public class FacebookMessengerRecoveredSettingsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<Element> nodes = index.get("Recovered Settings");

        if (nodes == null)
            return;

        Item parent = new Item();
        parent.setName("Facebook Messenger - Recovered Settings");
        parent.setIdInDataSource("paraben-fb-recovered-settings-root");
        parent.setPath(rootItem.getPath() + "/Applications/Facebook Messenger/Recovered Settings");
        parent.setParent(rootItem);
        parent.setHasChildren(true);

        parent.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-fb-recovered-settings"));

        Manager.getInstance().addItemToQueue(parent);
        caseData.incDiscoveredEvidences(1);

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int i = 0; i < rows.getLength(); i++) {

                Element row = (Element) rows.item(i);
                Map<String, String> data = extractRow(row);

                Item item = new Item();

                String key = data.get("Key");
                String rowId = get(row, "ID");
                item.setIdInDataSource("paraben-fb-recovered-setting-" + rowId);

                item.setName((key != null && !key.trim().isEmpty()) ? key : "Recovered Setting");
                item.setPath(parent.getPath() + "/" + item.getName() + "_" + rowId);
                item.setParent(parent);

                item.setMediaType(
                        org.apache.tika.mime.MediaType.parse("application/x-paraben-fb-setting"));

                // HTML simple
                String html = buildHtml(data);
                byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                ExportFileTask.getLastInstance()
                        .insertIntoStorage(item, bytes, bytes.length);

                item.setLength((long) bytes.length);

                item.getMetadata().set(BasicProps.CATEGORY, "Settings");

                Manager.getInstance().addItemToQueue(item);
                caseData.incDiscoveredEvidences(1);
            }
        }
    }

    private Map<String, String> extractRow(Element row) {
        Map<String, String> map = new HashMap<>();

        NodeList cells = row.getElementsByTagName("Cell");

        for (int i = 0; i < cells.getLength(); i++) {
            Element c = (Element) cells.item(i);
            String col = get(c, "Column");
            String val = get(c, "Value");
            map.put(col, val);
        }
        return map;
    }

    private String buildHtml(Map<String, String> data) {
        StringBuilder html = new StringBuilder("<html><body><table border='1'>");

        for (Map.Entry<String, String> e : data.entrySet()) {
            html.append("<tr><td><b>").append(e.getKey())
                    .append("</b></td><td>").append(e.getValue())
                    .append("</td></tr>");
        }

        html.append("</table></body></html>");
        return html.toString();
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}
