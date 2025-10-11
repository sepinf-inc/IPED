package iped.engine.datasource;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.datasource.IDataSource;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ParsingTaskConfig;
import iped.engine.core.Manager;
import iped.engine.data.CaseData;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.engine.datasource.ufed.UfedModelHandler;
import iped.engine.datasource.ufed.UfedModelHandler.UfedModelListener;
import iped.engine.io.MetadataInputStreamFactory;
import iped.engine.io.UFDRInputStreamFactory;
import iped.engine.io.UFEDXMLWrapper;
import iped.engine.localization.Messages;
import iped.engine.task.ExportFileTask;
import iped.engine.task.die.DIETask;
import iped.engine.util.Util;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.ufed.UfedChatParser;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.PhoneParsingConfig;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.FileInputStreamFactory;
import iped.utils.IOUtil;
import iped.utils.LocalizedFormat;
import iped.utils.SimpleHTMLEncoder;

public class UfedXmlReader extends DataSourceReader {

    private static Logger LOGGER = LogManager.getLogger(UfedXmlReader.class);

    private final Level CONSOLE = Level.getLevel("MSG"); //$NON-NLS-1$

    private static final String[] HEADER_STRINGS = { "project id", "extractionType", "sourceExtractions" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private static final String AVATAR_PATH_META = ExtraProperties.UFED_META_PREFIX + "contactphoto_extracted_path"; //$NON-NLS-1$
    private static final String ATTACH_PATH_META = ExtraProperties.UFED_META_PREFIX + "attachment_extracted_path"; //$NON-NLS-1$

    private static final String MEDIA_CLASSES_PROPERTY = ExtraProperties.UFED_META_PREFIX + "mediaClasses"; //$NON-NLS-1$
    private static final String MEDIA_CLASSES_SCORE_PREFIX = ExtraProperties.UFED_META_PREFIX + "mediaClassScore:"; //$NON-NLS-1$
    private static final float MEDIA_CLASSES_THRESHOLD = 50.0f;
    private static final double MISSING_FRAME_SCORE = 49.99f;

    public static final String UFED_ID = ExtraProperties.UFED_META_PREFIX + "id"; //$NON-NLS-1$
    public static final String UFED_MIME_PREFIX = MediaTypes.UFED_MIME_PREFIX;
    public static final String UFED_EMAIL_MIME = MediaTypes.UFED_EMAIL_MIME.toString();
    public static final String UFED_CONTACTPHOTO_MIME = UFED_MIME_PREFIX + "contactphoto";
    public static final String UFED_NATIVE_SCENE_CLASSIFICATION = ExtraProperties.UFED_META_PREFIX + "Native Scene Classification";
    public static final String MSISDN_PROP = "MSISDN";

    private static final String EMPTY_EXTRACTION_STR = "-";

    private static final String FILE_ID_ATTR = ExtraProperties.UFED_META_PREFIX + "file_id"; //$NON-NLS-1$
    private static final String LOCAL_PATH_META = ExtraProperties.UFED_META_PREFIX + "local_path"; //$NON-NLS-1$

    public static final String META_PHONE_OWNER = ExtraProperties.UFED_META_PREFIX + "phoneOwner";
    public static final String META_FROM_OWNER = ExtraProperties.UFED_META_PREFIX + "fromOwner";
    public static final String CHILD_MSG_IDS = ExtraProperties.UFED_META_PREFIX + "msgChildIds";

    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    private Set<String> supportedApps = new HashSet<String>(Arrays.asList(WhatsAppParser.WHATSAPP,
            TelegramParser.TELEGRAM, WhatsAppParser.WHATSAPP + " Business", WhatsAppParser.WHATSAPP + " (Dual App)"));

    private static HashMap<File, UFDRInputStreamFactory> uisfMap = new HashMap<>();

    File root, rootFolder, ufdrFile;
    UFDRInputStreamFactory uisf;
    FileInputStreamFactory fisf, previewFisf;
    IItem rootItem;
    IItem decodedFolder;
    HashMap<String, IItem> pathToParent = new HashMap<>();
    boolean ignoreSupportedChats = false;
    HashMap<String, String> ufdrPathToUfedId = new HashMap<>();
    HashMap<String, String> ufedFileIdToLocalPath = new HashMap<>();    // used to replace non-existent attachment extracted path by local path
    private final List<String[]> deviceInfoData = new ArrayList<String[]>();
    private HashSet<String> addedTrackIds = new HashSet<>();

    HashMap<String, String> md5ToLocalPath = new HashMap<>();

    public UfedXmlReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    @Override
    public boolean isSupported(File datasource) {

        if (datasource.getName().toLowerCase().endsWith(".ufdr")) {
            return true;
        }

        InputStream xmlReport = lookUpXmlReportInputStream(datasource);
        IOUtil.closeQuietly(xmlReport);

        if (xmlReport != null)
            return true;

        return false;
    }

    private InputStream getXmlInputStream(File file) {
        if (file.getName().toLowerCase().endsWith(".xml")) { //$NON-NLS-1$
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) { //$NON-NLS-1$
                char[] cbuf = new char[1024];
                int off = 0, i = 0;
                while (off < cbuf.length && (i = reader.read(cbuf, off, cbuf.length - off)) != -1)
                    off += i;
                String header = new String(cbuf, 0, off);
                for (String str : HEADER_STRINGS)
                    if (!header.contains(str))
                        return null;
                return new FileInputStream(file);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (file.getName().toLowerCase().endsWith(".ufdr")) {
            try {
                ufdrFile = file;
                String xml = "report.xml";
                if (!getUISF().entryExists(xml)) {
                    xml = "Report.xml";
                }
                return getUISF().getSeekableInputStream(xml);

            } catch (Exception e) {
                throw new RuntimeException("Invalid UFDR file " + file.getAbsolutePath(), e);
            }
        }
        return null;
    }

    private boolean entryExists(String entryPath) {
        if (ufdrFile != null) {
            return getUISF().entryExists(entryPath);
        } else {
            return Files.exists(root.toPath().resolve(entryPath));
        }
    }

    private UFDRInputStreamFactory getUISF() {
        if (uisf == null) {
            synchronized (uisfMap) {
                uisf = uisfMap.get(ufdrFile);
                if (uisf == null) {
                    uisf = new UFDRInputStreamFactory(ufdrFile.toPath());
                    uisfMap.put(ufdrFile, uisf);
                }
            }
        }
        return uisf;
    }

    private FileInputStreamFactory getFISF() {
        if (fisf == null) {
            synchronized (root) {
                if (fisf == null) {
                    rootFolder = root.isDirectory() ? root : root.getParentFile();
                    fisf = new FileInputStreamFactory(rootFolder.toPath());
                }
            }
        }
        return fisf;
    }

    private InputStream lookUpXmlReportInputStream(File root) {
        if (root.isFile())
            return getXmlInputStream(root);
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().toLowerCase().endsWith(".xml")) {
                    InputStream is = getXmlInputStream(file);
                    if (is != null)
                        return is;
                }
            }
        }
        return null;
    }

    @Override
    public void read(File root) throws Exception {
        read(root, null);
    }

    @Override
    public void read(File root, Item parent) throws Exception {

        this.root = root;
        addRootItem(parent);
        addVirtualDecodedFolder();
        InputStream xmlStream = null;
        try {
            xmlStream = lookUpXmlReportInputStream(root);

            configureParsers();

            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxParser = spf.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setContentHandler(new XMLContentHandler(xmlReader));
            xmlReader.setErrorHandler(new XMLErrorHandler());
            xmlReader.parse(new InputSource(new UFEDXMLWrapper(xmlStream)));
        } finally {
            IOUtil.closeQuietly(xmlStream);
        }
    }

    private void configureParsers() {
        configureParsers(false);
    }

    private void configureParsers(boolean isIOS) {

        ParsingTaskConfig parsingConfig = ConfigurationManager.get().findObject(ParsingTaskConfig.class);

        PhoneParsingConfig.setUfdrReaderName(UfedXmlReader.class.getSimpleName());

        try {
            supportedApps = new HashSet<String>(Arrays.asList(parsingConfig.getInternalParsersList().split("\\s*,\\s*")));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse {} parameter from {}. Using default internal value: {}", ParsingTaskConfig.SOURCES_WITH_PARSERS, ParsingTaskConfig.CONF_FILE, supportedApps.toString());
        }

        if (!TelegramParser.isEnabledForUfdr()) {
            supportedApps.remove(TelegramParser.TELEGRAM);
        }

        if (parsingConfig.getPhoneParsersToUse().equalsIgnoreCase("internal")) { //$NON-NLS-1$
            UfedChatParser.setSupportedTypes(Collections.singleton(UfedChatParser.UFED_CHAT_MIME));
            ignoreSupportedChats = true;

        } else if (parsingConfig.getPhoneParsersToUse().equalsIgnoreCase("external")) { //$NON-NLS-1$
            PhoneParsingConfig.enableExternalPhoneParsersOnly();
        }
    }

    private void addRootItem(IItem parent) throws InterruptedException {

        if (listOnly)
            return;

        String evidenceName = getEvidenceName(root);
        IDataSource evidenceSource = new DataSource(root);
        evidenceSource.setName(evidenceName);

        rootItem = new Item();
        rootItem.setDataSource(evidenceSource);
        rootItem.setIdInDataSource("");
        rootItem.setHasChildren(true);
        if (root.getName().endsWith(".ufdr")) {
            rootItem.setLength(root.length());
            rootItem.setSumVolume(false);
        }
        // rootItem.setLength(0L);
        rootItem.setHash(""); //$NON-NLS-1$

        if (parent != null) {
            rootItem.setName(root.getName());
            rootItem.setParent(parent);
            rootItem.setPath(parent.getPath() + "/" + root.getName());
        } else {
            rootItem.setPath(evidenceName);
            rootItem.setRoot(true);
            rootItem.setName(evidenceName);
        }
        rootItem.setExtraAttribute(ExtraProperties.DATASOURCE_READER, this.getClass().getSimpleName());

        pathToParent.put(rootItem.getPath(), rootItem);

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(rootItem);
    }

    private void addVirtualDecodedFolder() throws InterruptedException {

        if (listOnly)
            return;

        decodedFolder = new Item();
        decodedFolder.setName("_DecodedData"); //$NON-NLS-1$
        decodedFolder.setParent(rootItem);
        decodedFolder.setIdInDataSource("");
        decodedFolder.setPath(rootItem.getPath() + "/" + decodedFolder.getName()); //$NON-NLS-1$
        decodedFolder.setIsDir(true);
        decodedFolder.setHasChildren(true);
        decodedFolder.setHash(""); //$NON-NLS-1$
        decodedFolder.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());

        pathToParent.put(decodedFolder.getPath(), decodedFolder);

        caseData.incDiscoveredEvidences(1);
        Manager.getInstance().addItemToQueue(decodedFolder);
    }

    private class XMLErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            exception.printStackTrace();
        }
    }

    private class XMLContentHandler implements ContentHandler, UfedModelListener {

        private static final String LAST_USE_PREFIX = "last known use:";

        private final StringBuilder chars = new StringBuilder();

        HashMap<String, String> extractionInfoMap = new HashMap<String, String>();

        DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"); //$NON-NLS-1$
        DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"); //$NON-NLS-1$
        DateFormat df3 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"); //$NON-NLS-1$
        DateFormat df4 = new SimpleDateFormat("dd-MMM-yy hh:mm:ss XXX"); //$NON-NLS-1$
        DateFormat df5 = new SimpleDateFormat("dd-MMM-yy hh:mm:ss  XXX"); //$NON-NLS-1$
        DateFormat df6 = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss XXX"); //$NON-NLS-1$

        DateFormat[] dfs = { df1, df2, df3, df4, df5, df6 };

        DateFormat out = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        private final DecimalFormat currencyFormat = LocalizedFormat.getDecimalInstance("#,##0.00");

        Stack<XmlNode> nodeSeq = new Stack<>();
        Stack<Item> itemSeq = new Stack<>();

        HashSet<String> elements = new HashSet<>();

        HashSet<String> ownerParties = new HashSet<>();

        List<String> msisdns = new ArrayList<>();
        TreeMap<Date, String> lastUseToMsisdn = new TreeMap<>();
        TreeMap<Date, String> iphoneSimSwitch = new TreeMap<>();

        private XMLReader xmlReader;

        private class XmlNode {
            String element;
            HashMap<String, String> atts = new HashMap<>();

            private XmlNode(String element, Attributes atts) {
                this.element = element;
                for (int i = 0; i < atts.getLength(); i++) {
                    this.atts.put(atts.getQName(i), atts.getValue(i));
                }
            }
        }

        HashSet<String> ignoreAttrs = new HashSet<>(Arrays.asList("type", //$NON-NLS-1$
                "path", //$NON-NLS-1$
                "size", //$NON-NLS-1$
                "deleted", //$NON-NLS-1$
                "deleted_state" //$NON-NLS-1$
        ));

        HashSet<String> ignoreNameAttrs = new HashSet<>(Arrays.asList("Tags", //$NON-NLS-1$
                "CreationTime", //$NON-NLS-1$
                "ModifyTime", //$NON-NLS-1$
                "AccessTime", //$NON-NLS-1$
                "CoreFileSystemFileSystemNodeCreationTime", //$NON-NLS-1$
                "CoreFileSystemFileSystemNodeModifyTime", //$NON-NLS-1$
                "CoreFileSystemFileSystemNodeLastAccessTime", //$NON-NLS-1$
                "UserMapping",
                "source"
        ));

        HashSet<String> mergeInParentNode = new HashSet<>(Arrays.asList("Party", //$NON-NLS-1$
                "PhoneNumber", //$NON-NLS-1$
                "EmailAddress", //$NON-NLS-1$
                "Coordinate", //$NON-NLS-1$
                "Organization", //$NON-NLS-1$
                "UserID", //$NON-NLS-1$
                "ContactPhoto", //$NON-NLS-1$
                "ForwardedMessageData", //$NON-NLS-1$
                "ReplyMessageData", //$NON-NLS-1$
                "StreetAddress", //$NON-NLS-1$
                "ContactEntry", //$NON-NLS-1$
                "KeyValueModel", //$NON-NLS-1$
                "MessageLabel", //$NON-NLS-1$
                "ProfilePicture", //$NON-NLS-1$
                "WebAddress", //$NON-NLS-1$
                "Reaction", //$NON-NLS-1$
                "Price",
                "QuotedMessageData"
        ));

        public XMLContentHandler(XMLReader xmlReader) {
            this.xmlReader = xmlReader;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            // TODO Auto-generated method stub

        }

        @Override
        public void startDocument() throws SAXException {
            // TODO remover timezone da exibição? obter da linha de comando?
            for (DateFormat df : dfs) {
                df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
            }
            out.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
        }

        private Date parseDate(String value) throws ParseException {
            for (DateFormat df : dfs) {
                try {
                    return df.parse(value);
                } catch (ParseException e) {
                    // ignore
                }
            }
            throw new ParseException("No dateformat configured for value " + value, 0);
        }

        @Override
        public void endDocument() throws SAXException {
            /*
             * for(String s : elements) System.out.println("element: " + s); for(String s :
             * types) System.out.println("type: " + s);
             */
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            // TODO Auto-generated method stub

        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            // TODO Auto-generated method stub

        }

        private IItem getParent(String path) throws SAXException {
            int idx = path.lastIndexOf('/');
            if (idx < 1)
                return rootItem;

            String parentPath = path.substring(0, idx);
            IItem parent = pathToParent.get(parentPath);
            if (parent != null)
                return parent;

            parent = new Item();
            parent.setName(parentPath.substring(parentPath.lastIndexOf('/') + 1));
            parent.setPath(parentPath);
            parent.setHasChildren(true);
            parent.setIsDir(true);
            // parent.setLength(0L);
            parent.setHash(""); //$NON-NLS-1$
            parent.setIdInDataSource("");
            parent.setParent(getParent(parentPath));
            parent.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());

            pathToParent.put(parentPath, parent);

            try {
                caseData.incDiscoveredEvidences(1);
                Manager.getInstance().addItemToQueue(parent);

            } catch (InterruptedException e) {
                throw new SAXException(e);
            }

            return parent;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

            chars.setLength(0);

            // if started <modelType type="Chat">, <modelType type="InstantMessage"> and so on...
            // then delegates the parsing to UfedModelHandler
            if (qName.equals("modelType") && StringUtils.equalsAny(atts.getValue("type"),
                    "Chat", "InstantMessage", "Contact", "UserAccount", "Email")) {
                xmlReader.setContentHandler(UfedModelHandler.create(xmlReader, this, this, listOnly));
                return;
            }

            XmlNode node = new XmlNode(qName, atts);
            nodeSeq.push(node);

            if (!listOnly)
                elements.add(qName);

            if (qName.equals("extractionInfo")) { //$NON-NLS-1$
                String id = atts.getValue("id"); //$NON-NLS-1$
                String name = atts.getValue("name"); //$NON-NLS-1$
                extractionInfoMap.put(id, name);

            } else if (qName.equals("file")) { //$NON-NLS-1$
                String len = atts.getValue("size"); //$NON-NLS-1$
                Long size = null;
                if (len != null)
                    size = Long.valueOf(len.trim());


                if (listOnly) {
                    caseData.incDiscoveredEvidences(1);
                    caseData.incDiscoveredVolume(size);
                    return;
                }

                Item item = new Item();
                item.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());
                item.setLength(size);

                String fs = "/" + atts.getValue("fs"); //$NON-NLS-1$ //$NON-NLS-2$
                String path = rootItem.getName() + fs + atts.getValue("path"); //$NON-NLS-1$
                item.setPath(path);

                String name = path.substring(path.lastIndexOf('/') + 1);
                // Check if the name is not too long (see issue #2107) 
                updateName(item, name);

                item.setParent(getParent(path));

                boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted")); //$NON-NLS-1$ //$NON-NLS-2$
                item.setDeleted(deleted);

                fillCommonMeta(item, atts);
                itemSeq.push(item);

            } else if (qName.equals("model")) { //$NON-NLS-1$
                XmlNode prevNode = nodeSeq.get(nodeSeq.size() - 2);
                if (prevNode.element.equals("modelType")) { //$NON-NLS-1$
                    if (listOnly) {
                        caseData.incDiscoveredEvidences(1);
                        return;
                    }

                    Item item = createModelItem(atts);
                    itemSeq.push(item);

                } else if (prevNode.element.equals("modelField") || prevNode.element.equals("multiModelField")) { //$NON-NLS-1$ //$NON-NLS-2$

                    String type = atts.getValue("type"); //$NON-NLS-1$
                    if (listOnly) {
                        if (!mergeInParentNode.contains(type))
                            caseData.incDiscoveredEvidences(1);
                        return;
                    }

                    Item item = new Item();
                    item.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());
                    IItem parent = itemSeq.get(itemSeq.size() - 1);

                    String name = type + "_" + atts.getValue("id"); //$NON-NLS-1$ //$NON-NLS-2$
                    String prevNameAtt = prevNode.atts.get("name"); //$NON-NLS-1$
                    if ("Location".equals(type) && ("FromPoint".equals(prevNameAtt) || "ToPoint".equals(prevNameAtt))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        name = prevNameAtt + "_" + name; //$NON-NLS-1$
                    item.setName(name);
                    item.setPath(parent.getPath() + "/" + name); //$NON-NLS-1$
                    item.setMediaType(MediaType.application(UFED_MIME_PREFIX + type));
                    item.setInputStreamFactory(new MetadataInputStreamFactory(item.getMetadata()));
                    item.setHash(""); //$NON-NLS-1$

                    Util.calctrackIDAndUpdateID((CaseData) caseData, parent);
                    item.setParent(parent);
                    if (!mergeInParentNode.contains(type))
                        parent.setHasChildren(true);

                    boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted_state")); //$NON-NLS-1$ //$NON-NLS-2$
                    item.setDeleted(deleted);

                    fillCommonMeta(item, atts);
                    itemSeq.push(item);

                }
            }
        }

        private Item createModelItem(Attributes atts) throws SAXException {
            Item item = new Item();
            item.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());
            String type = atts.getValue("type");
            String name = type + "_" + atts.getValue("id");
            item.setName(name);
            String path = decodedFolder.getPath() + "/" + type + "/" + name;
            item.setPath(path);
            item.setParent(getParent(path));
            item.setMediaType(MediaType.application(UFED_MIME_PREFIX + type));
            if (caseData.containsReport()) {
                // export metadata as content only if generating blind report
                item.setInputStreamFactory(new MetadataInputStreamFactory(item.getMetadata()));
            }
            item.setHash("");

            boolean deleted = "deleted".equalsIgnoreCase(atts.getValue("deleted_state"));
            item.setDeleted(deleted);

            fillCommonMeta(item, atts);
            return item;
        }

        private void fillCommonMeta(IItem item, Attributes atts) {
            if ("StreetAddress".equals(atts.getValue("type"))) //$NON-NLS-1$ //$NON-NLS-2$
                return;
            String extractionName = extractionInfoMap.get(atts.getValue("extractionId")); //$NON-NLS-1$
            item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + "extractionName", extractionName); //$NON-NLS-1$

            for (int i = 0; i < atts.getLength(); i++) {
                String attName = atts.getQName(i);
                if (!ignoreAttrs.contains(attName)) {
                    String value = atts.getValue(i);
                    if ("name".equals(attName)) {
                        attName = StringUtils.capitalize(attName);
                    }
                    item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + attName, value);
                }
            }
            if (item.getMetadata().get(UFED_ID) != null) {
                item.setIdInDataSource(item.getMetadata().get(UFED_ID));
            } else {
                // item.setIdInDataSource("");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            XmlNode currentNode = nodeSeq.pop();

            for (XmlNode node : nodeSeq) {
                if (node.element.equals("entityBookmarks")) { //$NON-NLS-1$
                    // currently there is no support for bookmarks
                    return;
                }
            }

            if (listOnly)
                return;

            String nameAttr = currentNode.atts.get("name"); //$NON-NLS-1$
            Item item = null;
            if (!itemSeq.empty())
                item = itemSeq.peek();

            XmlNode parentNode = null;
            if (!nodeSeq.empty())
                parentNode = nodeSeq.peek();

            String metadataSection = parentNode != null ? parentNode.atts.get("section") : null;
            if ("Extraction Data".equals(metadataSection) || "Device Info".equals(metadataSection)) {
                String val = chars.toString().toLowerCase();
                if (val.contains("apple") || val.contains("iphone")) {
                    configureParsers(true);
                }
            }

            if (("MSISDN".equals(nameAttr) || "LastUsedMSISDN".equals(nameAttr)) && parentNode != null
                    && "Device Info".equals(parentNode.atts.get("section"))) {
                String msisdn = chars.toString().trim();
                if (!msisdn.isEmpty()) {
                    if ("LastUsedMSISDN".equals(nameAttr))
                        msisdns.add(0, msisdn);
                    else
                        msisdns.add(msisdn);
                    caseData.putCaseObject(MSISDN_PROP + rootItem.getDataSource().getUUID(), msisdns);

                    for (String attrVal : currentNode.atts.values()) {
                        if (attrVal.toLowerCase().startsWith(LAST_USE_PREFIX)) {
                            attrVal = attrVal.substring(LAST_USE_PREFIX.length()).trim();
                            try {
                                Date date = parseDate(attrVal);
                                this.lastUseToMsisdn.put(date, msisdn);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                }

            } else if (qName.equals("item")) { //$NON-NLS-1$
                if ("Tags".equals(nameAttr) && "Configuration".equals(chars.toString())) { //$NON-NLS-1$ //$NON-NLS-2$
                    item.setCategory(chars.toString());

                } else if ("Local Path".equals(nameAttr)) { //$NON-NLS-1$
                    String normalizedPath = normalizePaths(chars.toString());
                    setContent(item, normalizedPath);

                    // Add "Local Path" to item metadata
                    item.getMetadata().add(LOCAL_PATH_META, normalizedPath);

                    // Add key to map item id to "Local Path"
                    ufedFileIdToLocalPath.put(item.getMetadata().get(UFED_ID), normalizedPath);

                    if (item.getPath().endsWith("wireless/Library/Databases/CellularUsage.db")) {
                        parseIphoneSimSwitch(item);
                    }

                } else if (!ignoreNameAttrs.contains(nameAttr) && !nameAttr.toLowerCase().startsWith("exif")) //$NON-NLS-1$
                    if (item != null && !chars.toString().trim().isEmpty())
                        item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + nameAttr, chars.toString().trim());

            } else if (qName.equals("timestamp")) { //$NON-NLS-1$
                try {
                    String value = chars.toString().trim();
                    if (!value.isEmpty()) {
                        if (nameAttr.equals("CreationTime")) //$NON-NLS-1$
                            item.setCreationDate(parseDate(value));
                        else if (nameAttr.equals("ModifyTime")) //$NON-NLS-1$
                            item.setModificationDate(parseDate(value));
                        else if (nameAttr.equals("AccessTime")) //$NON-NLS-1$
                            item.setAccessDate(parseDate(value));
                        else
                            item.getMetadata().add(ExtraProperties.UFED_META_PREFIX + nameAttr, value);
                    }

                } catch (ParseException e) {
                    throw new SAXException(e);
                }
            } else if (qName.equals("value")) { //$NON-NLS-1$
                if (parentNode.element.equals("field") || parentNode.element.equals("multiField")) { //$NON-NLS-1$ //$NON-NLS-2$
                    String parentNameAttr = parentNode.atts.get("name"); //$NON-NLS-1$
                    if (!ignoreNameAttrs.contains(parentNameAttr)) {
                        String meta = ExtraProperties.UFED_META_PREFIX + parentNameAttr;
                        String type = currentNode.atts.get("type"); //$NON-NLS-1$
                        String value = chars.toString().trim();
                        if (type.equals("TimeStamp") && !value.isEmpty()) //$NON-NLS-1$
                            try {
                                item.getMetadata().add(meta, out.format(parseDate(value)));
                            } catch (ParseException e) {
                                throw new SAXException(e);
                            }
                        else if (item != null && !value.isEmpty()) {
                            if ("Base64String".equalsIgnoreCase(currentNode.atts.get("format"))) {
                                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                                boolean isString = true;
                                for (char c : decoded.toCharArray()) {
                                    if (!(Character.isLetter(c) || c == 0x0A || c == 0x0D || c == 0x09 || c == 0x0B
                                            || (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF))) {
                                        isString = false;
                                    }
                                }
                                if (isString) {
                                    value = decoded;
                                } else {
                                    item.getMetadata().add(meta + ":format", "base64");
                                }
                            }
                            item.getMetadata().add(meta, value);
                        }
                    }
                }
            } else if (qName.equals("targetid") && parentNode.element.equals("jumptargets")) { //$NON-NLS-1$ //$NON-NLS-2$
                item.getMetadata().add(ExtraProperties.UFED_JUMP_TARGETS, chars.toString().trim());

            } else if (qName.equals("ownerid") && parentNode.element.equals("sourcemodels")) {
                item.getMetadata().add(ExtraProperties.UFED_SOURCE_MODELS, chars.toString().trim());

            } else if (qName.equals("taggedFiles")) { //$NON-NLS-1$
                md5ToLocalPath.clear();

            } else if (qName.equals("file")) { //$NON-NLS-1$
                itemSeq.pop();

                // See https://github.com/sepinf-inc/IPED/issues/2299
                String md5 = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "MD5");
                String localPath = item.getMetadata().get(LOCAL_PATH_META);
                if (StringUtils.isNotBlank(md5) && md5.length() == 32) {
                    if (item.getInputStreamFactory() != null && !md5ToLocalPath.containsKey(md5) && StringUtils.isNotBlank(localPath)) {
                        md5ToLocalPath.put(md5, localPath);
                    } else if (item.getInputStreamFactory() == null && md5ToLocalPath.containsKey(md5)) {
                        String seenPath = md5ToLocalPath.get(md5);
                        setContent(item, seenPath);
                    }
                }

                // See https://github.com/sepinf-inc/IPED/issues/1685
                boolean merged = false;
                if (!itemSeq.isEmpty()) {
                    IItem parentItem = itemSeq.peek();
                    if (parentItem.getMediaType() != null && UFED_CONTACTPHOTO_MIME.equals(parentItem.getMediaType().getSubtype())) {
                        String[] split = item.getIdInDataSource().split(UFDRInputStreamFactory.UFDR_PATH_PREFIX);
                        String exportPath = split[split.length - 1];
                        parentItem.getMetadata().set(AVATAR_PATH_META, exportPath);
                        caseData.incDiscoveredEvidences(-1);
                        merged = true;
                    }
                }

                String sceneClassifications = item.getMetadata().get(UFED_NATIVE_SCENE_CLASSIFICATION);
                if (sceneClassifications != null) {
                    item.getMetadata().remove(UFED_NATIVE_SCENE_CLASSIFICATION);
                    for (String sceneClass : sceneClassifications.split(",")) {
                        sceneClass = MetadataUtil.normalizeTerm(sceneClass);
                        if (!sceneClass.isEmpty()) {
                            item.getMetadata().add(UFED_NATIVE_SCENE_CLASSIFICATION, sceneClass);
                        }
                    }
                }

                if (!merged) {
                    setMediaResult(item);
                    String trackId = Util.getTrackID(item);
                    if (!addedTrackIds.add(trackId)) {
                        LOGGER.log(CONSOLE, "Unexpected UFDR report.xml structure, item with duplicated track id {}: {}.\nPlease report this to project"
                                + " developers sending the UFDR report.xml to add proper support for the new structure.", trackId, item.getPath());
                    }
                    try {
                        Manager.getInstance().addItemToQueue(item);
                    } catch (Exception e) {
                        throw new SAXException(e);
                    }
                }

            } else if (qName.equals("model") && ( //$NON-NLS-1$
                    parentNode.element.equals("modelType") || //$NON-NLS-1$
                    parentNode.element.equals("modelField") || //$NON-NLS-1$
                    parentNode.element.equals("multiModelField"))) { //$NON-NLS-1$

                itemSeq.pop();
                String type = currentNode.atts.get("type"); //$NON-NLS-1$
                if ("Attachment".equals(type)) { //$NON-NLS-1$
                    handleAttachment(item);
                }
                if ("Call".equals(type) || "SMS".equals(type) //$NON-NLS-4$
                        || "MMS".equals(type)) { //$NON-NLS-1$
                    String date = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "TimeStamp"); //$NON-NLS-1$
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "TimeStamp"); //$NON-NLS-1$
                    item.getMetadata().set(ExtraProperties.COMMUNICATION_DATE, date);

                    String subject = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Subject"); //$NON-NLS-1$
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Subject"); //$NON-NLS-1$
                    item.getMetadata().set(ExtraProperties.MESSAGE_SUBJECT, subject);

                    String body = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Body"); //$NON-NLS-1$
                    item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Body"); //$NON-NLS-1$
                    if (body == null) {
                        body = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Snippet"); //$NON-NLS-1$
                        item.getMetadata().remove(ExtraProperties.UFED_META_PREFIX + "Snippet"); //$NON-NLS-1$
                    }
                    item.getMetadata().set(ExtraProperties.MESSAGE_BODY, body);
                }
                if (mergeInParentNode.contains(type) && !itemSeq.empty()) {
                    IItem parentItem = itemSeq.peek();
                    if ("Party".equals(type)) { //$NON-NLS-1$
                        String role = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Role"); //$NON-NLS-1$
                        String parentNameAttr = parentNode.atts.get("name"); //$NON-NLS-1$
                        if (role == null || role.equals("General")) //$NON-NLS-1$
                            role = parentNameAttr;
                        if (role.equals("To") && (parentNameAttr.equals("Bcc") || parentNameAttr.equals("Cc"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            role = parentNameAttr;
                        if (role.equals("Parties")) //$NON-NLS-1$
                            role = "Participants"; //$NON-NLS-1$

                        String identifier = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Identifier"); //$NON-NLS-1$
                        String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name"); //$NON-NLS-1$
                        String value = name == null || name.equals(identifier) ? identifier
                                : identifier == null ? name : name + "(" + identifier + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                        if (value != null) {
                            if ("From".equalsIgnoreCase(role)) //$NON-NLS-1$
                                parentItem.getMetadata().add(ExtraProperties.COMMUNICATION_FROM, value);
                            else if ("To".equalsIgnoreCase(role)) //$NON-NLS-1$
                                parentItem.getMetadata().add(ExtraProperties.COMMUNICATION_TO, value);
                            else if ("Cc".equalsIgnoreCase(role)) //$NON-NLS-1$
                                parentItem.getMetadata().add(Message.MESSAGE_CC, value);
                            else if ("Bcc".equalsIgnoreCase(role)) //$NON-NLS-1$
                                parentItem.getMetadata().add(Message.MESSAGE_BCC, value);
                            else
                                parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + role, value);
                        }
                        boolean isOwner = Boolean
                                .valueOf(item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "IsPhoneOwner")); //$NON-NLS-1$
                        if (value != null && isOwner) { // $NON-NLS-1$
                            ownerParties.add(value);
                            if (parentItem.getMediaType().toString().contains("chat"))
                                parentItem.getMetadata().add(META_PHONE_OWNER, value);
                        }
                        if (isOwner && "From".equals(role)) //$NON-NLS-1$
                            parentItem.getMetadata().add(META_FROM_OWNER, Boolean.TRUE.toString());

                    } else if ("PhoneNumber".equals(type) || "EmailAddress".equals(type)) { //$NON-NLS-1$ //$NON-NLS-2$
                        String category = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Category"); //$NON-NLS-1$
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value"); //$NON-NLS-1$
                        if (value != null && !value.trim().isEmpty()) {
                            if (category != null)
                                value += " (" + category + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }

                    } else if ("Coordinate".equals(type)) { //$NON-NLS-1$
                        String lat = ExtraProperties.UFED_META_PREFIX + "Latitude"; //$NON-NLS-1$
                        String lon = ExtraProperties.UFED_META_PREFIX + "Longitude"; //$NON-NLS-1$
                        parentItem.getMetadata().set(lat, item.getMetadata().get(lat));
                        parentItem.getMetadata().set(lon, item.getMetadata().get(lon));
                        parentItem.getMetadata().set(ExtraProperties.UFED_COORDINATE_ID, item.getMetadata().get(ExtraProperties.UFED_ID));

                    } else if ("Organization".equals(type)) { //$NON-NLS-1$
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Name"); //$NON-NLS-1$
                        if (value != null) {
                            String position = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Position"); //$NON-NLS-1$
                            if (position != null)
                                value += " (" + position + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }
                    } else if ("UserID".equals(type)) { //$NON-NLS-1$
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value"); //$NON-NLS-1$
                        if (value != null) {
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }

                    } else if ("ContactPhoto".equals(type)) { //$NON-NLS-1$
                        String avatarPath = item.getMetadata().get(AVATAR_PATH_META);
                        if (avatarPath != null) {
                            avatarPath = normalizePaths(avatarPath);
                            parentItem.getMetadata().add(AVATAR_PATH_META, avatarPath);
                        }
                    } else if ("StreetAddress".equals(type)) { //$NON-NLS-1$
                        for (String meta : item.getMetadata().names()) {
                            if (meta.equals(ExtraProperties.UFED_META_PREFIX + "id"))
                                continue;
                            String[] vals = item.getMetadata().getValues(meta);
                            for (String val : vals)
                                parentItem.getMetadata().add(meta, val);
                        }
                    } else if ("ForwardedMessageData".equals(type) || "ReplyMessageData".equals(type)) {
                        for (String meta : item.getMetadata().names()) {
                            if (meta.equals(ExtraProperties.UFED_META_PREFIX + "id"))
                                continue;
                            String[] vals = item.getMetadata().getValues(meta);
                            for (String val : vals) {
                                parentItem.getMetadata().add(meta, val);
                            }
                        }
                    } else if ("KeyValueModel".equals(type)) {
                        String key = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Key");
                        if (key != null) {
                            String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value");
                            if (value != null) {
                                parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + key, value);
                            }
                        }
                    } else if ("MessageLabel".equals(type)) {
                        String prop = ExtraProperties.UFED_META_PREFIX + "Label";
                        String label = item.getMetadata().get(prop);
                        if (label != null) {
                            parentItem.getMetadata().add(prop, label);
                        }
                    } else if ("ContactEntry".equals(type) || "ProfilePicture".equals(type)
                            || "WebAddress".equals(type)) {
                        String value = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Value");
                        if (value != null) {
                            String category = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Category");
                            if (category != null) {
                                value += " (" + category + ")";
                            }
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + type, value);
                        }
                    } else if ("Reaction".equals(type)) {
                        XmlNode prevNode = nodeSeq.get(nodeSeq.size() - 2);
                        String prevType = prevNode.atts.get("type");
                        if ("Party".equals(prevType) && itemSeq.size() > 1) {
                            // If previous is a Party, go one node back to get the parent
                            parentItem = itemSeq.get(itemSeq.size() - 2);
                        }
                        String reaction = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ReactionType");
                        if (reaction != null) {
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + "Reaction", reaction);
                        }
                    } else if ("Price".equals(type)) {
                        String prop = ExtraProperties.UFED_META_PREFIX + "Amount";
                        String amount = item.getMetadata().get(prop);
                        if (amount != null) {
                            try {
                                double v = Double.parseDouble(amount);
                                if (v > 922337203685477.0) {
                                    // Undefined values
                                    amount = null;
                                } else {
                                    amount = currencyFormat.format(v);
                                }
                            } catch (Exception e) {
                            }
                            if (amount != null) {
                                parentItem.getMetadata().add(prop, amount);
                            }
                        }
                        prop = ExtraProperties.UFED_META_PREFIX + "Currency";
                        String currency = item.getMetadata().get(prop);
                        if (currency != null) {
                            parentItem.getMetadata().add(prop, currency);
                        }
                    } else if ("QuotedMessageData".equals(type)) {
                        String refId = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "ReferenceId");
                        if (refId != null) {
                            parentItem.getMetadata().add(ExtraProperties.UFED_META_PREFIX + "QuotedReferenceId", refId);
                        }
                    }
                } else {
                    processItem(item);
                }

            }
            if ("Device Info".equals(currentNode.atts.get("section"))) {
                if (!deviceInfoData.isEmpty()) {
                    item = new Item();
                    item.setExtraAttribute(ExtraProperties.DATASOURCE_READER, UfedXmlReader.class.getSimpleName());
                    String name = Messages.getString("UfedXmlReader.DeviceInfo");
                    item.setName(name);
                    item.setParent(rootItem);
                    item.setPath(rootItem.getPath() + "/" + name);
                    item.setIdInDataSource("");
                    item.setMediaType(MediaTypes.UFED_DEVICE_INFO);
                    createDeviceInfoPreview(item);
                    try {
                        caseData.incDiscoveredVolume(item.getLength());
                        caseData.incDiscoveredEvidences(1);
                        Manager.getInstance().addItemToQueue(item);
                    } catch (Exception e) {
                        throw new SAXException(e);
                    }
                }
            } else if (parentNode != null && parentNode.atts != null && "Device Info".equals(parentNode.atts.get("section"))) {
                String value = chars.toString().trim();
                if (!value.isEmpty()) {
                    String extractiontName = extractionInfoMap.get(currentNode.atts.get("sourceExtraction"));
                    if (extractiontName == null || extractiontName.trim().isEmpty()) {
                        extractiontName = EMPTY_EXTRACTION_STR;
                    }
                    deviceInfoData.add(new String[] { nameAttr, value, extractiontName });
                }
            }

            nameAttr = null;

        }

        private void processItem(Item item) throws SAXException {
            try {
                caseData.incDiscoveredVolume(item.getLength());
                fillMissingInfo(item);
                Manager.getInstance().addItemToQueue(item);

            } catch (Exception e) {
                throw new SAXException(e);
            }
        }

        private Property toProperty = Property.internalText(ExtraProperties.COMMUNICATION_TO);

        private void fillMissingInfo(Item item) {
            String from = item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM);
            String[] to = item.getMetadata().getValues(ExtraProperties.COMMUNICATION_TO);
            boolean fromOwner = Boolean.valueOf(item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "fromOwner"));
            if (to == null || to.length != 1) {
                if (item.getMediaType() != null
                        && MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.UFED_MESSAGE_MIME)) {
                    // we have seen ufed messages without parent chat
                    if (itemSeq.empty())
                        return;
                    IItem parentChat = itemSeq.peek();
                    List<String> toList = new ArrayList<>();
                    if (to != null && to.length > 0) {
                        toList = Arrays.asList(to);
                    } else {
                        String[] parties = parentChat.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants");
                        for (String party : parties) {
                            if ((from != null && !party.equals(from)) || (fromOwner && !ownerParties.contains(party)))
                                toList.add(party);
                        }
                    }
                    if (toList.size() == 1) {
                        item.getMetadata().set(toProperty, toList.get(0));
                    } else if (toList.size() > 1) {
                        item.getMetadata().set(toProperty, parentChat.getName());
                        item.getMetadata().set(ExtraProperties.IS_GROUP_MESSAGE, "true");
                    }

                }
            }
            if (!msisdns.isEmpty() && (MediaTypes.UFED_CALL_MIME.equals(item.getMediaType())
                    || MediaTypes.UFED_SMS_MIME.equals(item.getMediaType())
                    || MediaTypes.UFED_MMS_MIME.equals(item.getMediaType()))) {
                String likelyMsisdn = getLikelyMSISDN(item);
                likelyMsisdn += Messages.getString("UfedXmlReader.LowConfidence");
                String direction = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Type");
                String status = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Status");
                if (item.getMetadata().get(ExtraProperties.COMMUNICATION_TO) == null && likelyMsisdn != null
                        && ("Incoming".equals(direction) || "Missed".equals(direction)
                                || (!fromOwner && ("Read".equals(status) || "Unread".equals(status))))) {
                    item.getMetadata().set(ExtraProperties.COMMUNICATION_TO, likelyMsisdn);
                } else if (from == null && likelyMsisdn != null && ("Outgoing".equals(direction) || fromOwner
                        || "Sent".equals(status) || "Unsent".equals(status))) {
                    item.getMetadata().set(ExtraProperties.COMMUNICATION_FROM, likelyMsisdn);
                }
            }
        }

        private String getLikelyMSISDN(IItem item) {
            Property time = Property.externalDate(ExtraProperties.UFED_META_PREFIX + "TimeStamp");
            Date date = item.getMetadata().getDate(time);
            Entry<Date, String> entry = null;
            if (date != null)
                entry = iphoneSimSwitch.floorEntry(date);
            if (date != null && entry == null)
                entry = lastUseToMsisdn.ceilingEntry(date);
            if (entry == null)
                entry = iphoneSimSwitch.firstEntry();
            if (entry == null)
                entry = lastUseToMsisdn.lastEntry();
            if (entry != null)
                return entry.getValue();
            return msisdns.get(0);
        }

        private void parseIphoneSimSwitch(IItem item) {
            File file = null;
            try {
                file = File.createTempFile("CellularUsage", "db");
                try (InputStream is = item.getSeekableInputStream()) {
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                        Statement st = conn.createStatement()) {
                    String sql = "SELECT subscriber_mdn, last_update_time FROM subscriber_info"; //$NON-NLS-1$
                    ResultSet rs = st.executeQuery(sql);
                    while (rs.next()) {
                        String msisdn = rs.getString(1);
                        if (msisdn != null && !msisdn.isEmpty()) {
                            int time = rs.getInt(2);
                            Date date = new Date((time + 978307200l) * 1000l);
                            iphoneSimSwitch.put(date, msisdn);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse " + item.getPath(), e.toString());
                e.printStackTrace();
            } finally {
                if (file != null)
                    file.delete();
            }
        }

        private void setContent(Item item, String path) {
            item.setMediaType(null);
            item.setHash(null);
            item.setInputStreamFactory(null);
            if (path == null)
                return;
            String ufedId = item.getMetadata().get(UFED_ID);
            if (ufedId != null && !ufdrPathToUfedId.containsKey(path)) {
                ufdrPathToUfedId.put(path, ufedId);
            }
            if (ufdrFile == null) {
                if (rootFolder == null) {
                    rootFolder = root.isDirectory() ? root : root.getParentFile();
                }
                File file = new File(rootFolder, path);
                if (file.exists()) {
                    item.setInputStreamFactory(getFISF());
                    item.setIdInDataSource(path);
                    item.setLength(file.length());
                }
            } else {
                if (getUISF().entryExists(path)) {
                    item.setLength(getUISF().getEntrySize(path));
                    item.setInputStreamFactory(getUISF());
                    path = UFDRInputStreamFactory.UFDR_PATH_PREFIX + path;
                    String id = item.getIdInDataSource() != null ? item.getIdInDataSource() + "_" + path : path;
                    item.setIdInDataSource(id);
                }
            }
        }

        private void setMediaResult(Item item) {
            String ufedId = item.getMetadata().get(UFED_ID);
            if (ufedId == null) {
                return;
            }
            MediaResults mediaResults = null;
            String path = "MediaResults/" + ufedId + ".json";
            if (ufdrFile == null) {
                File file = new File(root, path);
                if (!file.exists()) {
                    return;
                }
                try (InputStream is = new FileInputStream(file)) {
                    mediaResults = readMediaResults(is);
                } catch (IOException e) {
                    LOGGER.warn("Error reading UFED mediaResult {}: {}", path, e.toString());
                }
            } else {
                try {
                    if (!getUISF().entryExists(path)) {
                        return;
                    }
                    try (InputStream is = getUISF().getSeekableInputStream(path)) {
                        mediaResults = readMediaResults(is);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Error reading UFDR mediaResult {}: {}", path, e.toString());
                }
            }
            if (mediaResults != null) {

                HashMap<String, Float> finalScorePerCat = new HashMap<>();

                // collect scores for videos
                if (mediaResults.classifications != null && !mediaResults.classifications.isEmpty()) {
                    final AtomicInteger numFrames = new AtomicInteger();
                    HashMap<String, List<Double>> scoresPerCat = new HashMap<>();
                    mediaResults.classifications.stream().forEach(classification -> {
                        numFrames.incrementAndGet();
                        if (classification.categories != null) {
                            for (Category cat : classification.categories) {
                                if (StringUtils.isNotBlank(cat.value)) {
                                    List<Double> scores = scoresPerCat.get(cat.value);
                                    if (scores == null) {
                                        scores = new ArrayList<>();
                                        scoresPerCat.put(cat.value, scores);
                                    }
                                    scores.add((double) cat.score);
                                }
                            }
                        }
                    });

                    // combine frames scores
                    scoresPerCat.entrySet().stream().forEach(entry -> {
                        // add missing frames scores, PA just stores a frame score if it is >= 50
                        // not the ideal solution...
                        while (entry.getValue().size() < numFrames.get()) {
                            entry.getValue().add(MISSING_FRAME_SCORE);
                        }
                        finalScorePerCat.put(entry.getKey(), (float) DIETask.videoScore(entry.getValue()));
                    });

                // collect scores for images
                } else if (mediaResults.categories != null){
                    mediaResults.categories.stream() //
                            .filter(cat -> StringUtils.isNotBlank(cat.value)) //
                            .forEach(cat -> finalScorePerCat.put(cat.value, cat.score));
                }

                // set scores
                finalScorePerCat.entrySet().stream()
                        .forEach(e -> item.setExtraAttribute(MEDIA_CLASSES_SCORE_PREFIX + e.getKey(), shorten(e.getValue())));

                // set high scored classes
                List<String> classes = finalScorePerCat.entrySet().stream()
                        .filter(entry -> entry.getValue() >= MEDIA_CLASSES_THRESHOLD)
                        .map(entry -> entry.getKey())
                        .collect(Collectors.toList());
                MetadataUtil.normalizeTerms(classes);
                if (!classes.isEmpty()) {
                    item.setExtraAttribute(MEDIA_CLASSES_PROPERTY, classes);
                }
            }
        }

        private MediaResults readMediaResults(InputStream is) throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectReader reader = objectMapper.readerFor(MediaResults.class);
            return reader.readValue(is);
        }

        private void updateName(IItem item, String newName) {
            // prevents error DocValuesField is too large
            int maxNameSize = 4096;
            if (newName.length() > maxNameSize) {
                newName = newName.substring(0, maxNameSize);
                item.setPath(item.getPath().substring(0, item.getPath().lastIndexOf('/') + 1) + newName);
            }
            item.setName(newName);
        }

        private String handleAttachment(Item item) {
            String name = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Filename"); //$NON-NLS-1$
            if (name != null)
                updateName(item, name);
            String extracted_path = item.getMetadata().get(ATTACH_PATH_META);
            String ufedId = null;
            if (extracted_path != null) {
                extracted_path = normalizePaths(extracted_path);
                ufedId = ufdrPathToUfedId.get(extracted_path);

                // If extracted path doesn't exist, replace non-existent extracted path by attached file's local path
                if (!entryExists(extracted_path)) {
                    // Replace extracted path by attached file's local path
                    extracted_path = ufedFileIdToLocalPath.get(item.getMetadata().get(FILE_ID_ATTR));
                }

                // if extracted_path does not reference a ufedId, use the ufedId of attached file
                if (ufedId == null && ufedFileIdToLocalPath.containsKey(item.getMetadata().get(FILE_ID_ATTR))) {
                    ufedId = item.getMetadata().get(FILE_ID_ATTR);
                }
                
            }
            setContent(item, extracted_path);
            return ufedId;
        }

        private String normalizePaths(String path) {
            path = path.replace('\\', '/');
            // workaround for ufed paths with spaces as prefixes
            String[] frags = path.split("/");
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String frag : frags) {
                sb.append(frag.trim());
                if (++i < frags.length)
                    sb.append("/");
            }
            return sb.toString();
        }

        // TODO Move this to (MakePreview)Task.
        // This is just used when parsing UFDR file generated by PA 7.x
        private void createDeviceInfoPreview(Item deviceInfo) {
            Collections.sort(deviceInfoData, new Comparator<String[]>() {
                public int compare(String[] a, String[] b) {
                    int cmp = a[0].compareToIgnoreCase(b[0]);
                    if (cmp != 0) return cmp;
                    if ((cmp = a[1].compareToIgnoreCase(b[1])) != 0) return cmp;
                    return a[2].compareToIgnoreCase(b[2]);
                }
            });
            // removes duplicate rows, depends on sorting above
            List<String[]> uniqueDeviceInfo = new ArrayList<>();
            for (int i = 0; i < deviceInfoData.size() - 1; i++) {
                String[] a = deviceInfoData.get(i);
                String[] b = deviceInfoData.get(i + 1);
                if (!a[0].equalsIgnoreCase(b[0]) || !a[1].equalsIgnoreCase(b[1])
                        || !(a[2].equalsIgnoreCase(b[2]) || a[2].equals(EMPTY_EXTRACTION_STR))) {
                    uniqueDeviceInfo.add(a);
                }
            }
            uniqueDeviceInfo.add(deviceInfoData.get(deviceInfoData.size() - 1));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos, "UTF-8"))) {
                bw.write("<!DOCTYPE html>\n"
                        + "<html>\n"
                        + "<head>\n"
                        + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                        + "<style>\n"
                        + ".t { border-collapse: collapse; font-family: Arial, sans-serif }\n"
                        + ".h { border: solid; border-width: thin; font-weight: bold; text-align: center; padding: 4px; background-color:#AAAAAA; vertical-align: middle; }\n"
                        + ".a { border: solid; border-width: thin; background-color:#EEEEEE; padding: 4px; text-align: left; vertical-align: middle; }\n"
                        + ".b { border: solid; border-width: thin; padding: 4px; text-align: left; vertical-align: middle; }\n"
                        + ".c { border: solid; border-width: thin; padding: 4px; font-size: small; text-align: left; vertical-align: middle; }\n"
                        + "</style>\n"
                        + "</head>\n"
                        + "<body>\n");
                bw.write("<table class=\"t\"><tr>\n");
                bw.write("<th class=\"h\" colspan=\"2\">" + Messages.getString("UfedXmlReader.DeviceInfo") + "</th>");
                bw.write("<th class=\"h\">" + Messages.getString("UfedXmlReader.Extraction") + "</th>");
                bw.write("</tr>\n");
                String[] prev = null;
                for (String[] s : uniqueDeviceInfo) {
                    if (!Arrays.equals(s, prev)) {
                        bw.write("<tr>");
                        bw.write("<td class=\"a\">" + SimpleHTMLEncoder.htmlEncode(s[0]) + "</td>");
                        bw.write("<td class=\"b\">" + SimpleHTMLEncoder.htmlEncode(s[1]) + "</td>");
                        bw.write("<td class=\"c\">" + SimpleHTMLEncoder.htmlEncode(s[2]) + "</td>");
                        bw.write("</tr>\n");
                        prev = s;
                    }
                }
                bw.write("</table></body></html>");
                bw.flush();
                bw.close();

                ExportFileTask.getLastInstance().insertIntoStorage(deviceInfo, baos.toByteArray(), baos.size());
                deviceInfo.setHash(null);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (listOnly)
                return;
            chars.append(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            // TODO Auto-generated method stub

        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            // TODO Auto-generated method stub

        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            // TODO Auto-generated method stub

        }

        @Override
        public void onModelStarted(BaseModel model, Attributes attr) {
            if (listOnly) {
                return;
            }

            try {
                Item item = createModelItem(attr);
                item.setSumVolume(false);
                itemSeq.push(item);
            } catch (SAXException e) {
                LOGGER.error("Error creating model Item", e);
            }
        }

        @Override
        public void onModelCompleted(BaseModel model) {

            boolean ignoreChat = model instanceof Chat
                    && ignoreSupportedChats
                    && supportedApps.contains(((Chat) model).getSource());

            if (listOnly) {
                if (!ignoreChat) {
                    caseData.incDiscoveredEvidences(1);
                }
                return;
            }

            Item item = itemSeq.pop();
            if (ignoreChat) {
                return;
            }

            item.setOpenContainer(model);

            try {
                processItem(item);
            } catch (SAXException e) {
                LOGGER.error("Error on sending model to process", e);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MediaResults {

        @JsonProperty("FileId")
        private String fileId;

        @JsonProperty("Classifications")
        private List<Classification> classifications;

        @JsonProperty("Categories")
        private List<Category> categories;
    }

    private static class Classification {

        @JsonProperty("FrameOffsetMilliseconds")
        private long frameMilliseconds;

        @JsonProperty("Categories")
        private List<Category> categories;

    }

    private static class Category {

        @JsonProperty("Value")
        private String value;

        @JsonProperty("Score")
        private float score;
    }

    @Override
    public void close() throws IOException {
        IOUtil.closeQuietly(uisf);
    }

    private static double shorten(double val) {
        return Math.round(val * 100) / 100;
    }
}
