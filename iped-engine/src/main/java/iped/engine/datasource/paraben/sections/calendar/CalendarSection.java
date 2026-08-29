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
package iped.engine.datasource.paraben.sections.calendar;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.utils.ParabenDateUtil;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;

public class CalendarSection implements ParabenSection {

    @Override
    public void process(File root,
            Item rootItem,
            List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<EventData> events = extractEvents(xmlChain);
        List<CalendarInfo> calendars = extractCalendars(index);

        for (EventData e : events) {

            if (isEmpty(e))
                continue;

            Item item = new Item();

            String name = normalize(e.summary);

            item.setName("Calendar Event - " + name + " (" + normalize(e.start) + ")");
            item.setPath(rootItem.getPath() + "/Calendar/" + name);
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-calendar"));

            item.setIdInDataSource("paraben-calendar-" + e.id);

            String html = buildHtml(e);
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Calendar");

            item.getMetadata().set(BasicProps.NAME, name);
            item.getMetadata().add(BasicProps.TYPE, normalize(e.status));

            if (e.startEpoch != null) {
                Date d = ParabenDateUtil.fromEpoch(e.startEpoch);
                if (d != null)
                    item.setCreationDate(d);
            }

            item.getMetadata().add("paraben:calendar:summary", normalize(e.summary));
            item.getMetadata().add("paraben:calendar:location", normalize(e.location));
            item.getMetadata().add("paraben:calendar:description", normalize(e.description));
            item.getMetadata().add("paraben:calendar:start", normalize(e.start));
            item.getMetadata().add("paraben:calendar:end", normalize(e.end));
            item.getMetadata().add("paraben:calendar:timezone", normalize(e.timezone));
            item.getMetadata().add("paraben:calendar:status", normalize(e.status));
            item.getMetadata().add("paraben:calendar:alarm", normalize(e.hasAlarm));
            item.getMetadata().add("paraben:calendar:deleted", normalize(e.deleted));

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
        for (CalendarInfo c : calendars) {

            Item item = new Item();

            String name = normalize(c.name);

            item.setName("Calendar - " + name);
            item.setPath(rootItem.getPath() + "/Calendar/Calendars/" + name);
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-calendar-list"));

            item.setIdInDataSource("paraben-calendar-list-" + name.hashCode());

            String html = buildHtmlCalendar(c);
            byte[] bytes = html.getBytes("UTF-8");

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Calendar");

            item.getMetadata().add("paraben:calendar:name", normalize(c.name));
            item.getMetadata().add("paraben:calendar:display", normalize(c.displayName));
            item.getMetadata().add("paraben:calendar:timezone", normalize(c.timezone));
            item.getMetadata().add("paraben:calendar:selected", normalize(c.selected));
            item.getMetadata().add("paraben:calendar:hidden", normalize(c.hidden));

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
    }

    private List<EventData> extractEvents(List<File> xmlChain) throws Exception {

        List<EventData> list = new ArrayList<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = get(node, "NodeType");
                String title = get(node, "Title");

                if (!"DSCase.Calendar".equals(nodeType))
                    continue;

                if (!"Events".equals(title))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    EventData e = new EventData();
                    e.id = get(row, "ID");

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");
                        String tag = get(cell, "Tag");

                        switch (column) {

                            case "Summary":
                                e.summary = value;
                                break;

                            case "Location":
                                e.location = value;
                                break;

                            case "Description":
                                e.description = value;
                                break;

                            case "Start Time":
                                e.start = value;
                                e.startEpoch = tag;
                                break;

                            case "End Time":
                                e.end = value;
                                e.endEpoch = tag;
                                break;

                            case "Timezone":
                                e.timezone = value;
                                break;

                            case "Status":
                                e.status = value;
                                break;

                            case "Has Alarm":
                                e.hasAlarm = value;
                                break;

                            case "Is Deleted":
                                e.deleted = value;
                                break;
                        }
                    }

                    list.add(e);
                }
            }
        }

        return list;
    }

    private List<CalendarInfo> extractCalendars(Map<String, List<Element>> index) {

        List<CalendarInfo> list = new ArrayList<>();

        List<Element> nodes = index.get("Calendars");

        if (nodes == null)
            return list;

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int j = 0; j < rows.getLength(); j++) {

                Element row = (Element) rows.item(j);

                CalendarInfo c = new CalendarInfo();

                NodeList cells = row.getElementsByTagName("Cell");

                for (int k = 0; k < cells.getLength(); k++) {

                    Element cell = (Element) cells.item(k);

                    String column = get(cell, "Column");
                    String value = get(cell, "Value");

                    switch (column) {

                        case "Name":
                            c.name = value;
                            break;

                        case "Display Name":
                            c.displayName = value;
                            break;

                        case "Selected":
                            c.selected = value;
                            break;

                        case "Hidden":
                            c.hidden = value;
                            break;

                        case "Timezone":
                            c.timezone = value;
                            break;

                        case "URL":
                            c.url = value;
                            break;
                    }
                }

                list.add(c);
            }
        }

        return list;
    }

    private String buildHtml(EventData e) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Calendar Event</h2>");
        html.append("<table border='1' cellpadding='5'>");

        html.append(row("Summary", e.summary));
        html.append(row("Location", e.location));
        html.append(row("Description", e.description));
        html.append(row("Start", e.start));
        html.append(row("End", e.end));
        html.append(row("Timezone", e.timezone));
        html.append(row("Status", e.status));
        html.append(row("Has Alarm", e.hasAlarm));
        html.append(row("Deleted", e.deleted));

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    private String row(String k, String v) {
        return "<tr><td><b>" + k + "</b></td><td>" + normalize(v) + "</td></tr>";
    }

    private boolean isEmpty(EventData e) {
        return normalize(e.summary).equals("-");
    }

    private String normalize(String v) {
        if (v == null || v.trim().isEmpty() || v.equalsIgnoreCase("None"))
            return "-";
        return v.trim();
    }

    private String get(Element el, String tag) {

        NodeList children = el.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node n = children.item(i);

            if (n instanceof Element && tag.equals(n.getNodeName())) {
                return n.getTextContent();
            }
        }

        return "";
    }

    private static class EventData {
        String id;
        String summary;
        String location;
        String description;
        String start;
        String end;
        String startEpoch;
        String endEpoch;
        String timezone;
        String status;
        String hasAlarm;
        String deleted;
    }

    private String buildHtmlCalendar(CalendarInfo c) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Calendar</h2>");
        html.append("<table border='1' cellpadding='5'>");

        html.append(row("Name", c.name));
        html.append(row("Display Name", c.displayName));
        html.append(row("Selected", c.selected));
        html.append(row("Hidden", c.hidden));
        html.append(row("Timezone", c.timezone));
        html.append(row("URL", c.url));

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    private static class CalendarInfo {
        String name;
        String displayName;
        String selected;
        String hidden;
        String timezone;
        String url;
    }
}