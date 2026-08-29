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
package iped.engine.datasource.paraben.sections.sms;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.utils.ParabenDateUtil;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;

public class SmsMmsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<MessageData> messages = extractMessages(xmlChain);

        if (messages.isEmpty()) {
            return;
        }

        for (MessageData m : messages) {

            if (isEmpty(m))
                continue;

            Item item = new Item();

            String title = buildTitle(m);

            item.setName(title);
            item.setPath(rootItem.getPath() + "/Messages/" + title);
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-message"));

            item.setIdInDataSource("paraben-msg-" + m.id);

            String html = buildHtml(m);
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Messages");

            item.getMetadata().set(BasicProps.NAME, normalize(m.body));
            item.getMetadata().set(BasicProps.CONTENT, normalize(m.body));
            item.getMetadata().set(BasicProps.TYPE, "Message");

            String phone = normalize(m.address);

            item.getMetadata().add(ExtraProperties.USER_PHONE, phone);
            item.getMetadata().add(ExtraProperties.PARTICIPANTS, phone);

            if (!phone.equals("-")) {
                item.getMetadata().add(ExtraProperties.COMMUNICATION_TO, phone);
            }

            if (m.dateEpoch != null) {
                Date d = ParabenDateUtil.fromEpoch(m.dateEpoch);
                if (d != null) {
                    item.setCreationDate(d);
                    item.getMetadata().set(ExtraProperties.COMMUNICATION_DATE, d);
                }
            }

            item.getMetadata().add("paraben:message:id", m.id);
            item.getMetadata().add("paraben:message:type", m.type);
            item.getMetadata().add("paraben:message:body", normalize(m.body));
            item.getMetadata().add("paraben:message:address", phone);
            item.getMetadata().add("paraben:message:status", normalize(m.status));
            item.getMetadata().add("paraben:message:box", normalize(m.box));
            item.getMetadata().add("entity:type", "message");

            String direction = "Unknown";
            String box = normalize(m.box).toLowerCase();

            if (box.contains("inbox") || box.contains("received")) {
                direction = "Incoming";
            } else if (box.contains("sent") || box.contains("outbox")) {
                direction = "Outgoing";
            }

            item.getMetadata().set(ExtraProperties.COMMUNICATION_DIRECTION, direction);

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
    }

    private List<MessageData> extractMessages(List<File> xmlChain) throws Exception {

        List<MessageData> list = new ArrayList<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = get(node, "NodeType");
                String title = get(node, "Title");

                if (nodeType == null || !nodeType.contains("SMS"))
                    continue;

                if (title == null || !title.toLowerCase().contains("history"))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                if (rows.getLength() == 0) {
                    System.out.println("⚠️ Empty SMS/MMS node detected: " + title);
                    continue;
                }

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    MessageData m = new MessageData();
                    m.id = get(row, "ID");

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");
                        String tag = get(cell, "Tag");

                        switch (column) {

                            case "Address":
                                m.address = value;
                                break;

                            case "Body":
                                m.body = value;
                                break;

                            case "Date":
                                m.date = value;
                                m.dateEpoch = tag;
                                break;

                            case "Type":
                                m.type = value;
                                break;

                            case "Status":
                                m.status = value;
                                break;

                            case "Message Box":
                                m.box = value;
                                break;
                        }
                    }

                    list.add(m);
                }
            }
        }

        return list;
    }

    private String buildHtml(MessageData m) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Message</h2>");
        html.append("<table border='1' cellpadding='5'>");

        html.append(row("ID", m.id));
        html.append(row("Address", m.address));
        html.append(row("Body", m.body));
        html.append(row("Date", m.date));
        html.append(row("Type", m.type));
        html.append(row("Status", m.status));
        html.append(row("Box", m.box));

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    private String row(String k, String v) {
        return "<tr><td><b>" + k + "</b></td><td>" + normalize(v) + "</td></tr>";
    }

    private boolean isEmpty(MessageData m) {
        return normalize(m.body).equals("-") && normalize(m.address).equals("-");
    }

    private String buildTitle(MessageData m) {
        return normalize(m.address) + " - " + normalize(m.date);
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

    private static class MessageData {
        String id;
        String address;
        String body;
        String date;
        String dateEpoch;
        String type;
        String status;
        String box;
    }
}