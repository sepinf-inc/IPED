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
package iped.engine.datasource.paraben.sections.calls;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.utils.ParabenDateUtil;
import iped.engine.datasource.paraben.util.HtmlTemplate;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CallHistorySection implements ParabenSection {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CallHistorySection.class);

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        LOGGER.info("📞 CallHistorySection START");

        for (File xml : xmlChain) {

            LOGGER.info("📄 Parsing XML: {}", xml.getName());

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = get(node, "NodeType");
                String title = get(node, "Title");

                if (!"DSCase.folder".equals(nodeType) ||
                        !"Call History".equalsIgnoreCase(title)) {
                    continue;
                }

                LOGGER.info("📂 Found Call History folder");

                NodeList grids = node.getElementsByTagName("Node");

                for (int j = 0; j < grids.getLength(); j++) {

                    Node n = grids.item(j);
                    if (!(n instanceof Element))
                        continue;

                    Element grid = (Element) n;

                    if (!"DSCase.CallsHistory".equalsIgnoreCase(get(grid, "NodeType"))) {
                        continue;
                    }

                    LOGGER.info("📊 Found CallsHistory GRID");

                    NodeList rows = grid.getElementsByTagName("Row");

                    for (int k = 0; k < rows.getLength(); k++) {

                        Map<String, String> data = extractRow((Element) rows.item(k));

                        if (data.isEmpty() || data.get("Date") == null)
                            continue;

                        LOGGER.info("📞 Call parsed: {}", data);

                        createCallItem(rootItem, data, caseData);
                    }
                }
            }
        }
    }

    private void createCallItem(Item rootItem,
            Map<String, String> data,
            ICaseData caseData) throws Exception {

        String number = data.get("Number");
        String date = data.get("Date");
        String type = data.get("Type");

        Item call = new Item();

        call.setIdInDataSource("paraben-call-" + UUID.randomUUID());

        call.setName((type != null ? type : "Call") + " - " +
                (number != null ? number : "Unknown"));

        call.setPath(rootItem.getPath() + "/Call History/" +
                UUID.randomUUID() + "_" + call.getName());

        call.setParent(rootItem);
        call.setHasChildren(false);

        call.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-call"));

        String epoch = data.get("Date_tag");
        Date callDate = null;

        if (epoch != null) {
            callDate = ParabenDateUtil.fromEpoch(epoch);
            if (callDate != null) {
                call.setCreationDate(callDate);
            }
        }

        String html = HtmlTemplate.buildTable("Call Record", data);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        ExportFileTask.getLastInstance()
                .insertIntoStorage(call, bytes, bytes.length);

        call.setLength((long) bytes.length);

        call.getMetadata().set(BasicProps.CONTENT, html);

        call.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
        call.getMetadata().set(BasicProps.CATEGORY, "Communication");
        call.getMetadata().set(BasicProps.NAME,
                number != null ? number : "Unknown");
        call.getMetadata().set(BasicProps.TYPE,
                type != null ? type : "Call");

        if (number != null) {
            call.getMetadata().add(ExtraProperties.COMMUNICATION_TO, number);
        }

        call.getMetadata().set(
                ExtraProperties.COMMUNICATION_DIRECTION,
                normalizeDirection(type));

        if (callDate != null) {
            call.getMetadata().set(
                    ExtraProperties.COMMUNICATION_DATE, callDate);
        }

        call.getMetadata().add("paraben:call:number", number);
        call.getMetadata().add("paraben:call:type", type);
        call.getMetadata().add("paraben:call:duration", data.get("Duration"));
        call.getMetadata().add("paraben:call:new", data.get("New"));

        if (data.get("Name (Cached)") != null) {
            call.getMetadata().add(
                    "paraben:call:name_cached",
                    data.get("Name (Cached)"));
        }

        call.getMetadata().add("entity:type", "call");

        Manager.getInstance().addItemToQueue(call);
        caseData.incDiscoveredEvidences(1);

        LOGGER.info("📦 Call item created: {}", call.getName());
    }

    private String normalizeDirection(String type) {

        if (type == null)
            return "Unknown";

        String t = type.toLowerCase();

        if (t.contains("incoming"))
            return "Incoming";
        if (t.contains("outgoing"))
            return "Outgoing";
        if (t.contains("missed"))
            return "Missed";

        return type;
    }

    private Map<String, String> extractRow(Element row) {

        Map<String, String> map = new HashMap<>();

        NodeList cells = row.getElementsByTagName("Cell");

        for (int i = 0; i < cells.getLength(); i++) {

            Element cell = (Element) cells.item(i);

            String col = get(cell, "Column");
            String val = get(cell, "Value");
            String tag = get(cell, "Tag");

            map.put(col, val);

            if (tag != null && !tag.isEmpty()) {
                map.put(col + "_tag", tag);
            }
        }

        return map;
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}