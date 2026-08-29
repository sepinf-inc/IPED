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
package iped.engine.datasource.paraben.sections.device;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.util.HtmlTemplate;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeviceInfoSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain, ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        Item deviceNode = new Item();

        deviceNode.setName("Device Info");
        deviceNode.setPath(rootItem.getPath() + "/Device Info");
        deviceNode.setParent(rootItem);
        deviceNode.setHasChildren(false);

        deviceNode.setMediaType(
                org.apache.tika.mime.MediaType.parse("application/x-paraben-device"));

        deviceNode.setIdInDataSource("paraben-device");
        deviceNode.setHash("");

        Map<String, String> props = extractProps(xmlChain);

        String html = HtmlTemplate.buildTable("Device Info", props);

        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ExportFileTask.getLastInstance()
                .insertIntoStorage(deviceNode, bytes, bytes.length);

        deviceNode.setLength((long) bytes.length);

        deviceNode.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));

        deviceNode.getMetadata().set(BasicProps.CATEGORY, "Device Info");

        for (Map.Entry<String, String> e : props.entrySet()) {

            String key = e.getKey()
                    .toLowerCase()
                    .replace(" ", "_")
                    .replace("(", "")
                    .replace(")", "")
                    .replace("/", "_");

            deviceNode.getMetadata().add("paraben:device:" + key, e.getValue());
        }

        deviceNode.setExtraAttribute(
                ExtraProperties.DECODED_DATA,
                true);

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(deviceNode);
    }

    private Map<String, String> extractProps(List<File> xmlChain) throws Exception {

        Map<String, String> props = new LinkedHashMap<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);

                NodeList types = node.getElementsByTagName("NodeType");
                if (types.getLength() == 0)
                    continue;

                String type = types.item(0).getTextContent();

                if (!"DSCase.Phone".equals(type))
                    continue;

                NodeList properties = node.getElementsByTagName("Property");

                for (int j = 0; j < properties.getLength(); j++) {

                    org.w3c.dom.Element prop = (org.w3c.dom.Element) properties.item(j);

                    String name = prop.getElementsByTagName("PropertyName")
                            .item(0).getTextContent();

                    if ("Program timestamp".equalsIgnoreCase(name) ||
                            "Acquisition Type".equalsIgnoreCase(name)) {
                        continue;
                    }
                    String value = "";
                    NodeList valNode = prop.getElementsByTagName("PropertyValue");
                    if (valNode.getLength() > 0) {
                        value = valNode.item(0).getTextContent();
                    }

                    props.put(name, value);
                }
            }
        }

        return props;
    }
}