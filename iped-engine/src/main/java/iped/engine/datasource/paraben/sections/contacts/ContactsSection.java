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
package iped.engine.datasource.paraben.sections.contacts;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;

public class ContactsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<ContactData> contacts = extractContacts(xmlChain);

        Set<String> seen = new HashSet<>();

        for (ContactData c : contacts) {

            if (isEmptyContact(c))
                continue;

            String name = normalize(c.name);
            String phone = normalizePhone(c.phone);
            String email = normalize(c.email);

            // 🔥 deduplicación
            String key = name + "|" + phone + "|" + email;
            if (!seen.add(key))
                continue;

            Item item = new Item();

            item.setName("Contact - " + name);
            item.setPath(rootItem.getPath() + "/Contacts/" + name);
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-contact"));

            item.setIdInDataSource("paraben-contact-" + c.id);

            // HTML
            String html = buildHtml(c);
            byte[] bytes = html.getBytes("UTF-8");

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            // =====================================================
            // 🔥 METADATA
            // =====================================================

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Contacts");

            item.getMetadata().set(BasicProps.NAME, name);
            item.getMetadata().add(BasicProps.ID, c.id);

            item.getMetadata().add(ExtraProperties.USER_NAME, name);

            if (!phone.equals("-"))
                item.getMetadata().add(ExtraProperties.USER_PHONE, phone);

            if (!email.equals("-"))
                item.getMetadata().add(ExtraProperties.USER_EMAIL, email);

            String account = normalize(c.account);
            if (!account.equals("-"))
                item.getMetadata().add(ExtraProperties.USER_ACCOUNT, account);

            String notes = normalize(c.notes);
            if (!notes.equals("-"))
                item.getMetadata().set(ExtraProperties.USER_NOTES, notes);

            String group = normalize(c.group);
            if (!group.equals("-"))
                item.getMetadata().add("paraben:contact:group", group);

            String times = normalize(c.times);
            if (!times.equals("-"))
                item.getMetadata().add("paraben:contact:times_contacted", times);

            item.getMetadata().add("entity:type", "contact");

            // 🔥 FOTO → referencia
            if (c.photoLink != null && !c.photoLink.isEmpty()) {

                String normalizedPath = c.photoLink.replace("Binary Files/", "");

                item.getMetadata().add("paraben:contact:photo_path", normalizedPath);

                List<String> links = new ArrayList<>();
                links.add(normalizedPath);

                item.setExtraAttribute("paraben:linked_items", normalizedPath);
            }

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
    }

    // =====================================================
    // 🔥 PARSEO XML
    // =====================================================
    private List<ContactData> extractContacts(List<File> xmlChain) throws Exception {

        List<ContactData> list = new ArrayList<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String nodeType = get(node, "NodeType");

                if (!"DSCase.Phonebook".equals(nodeType))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    ContactData c = new ContactData();
                    c.id = get(row, "ID");

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");
                        String link = getOptional(cell, "Link");

                        switch (column) {

                            case "Name":
                                c.name = value;
                                break;

                            case "Phone (Mobile)":
                            case "Phone (Móvil)":
                                if (c.phone == null || c.phone.isEmpty())
                                    c.phone = value;
                                break;

                            case "Email":
                                c.email = value;
                                break;

                            case "Notes":
                                c.notes = value;
                                break;

                            case "Group":
                                c.group = value;
                                break;

                            case "Account":
                                c.account = value;
                                break;

                            case "Times Contacted":
                                c.times = value;
                                break;

                            case "Photo":
                                c.photoLink = link;
                                break;
                        }
                    }

                    list.add(c);
                }
            }
        }

        return list;
    }

    // =====================================================
    // 🔥 HTML
    // =====================================================
    private String buildHtml(ContactData c) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Contact</h2>");
        html.append("<table border='1' cellpadding='5'>");

        html.append(row("Name", c.name));
        html.append(row("Phone", c.phone));
        html.append(row("Email", c.email));
        html.append(row("Notes", c.notes));
        html.append(row("Group", c.group));
        html.append(row("Account", c.account));
        html.append(row("Times Contacted", c.times));

        if (c.photoLink != null && !c.photoLink.isEmpty()) {

            String path = c.photoLink.replace("Binary Files/", "");

            html.append("<tr><td><b>Photo</b></td><td>");
            html.append("<img src='").append(path).append("' width='120'/>");
            html.append("</td></tr>");
        }

        html.append("</table>");
        html.append("</body></html>");

        return html.toString();
    }

    private String row(String k, String v) {
        return "<tr><td><b>" + k + "</b></td><td>" + normalize(v) + "</td></tr>";
    }

    // =====================================================
    // UTILS
    // =====================================================

    private boolean isEmptyContact(ContactData c) {
        return normalize(c.name).equals("-")
                && normalize(c.phone).equals("-")
                && normalize(c.email).equals("-");
    }

    private String normalize(String v) {
        if (v == null || v.trim().isEmpty() || v.equalsIgnoreCase("None"))
            return "-";
        return v.trim();
    }

    private String normalizePhone(String v) {
        if (v == null || v.trim().isEmpty())
            return "-";
        return v.replaceAll("[^0-9+]", "");
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private String getOptional(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return (n != null && n.getLength() > 0) ? n.item(0).getTextContent() : null;
    }

    // =====================================================
    // DTO
    // =====================================================
    private static class ContactData {
        String id;
        String name;
        String phone;
        String email;
        String notes;
        String group;
        String account;
        String times;
        String photoLink;
    }
}