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
package iped.engine.datasource.paraben.sections.info;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.util.HtmlTemplate;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParabenInfoSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        Map<String, String> props = extractInfo(xmlChain);

        Item item = new Item();

        item.setName("Paraben Info");
        item.setPath(rootItem.getPath() + "/Paraben Info");
        item.setParent(rootItem);
        item.setHasChildren(false);

        item.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-info"));

        item.setIdInDataSource("paraben-info");

        String html = HtmlTemplate.buildTable("Paraben Info", props);

        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ExportFileTask.getLastInstance()
                .insertIntoStorage(item, bytes, bytes.length);

        item.setLength((long) bytes.length);
        item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
        item.getMetadata().set(BasicProps.CATEGORY, "Case Info");

        for (Map.Entry<String, String> e : props.entrySet()) {
            item.getMetadata().add("paraben:info:" + e.getKey(), e.getValue());
        }

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(item);
    }

    private Map<String, String> extractInfo(List<File> xmlChain) throws Exception {

        Map<String, String> props = new LinkedHashMap<>();

        File xml = xmlChain.get(0);

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xml);

        props.put("Program Name", get(doc, "ProductName"));
        props.put("Program Version", get(doc, "ProductVersion"));
        props.put("Program Site", get(doc, "ProductSite"));

        props.put("UTC Zone", get(doc, "UtcZone"));
        props.put("UTC Offset (minutes)", get(doc, "UtcOffset"));

        NodeList nodes = doc.getElementsByTagName("Node");

        for (int i = 0; i < nodes.getLength(); i++) {

            org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);

            NodeList types = node.getElementsByTagName("NodeType");
            if (types.getLength() == 0)
                continue;

            String type = types.item(0).getTextContent();

            if (!"DSCase.Phone".equals(type))
                continue;

            props.put("Device Title", getFromNode(node, "Title"));
            props.put("Device ItemID", getFromNode(node, "ItemID"));

            NodeList properties = node.getElementsByTagName("Property");

            for (int j = 0; j < properties.getLength(); j++) {

                org.w3c.dom.Element prop = (org.w3c.dom.Element) properties.item(j);

                String name = getFromNode(prop, "PropertyName");
                String value = getFromNode(prop, "PropertyValue");

                if ("Program timestamp".equalsIgnoreCase(name)) {
                    props.put("Acquisition Timestamp", value);

                    String epoch = getFromNode(prop, "Tag");
                    if (!epoch.isEmpty()) {
                        props.put("Acquisition Timestamp (epoch)", epoch);
                    }
                }

                if ("Acquisition Type".equalsIgnoreCase(name)) {
                    props.put("Acquisition Type", value);
                }
            }
        }

        return props;
    }

    private String getFromNode(org.w3c.dom.Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private String get(Document doc, String tag) {
        NodeList n = doc.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}