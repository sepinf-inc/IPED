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
package iped.engine.datasource.paraben.sections.timeline;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.utils.ParabenDateUtil;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import java.util.Date;
import org.w3c.dom.*;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class UserActivityTimelineSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<org.w3c.dom.Element>> index) throws Exception {

        List<RowData> rows = extractTimeline(xmlChain);

        if (rows.isEmpty())
            return;

        for (RowData r : rows) {

            Item item = new Item();

            String safeApp = normalize(r.appName);

            item.setName(safeApp + " @ " + normalize(r.time));
            item.setPath(rootItem.getPath() + "/User Activity Timeline/" + item.getName());
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-timeline"));

            item.setIdInDataSource("paraben-timeline-" + r.id);

            // 🔥 HTML individual
            String html = buildHtmlSingle(r);

            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            // =====================================================
            // 🔥 BASIC PROPS
            // =====================================================

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Timeline");

            item.getMetadata().set(BasicProps.NAME, safeApp);
            item.getMetadata().set(BasicProps.TYPE, normalize(r.type));
            item.getMetadata().set(BasicProps.CONTENT, normalize(r.packageName));

            // 🔥 TIME (epoch)
            // 🔥 TIME (epoch → Date real)
            Date d = ParabenDateUtil.fromEpoch(r.epoch);
            if (d != null) {
                item.setCreationDate(d);
            }

            // =====================================================
            // 🔥 EXTRA METADATA
            // =====================================================

            item.getMetadata().add("paraben:timeline:id", r.id);
            item.getMetadata().add("paraben:timeline:time", normalize(r.time));
            item.getMetadata().add("paraben:timeline:app", safeApp);
            item.getMetadata().add("paraben:timeline:package", normalize(r.packageName));
            item.getMetadata().add("paraben:timeline:activity", normalize(r.activity));

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
    }

    // =====================================================
    // 🔥 PARSEO
    // =====================================================
    private List<RowData> extractTimeline(List<File> xmlChain) throws Exception {

        List<RowData> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String title = get(node, "Title");

                if (!"User Activity Timeline".equals(title))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    RowData data = new RowData();
                    data.id = get(row, "ID");

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");
                        String tag = get(cell, "Tag");

                        switch (column) {
                            case "Time":
                                data.time = value;
                                data.epoch = tag;
                                break;
                            case "Application Name":
                                data.appName = value;
                                break;
                            case "Internal Application Name":
                                data.packageName = value;
                                break;
                            case "Internal Activity Name":
                                data.activity = value;
                                break;
                            case "Type":
                                data.type = value;
                                break;
                        }
                    }

                    if (data.id != null && !seen.contains(data.id)) {
                        list.add(data);
                        seen.add(data.id);
                    }
                }
            }
        }

        return list;
    }

    // =====================================================
    // 🔥 HTML POR EVENTO
    // =====================================================
    private String buildHtmlSingle(RowData r) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>User Activity Event</h2>");
        html.append("<table border='1' cellpadding='5'>");

        html.append(row("ID", r.id));
        html.append(row("Time", r.time));
        html.append(row("Application", r.appName));
        html.append(row("Package", r.packageName));
        html.append(row("Activity", r.activity));
        html.append(row("Type", r.type));

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    private String row(String k, String v) {
        return "<tr><td><b>" + k + "</b></td><td>" + normalize(v) + "</td></tr>";
    }

    private String normalize(String v) {
        if (v == null || v.trim().isEmpty() || v.equalsIgnoreCase("None"))
            return "-";
        return v.trim();
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    // =====================================================
    // 🔥 DTO
    // =====================================================
    private static class RowData {
        String id;
        String time;
        String epoch;
        String appName;
        String packageName;
        String activity;
        String type;
    }
}