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
package iped.engine.datasource;

import iped.data.ICaseData;
import iped.datasource.IDataSource;
import iped.engine.core.Manager;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.properties.ExtraProperties;
import iped.engine.datasource.paraben.sections.info.ParabenInfoSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.tika.metadata.Metadata;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

public class ParabenXmlReader extends DataSourceReader {

    private static final Logger LOGGER = LogManager.getLogger(ParabenXmlReader.class);

    private Item rootItem;
    private boolean listOnly;

    public ParabenXmlReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
        this.listOnly = listOnly;
    }

    // =====================================================
    // 🔍 DETECCIÓN
    // =====================================================
    @Override
    public boolean isSupported(File datasource) {

        if (!datasource.isDirectory())
            return false;

        File xml = new File(datasource, "Data_Structure.xml");
        File bin = new File(datasource, "Binary Files");

        if (!xml.exists() || !bin.exists())
            return false;

        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList list = doc.getElementsByTagName("ProductName");

            if (list.getLength() == 0)
                return false;

            String product = list.item(0).getTextContent();

            boolean ok = product != null && product.toLowerCase().contains("paraben");

            if (ok)
                LOGGER.info("✔ Paraben datasource detectado");

            return ok;

        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================
    // 🚀 ENTRY POINT
    // =====================================================
    @Override
    public void read(File root) throws Exception {
        read(root, null);
    }

    @Override
    public void read(File root, Item parent) throws Exception {

        LOGGER.info("=== ParabenXmlReader === {}", root.getAbsolutePath());

        addRootItem(root, parent);

        if (listOnly)
            return;

        File baseXml = new File(root, "Data_Structure.xml");

        List<File> xmlChain = collectXmlChain(root, baseXml);
        // 🔥 índice global (une todos los XML)
        Map<String, List<org.w3c.dom.Element>> index = buildIndex(xmlChain);
        // =====================================================
        // 🧠 DATASET XML (para parsers)
        // =====================================================
        Item xmlDataset = new Item();

        xmlDataset.setName("Paraben XML Dataset");
        xmlDataset.setPath(rootItem.getPath() + "/Paraben XML Dataset");
        xmlDataset.setParent(rootItem);
        xmlDataset.setHasChildren(true);

        xmlDataset.setIdInDataSource("paraben-dataset-" + root.getAbsolutePath().hashCode());

        xmlDataset.setMediaType(org.apache.tika.mime.MediaType.application("x-paraben-xml"));

        xmlDataset.getMetadata().set("Content-Type", "application/x-paraben-xml");
        xmlDataset.getMetadata().set(Metadata.CONTENT_TYPE, "application/xml");
        xmlDataset.getMetadata().set("Content-Type", "application/x-paraben-xml");
        // 🔥 CLAVE
        xmlDataset.setExtraAttribute("paraben_dataset", "true");
        xmlDataset.getMetadata().set("paraben:isDataset", "true");
        // XMLs
        for (File xml : xmlChain) {
            xmlDataset.getMetadata().add("paraben:xml", xml.getAbsolutePath());
        }
        xmlDataset.getMetadata().set("embeddedResourceType", "dataset");
        xmlDataset.setExtraAttribute(
                ExtraProperties.DATASOURCE_READER,
                ParabenXmlReader.class.getSimpleName());

        Manager.getInstance().addItemToQueue(xmlDataset);

        List<iped.engine.datasource.paraben.sections.ParabenSection> sections = Arrays.asList(
                new iped.engine.datasource.paraben.sections.device.DeviceInfoSection(),
                new iped.engine.datasource.paraben.sections.info.ParabenInfoSection(),
                new iped.engine.datasource.paraben.sections.timeline.UserActivityTimelineSection(),
                new iped.engine.datasource.paraben.sections.contacts.ContactsSection(),
                new iped.engine.datasource.paraben.sections.sms.SmsMmsSection(),
                new iped.engine.datasource.paraben.sections.calendar.CalendarSection(),
                new iped.engine.datasource.paraben.sections.apps.ApplicationSection(),
                new iped.engine.datasource.paraben.sections.facebook.FacebookSettingsSection(),
                new iped.engine.datasource.paraben.sections.facebook.FacebookRecoveredSettingsSection(),
                new iped.engine.datasource.paraben.sections.facebook.FacebookRecoveredConversationsSection(),
                new iped.engine.datasource.paraben.sections.facebook.FacebookConversationsSection(),
                new iped.engine.datasource.paraben.sections.facebookmess.FacebookMessengerConversationsSection(),
                new iped.engine.datasource.paraben.sections.facebookmess.FacebookMessengerCurrentSettingsSection(),
                new iped.engine.datasource.paraben.sections.facebookmess.FacebookMessengerRecoveredSettingsSection(),
                new iped.engine.datasource.paraben.sections.calls.CallHistorySection(),
                new iped.engine.datasource.paraben.sections.settings.SettingsSection());

        for (iped.engine.datasource.paraben.sections.ParabenSection section : sections) {
            section.process(root, rootItem, xmlChain, caseData, index);
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);

        SAXParser parser = factory.newSAXParser();

        ParabenHandler handler = new ParabenHandler(root, rootItem, caseData);

        for (File xml : xmlChain) {
            LOGGER.info("📄 Parsing XML: {}", xml.getName());

            handler.setCurrentXml(xml.getName()); // 🔥 CLAVE

            parser.parse(xml, handler);
        }
    }

    // =====================================================
    // 🌳 ROOT ITEM
    // =====================================================
    private void addRootItem(File root, Item parent) throws InterruptedException {

        if (listOnly) {
            caseData.incDiscoveredEvidences(1);
            return;
        }

        IDataSource ds = new DataSource(root);

        rootItem = new Item();
        rootItem.setDataSource(ds);
        rootItem.setName(root.getName());
        rootItem.setPath(root.getName());
        rootItem.setRoot(true);
        rootItem.setHasChildren(true);
        rootItem.setIdInDataSource("paraben-root");
        rootItem.setHash("");

        rootItem.setExtraAttribute(
                ExtraProperties.DATASOURCE_READER,
                this.getClass().getSimpleName());

        Manager.getInstance().addItemToQueue(rootItem);

        caseData.incDiscoveredEvidences(1);
    }

    // =====================================================
    // 🔗 XML CHAIN
    // =====================================================
    private List<File> collectXmlChain(File root, File baseXml) throws Exception {

        List<File> list = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        File current = baseXml;

        while (true) {

            if (!visited.add(current.getName()))
                throw new RuntimeException("Loop XML");

            list.add(current);

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(current);

            NodeList links = doc.getElementsByTagName("forwHLink");

            if (links.getLength() == 0)
                break;

            String next = links.item(0).getTextContent();

            if (next == null || next.isEmpty())
                break;

            current = new File(root, next.trim());
        }

        return list;
    }

    // =====================================================
    // 🧠 GLOBAL NODE INDEX (UNE TODOS LOS XML)
    // =====================================================
    private Map<String, List<org.w3c.dom.Element>> buildIndex(List<File> xmlChain) throws Exception {

        Map<String, List<org.w3c.dom.Element>> map = new HashMap<>();

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);

                NodeList titles = node.getElementsByTagName("Title");

                if (titles.getLength() == 0)
                    continue;

                String title = titles.item(0).getTextContent();

                if (title == null || title.trim().isEmpty())
                    continue;

                map.computeIfAbsent(title.trim(), k -> new ArrayList<>()).add(node);
            }
        }

        return map;
    }

    // =====================================================
    // 🧠 SAX HANDLER
    // =====================================================
    private class ParabenHandler extends DefaultHandler {

        private File root;
        private Item rootItem;
        private ICaseData caseData;
        private StringBuilder chars = new StringBuilder();

        private boolean inBinary = false;

        private String itemId;
        private Map<String, String> props = new HashMap<>();
        private Map<String, String> propsTag = new HashMap<>();

        private String currentPropName;
        private String currentXml;

        public ParabenHandler(File root, Item rootItem, ICaseData caseData) {
            this.root = root;
            this.rootItem = rootItem;
            this.caseData = caseData;
        }

        public void setCurrentXml(String xmlName) {
            this.currentXml = xmlName;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {

            chars.setLength(0);

            if ("Node".equals(qName)) {

                String type = atts.getValue("xsi:type");

                if ("Binary".equals(type)) {
                    inBinary = true;
                    props.clear();
                    propsTag.clear(); // 🔥 IMPORTANTE
                    itemId = null;
                }
            }

            if ("Property".equals(qName)) {
                currentPropName = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            chars.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            String value = chars.toString().trim();

            if (!inBinary)
                return;

            switch (qName) {

                case "ItemID":
                    itemId = value;
                    break;

                case "PropertyName":
                    currentPropName = value;
                    break;

                case "PropertyValue":
                    if (currentPropName != null)
                        props.put(currentPropName, value);
                    break;
                case "Tag":
                    if (currentPropName != null)
                        propsTag.put(currentPropName, value);
                    break;
                case "Node":

                    try {

                        Item item = iped.engine.datasource.paraben.ParabenItemBuilder.build(
                                root,
                                rootItem,
                                itemId,
                                props,
                                propsTag,
                                currentXml);

                        if (item != null) {
                            Manager.getInstance().addItemToQueue(item);
                            caseData.incDiscoveredEvidences(1);
                        }

                    } catch (InterruptedException e) {
                        throw new SAXException(e); // 👈 igual que UFED
                    }

                    inBinary = false;
                    break;
            }
        }

    }
}