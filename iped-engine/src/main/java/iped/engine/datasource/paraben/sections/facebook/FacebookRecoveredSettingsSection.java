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

public class FacebookRecoveredSettingsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<RecoveredRow> rows = extractRecovered(xmlChain);

        boolean hasUsefulData = false;

        for (RecoveredRow r : rows) {
            if ((r.key != null && !r.key.trim().isEmpty()
                    && r.value != null && !r.value.trim().isEmpty())
                    || (r.link != null && !r.link.trim().isEmpty())) {

                hasUsefulData = true;
                break;
            }
        }

        if (!hasUsefulData)
            return;

        Map<String, String> htmlData = new LinkedHashMap<>();

        for (RecoveredRow r : rows) {
            if (r.key != null && !r.key.trim().isEmpty()
                    && r.value != null && !r.value.trim().isEmpty()) {

                htmlData.put(r.key, r.value);
            }
        }

        if (htmlData.isEmpty())
            return;

        Item item = new Item();

        item.setName("Facebook Recovered Settings");
        item.setPath(rootItem.getPath() + "/Applications/Facebook/Recovered Settings");
        item.setParent(rootItem);
        item.setHasChildren(true);

        item.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-settings-recovered"));

        item.setIdInDataSource("paraben-facebook-settings-recovered");

        String html = HtmlTemplate.buildTable("Recovered Facebook Settings", htmlData);
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ExportFileTask.getLastInstance()
                .insertIntoStorage(item, bytes, bytes.length);

        item.setLength((long) bytes.length);

        item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
        item.getMetadata().set(BasicProps.CATEGORY, "Applications");
        item.getMetadata().add("facebook:recovered", "true");

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(item);

        boolean hasBinaryChildren = false;

        for (RecoveredRow r : rows) {

            if (r.link == null || r.link.trim().isEmpty())
                continue;

            File f = new File(root, r.link);

            if (!f.exists())
                continue;

            hasBinaryChildren = true;

            Item child = new Item();

            child.setName("Recovered Blob - " + f.getName());
            child.setPath(item.getPath() + "/" + f.getName());
            child.setParent(item);
            child.setHasChildren(false);

            child.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-facebook-recovered-blob"));

            child.setIdInDataSource(f.getAbsolutePath());

            child.setInputStreamFactory(new iped.utils.FileInputStreamFactory(f.toPath()));
            child.setLength(f.length());

            child.getMetadata().add("facebook:recovered", "true");
            child.getMetadata().add("facebook:source", "Recovered Settings");

            if (r.key != null)
                child.getMetadata().add("facebook:key", r.key);

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(child);
        }
    }

    private List<RecoveredRow> extractRecovered(List<File> xmlChain) throws Exception {

        List<RecoveredRow> list = new ArrayList<>();

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

                if (!"Recovered Settings".equalsIgnoreCase(title))
                    continue;

                NodeList rows = node.getElementsByTagName("Row");

                for (int j = 0; j < rows.getLength(); j++) {

                    Element row = (Element) rows.item(j);

                    RecoveredRow data = new RecoveredRow();

                    NodeList cells = row.getElementsByTagName("Cell");

                    for (int k = 0; k < cells.getLength(); k++) {

                        Element cell = (Element) cells.item(k);

                        String column = get(cell, "Column");
                        String value = get(cell, "Value");
                        String link = getOptional(cell, "Link");

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
                            case "Raw Data":
                                data.link = link;
                                break;
                        }
                    }

                    list.add(data);
                }
            }
        }

        return list;
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private String getOptional(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return (n != null && n.getLength() > 0) ? n.item(0).getTextContent() : null;
    }

    private String getAttr(Element el, String attr) {
        return el.hasAttribute(attr) ? el.getAttribute(attr) : "";
    }

    private static class RecoveredRow {
        String key;
        String value;
        String type;
        String link;
    }
}