package iped.parsers.eventtranscript;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.sqlite.detector.SQLiteContainerDetector;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ToXMLContentHandler;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;

/**
 * Parser for the EventTranscript.db file in Windows 10
 * 
 * Data collected:
 * 
 * Browser History through HJ_HistoryAddUrl and HJ_HistoryAddUrlEx events
 * 
 * Inventory Applications (Installed apps)
 * https://docs.microsoft.com/en-us/windows/privacy/basic-level-windows-diagnostic-events-and-fields-1709#microsoftwindowsinventorycoreinventoryapplicationadd
 * 
 * App Interactivity
 * https://docs.microsoft.com/en-us/windows/privacy/enhanced-diagnostic-data-windows-analytics-events-and-fields#win32ktraceloggingappinteractivitysummary
 *
 * @author Felipe Farias da Costa <felipecostasdc@gmail.com>
 */
public class EventTranscriptParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType EVENT_TRANSCRIPT = MediaType.application("x-event-transcript");

    public static final MediaType EVENT_TRANSCRIPT_HIST = MediaType.application("x-event-transcript-history");
    public static final MediaType EVENT_TRANSCRIPT_HIST_REG = MediaType.application("x-event-transcript-history-registry");

    public static final MediaType EVENT_TRANSCRIPT_INVENTORY_APP = MediaType.application("x-event-transcript-inventory-app");
    public static final MediaType EVENT_TRANSCRIPT_INVENTORY_APP_REG = MediaType.application("x-event-transcript-inventory-app-registry");

    public static final MediaType EVENT_TRANSCRIPT_APP_INTERACT = MediaType.application("x-event-transcript-app-interactivity");
    public static final MediaType EVENT_TRANSCRIPT_APP_INTERACT_REG = MediaType.application("x-event-transcript-app-interactivity-registry");

    public static final MediaType EVENT_TRANSCRIPT_DEVICE_PNP = MediaType.application("x-event-transcript-device-pnp");
    public static final MediaType EVENT_TRANSCRIPT_DEVICE_PNP_REG = MediaType.application("x-event-transcript-device-pnp-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(EVENT_TRANSCRIPT);
    
    private static final String[] HISTORY_COLUMN_NAMES = new String[] { "Page Titles", "Visit Date (UTC)", "Referrer URL", "URL", "App" };
    private static final String[] INVENTORY_APPS_COLUMN_NAMES = new String[] { "Name", "Timestamp (UTC)", "Version", "Publisher", "Root Directory Path", "Install Date" };
    private static final String[] APP_INTERACT_COLUMN_NAMES = new String[] {"App", "Timestamp (UTC)", "Type", "Window size (WxH)", "MouseInput (sec)",
    "InFocusDuration (ms)", "UserActiveDuration (ms)"};
    private static final String[] DEVICE_PNP_COLUMN_NAMES = new String[] {"Model", "Timestamp (UTC)", "InstanceId", "Provider", "Manufacturer", "Install Date", "Enumerator" };

    // extract each history entry as a subitem.
    private boolean extractEntries = true;

    // Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }


    /**
     * Parses EventTranscript.db files
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File browserHistoryFile = tmp.createTemporaryFile();
        File inventoryAppFile = tmp.createTemporaryFile();
        File appInteractivityFile = tmp.createTemporaryFile();
        File devicePnpFile = tmp.createTemporaryFile();

        if (new SQLiteContainerDetector().detect(tis, metadata) != EVENT_TRANSCRIPT) {
            sqliteParser.parse(stream, handler, metadata, context);
            return;
        }

        try (Connection connection = getConnection(tis, metadata, context)) {

            try (FileOutputStream tmpHistoryFile = new FileOutputStream(browserHistoryFile)) {
                ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-16");
                String title = "Event Transcript Browser History";

                Metadata metadataHistory = new Metadata();
                metadataHistory.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_HIST.toString());
                metadataHistory.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                metadataHistory.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                metadataHistory.set(BasicProps.HASCHILD, "true");
                metadataHistory.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (HistoryIterable historyEntriesIterable = new HistoryIterable(connection, DBQueries.HISTORY)) {
                    XHTMLContentHandler xHandler = emitHeader(historyHandler, metadataHistory, title, HISTORY_COLUMN_NAMES);

                    int i = 0;
                    for (BrowserHistoryEntry historyEntry : historyEntriesIterable) {
                        String[] rowValues = new String[] { String.join("; ", historyEntry.getPageTitles()),
                            historyEntry.getTimestampStr(), historyEntry.getReferUrl(), historyEntry.getUrl(), historyEntry.getAppName() };
                        emitEntry(xHandler, ++i, rowValues);
            
                        if (extractEntries) {
                            Metadata historySubitem = getHistoryEntryMetadata(historyEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, historySubitem, true);
                        }
                    }

                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(browserHistoryFile)) {
                        extractor.parseEmbedded(fis, handler, metadataHistory, true);
                    }
                }
            }

            try (FileOutputStream tmpInventoryAppFile = new FileOutputStream(inventoryAppFile)) {
                ToXMLContentHandler inventoryAppHandler = new ToXMLContentHandler(tmpInventoryAppFile, "UTF-16");
                String title = "Event Transcript Inventory Apps";

                Metadata inventoryAppMeta = new Metadata();
                inventoryAppMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_INVENTORY_APP.toString());
                inventoryAppMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                inventoryAppMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                inventoryAppMeta.set(BasicProps.HASCHILD, "true");
                inventoryAppMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (InventoryAppsIterable inventoryAppsIterable = new InventoryAppsIterable(connection, DBQueries.INVENTORY_APPS)) {
                    XHTMLContentHandler xHandler = emitHeader(inventoryAppHandler, inventoryAppMeta, title, INVENTORY_APPS_COLUMN_NAMES);

                    int i = 0;
                    for (InventoryAppsEntry appInvEntry : inventoryAppsIterable) {
                        String[] values = new String[] { appInvEntry.getName(), appInvEntry.getTimestampStr(), appInvEntry.getVersion(),
                            appInvEntry.getPublisher(), appInvEntry.getRootDirPath(), appInvEntry.getInstallDateStr() };
                        emitEntry(xHandler, ++i, values);

                        if (extractEntries) {
                            Metadata inventorySubitem = getInventoryEntryMetadata(appInvEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, inventorySubitem, true);
                        }
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(inventoryAppFile)) {
                        extractor.parseEmbedded(fis, handler, inventoryAppMeta, true);
                    }
                }
            }

            try (FileOutputStream tmpAppInteractFile = new FileOutputStream(appInteractivityFile)) {
                ToXMLContentHandler appInteractivityHandler = new ToXMLContentHandler(tmpAppInteractFile, "UTF-16");
                String title = "Event Transcript App Interactivity";

                Metadata appInteractMeta = new Metadata();
                appInteractMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_APP_INTERACT.toString());
                appInteractMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                appInteractMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                appInteractMeta.set(BasicProps.HASCHILD, "true");
                appInteractMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (AppInteractivityIterable appInteractivityIterable = new AppInteractivityIterable(connection, DBQueries.APP_INTERACTIVITY)) {
                    XHTMLContentHandler xHandler = emitHeader(appInteractivityHandler, appInteractMeta, title, APP_INTERACT_COLUMN_NAMES);

                    int i = 0;
                    for (AppInteractivityEntry appIntEntry : appInteractivityIterable) {
                        String[] values = new String[] { appIntEntry.getApp(), appIntEntry.getTimestampStr(), appIntEntry.getType(), appIntEntry.getWindowSize(),
                            appIntEntry.getMouseInputSec(), appIntEntry.getInFocusDuration(), appIntEntry.getUserActiveDuration() };
                        emitEntry(xHandler, ++i, values);

                        if (extractEntries) {
                            Metadata appInteractSubitem = getAppInteractEntryMetadata(appIntEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, appInteractSubitem, true);
                        }
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(inventoryAppFile)) {
                        extractor.parseEmbedded(fis, handler, appInteractMeta, true);
                    }
                }
            }
            
            try (FileOutputStream tmpDevicePnpFile = new FileOutputStream(devicePnpFile)) {
                ToXMLContentHandler devicePnpHandler = new ToXMLContentHandler(tmpDevicePnpFile, "UTF-16");
                String title = "Event Transcript Device Pnp Inventory";

                Metadata devicePnpMeta = new Metadata();
                devicePnpMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_DEVICE_PNP.toString());
                devicePnpMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                devicePnpMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                devicePnpMeta.set(BasicProps.HASCHILD, "true");
                devicePnpMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (DevicePnpIterable devicePnpIterable = new DevicePnpIterable(connection, DBQueries.DEVICE_PNP)) {
                    XHTMLContentHandler xHandler = emitHeader(devicePnpHandler, devicePnpMeta, title, DEVICE_PNP_COLUMN_NAMES);

                    int i = 0;
                    for (DevicePnpEntry deviceEntry : devicePnpIterable) {
                        String[] values = new String[] { deviceEntry.getModel(), deviceEntry.getTimestampStr(), deviceEntry.getInstanceId(),
                            deviceEntry.getProvider(), deviceEntry.getManufacturer(), deviceEntry.getInstallDateStr(), deviceEntry.getEnumerator() };
                        emitEntry(xHandler, ++i, values);

                        if (extractEntries) {
                            Metadata deviceSubitem = getDevicePnpMetadata(deviceEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, deviceSubitem, true);
                        }
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(devicePnpFile)) {
                        extractor.parseEmbedded(fis, handler, devicePnpMeta, true);
                    }
                }
            }

        } catch (Exception e) {
            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite EventTranscript parsing exception", e);

        } finally {
            tmp.close();
        }
    }

    private XHTMLContentHandler emitHeader(ContentHandler handler, Metadata metadata, String title, String[] colnames)
        throws SAXException {
        XHTMLContentHandler xHandler = null;
        
        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
        xHandler.endElement("style");
        xHandler.endElement("head");

        xHandler.startElement("h2 align=center");
        xHandler.characters(title);
        xHandler.endElement("h2");
        xHandler.startElement("br");
        xHandler.startElement("br");

        xHandler.startElement("table");
        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("");    // idx
        xHandler.endElement("th");

        for (String colname : colnames) {
            xHandler.startElement("th");
            xHandler.characters(colname);
            xHandler.endElement("th");
        }
        xHandler.endElement("tr");

        return xHandler;
    }

    private void emitEntry(XHTMLContentHandler xHandler, int idx, String[] values)
        throws SQLException, TikaException, SAXException {
        xHandler.startElement("tr");
        xHandler.startElement("td");
        xHandler.characters(String.valueOf(idx));
        xHandler.endElement("td");  

        for (String value : values) {
            xHandler.startElement("td");
            xHandler.characters(value);
            xHandler.endElement("td");    
        }
        xHandler.endElement("tr");   
    }

    // subitems

    private Metadata getHistoryEntryMetadata(BrowserHistoryEntry historyEntry, int i)
        throws ParseException {
        Metadata metadataHistoryEntry = new Metadata();

        metadataHistoryEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_HIST_REG.toString());
        metadataHistoryEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript History Entry " + i);
        metadataHistoryEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataHistoryEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));
        metadataHistoryEntry.set(BasicProps.LENGTH, "");

        metadataHistoryEntry.add(TikaCoreProperties.TITLE, String.join("; ", historyEntry.getPageTitles()));
        metadataHistoryEntry.set(ExtraProperties.ACCESSED, historyEntry.getTimestamp());
        metadataHistoryEntry.set(ExtraProperties.VISIT_DATE, historyEntry.getTimestamp());
        metadataHistoryEntry.add(ExtraProperties.URL, historyEntry.getUrl());
        metadataHistoryEntry.add("referUrl", historyEntry.getReferUrl());
        metadataHistoryEntry.add("eventNames", String.join("; ", historyEntry.getEventNames()));
        metadataHistoryEntry.add("originalPayload", historyEntry.getJSONPayload());

        return metadataHistoryEntry;
    }

    private Metadata getInventoryEntryMetadata(InventoryAppsEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_INVENTORY_APP_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Inventory App Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.set(TikaCoreProperties.CREATED, entry.getTimestamp());
        metadataEntry.add(TikaCoreProperties.TITLE, entry.getName());
        metadataEntry.set(ExtraProperties.DOWNLOAD_DATE, entry.getInstallDate());
        metadataEntry.set(TikaCoreProperties.SOURCE_PATH, entry.getRootDirPath());
        metadataEntry.set("version", entry.getVersion());
        metadataEntry.set("publisher", entry.getPublisher());
        metadataEntry.set("eventName", entry.getEventName());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private Metadata getAppInteractEntryMetadata(AppInteractivityEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_APP_INTERACT_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript App Interaction " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.set(TikaCoreProperties.CREATED, entry.getTimestamp());
        metadataEntry.add(TikaCoreProperties.TITLE, entry.getApp());
        metadataEntry.add("aggregationDuration", entry.getAggregationDuration());
        metadataEntry.add("userActiveDuration", entry.getUserActiveDuration());
        metadataEntry.add("userOrDisplayActiveDuration", entry.getUserOrDisplayActiveDuration());
        metadataEntry.add("programID", entry.getProgramID());
        metadataEntry.add("userID", entry.getUserID());
        metadataEntry.add("userSID", entry.getUserSID());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private Metadata getDevicePnpMetadata(DevicePnpEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_DEVICE_PNP_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Device Pnp Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.set(TikaCoreProperties.CREATED, entry.getTimestamp());
        metadataEntry.add(TikaCoreProperties.TITLE, entry.getModel());
        metadataEntry.add("instanceId", entry.getInstanceId());
        metadataEntry.set(ExtraProperties.DOWNLOAD_DATE, entry.getInstallDate());
        metadataEntry.add("firstInstallDate", entry.getFirstInstallDateStr());
        metadataEntry.set("manufacturer", entry.getManufacturer());
        metadataEntry.set("provider", entry.getProvider());
        metadataEntry.set("eventName", entry.getEventName());
        metadataEntry.set("enumerator", entry.getEnumerator());
        metadataEntry.add("userSID", entry.getUserSID());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    // iterators

    private class HistoryIterable implements Iterable<BrowserHistoryEntry>, Closeable {
        ResultSet rs;
        Statement statement;

        public HistoryIterable(Connection connection, String query) throws SQLException {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
        }

        @Override
        public Iterator<BrowserHistoryEntry> iterator() {
            return new Iterator<BrowserHistoryEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public BrowserHistoryEntry next() {
                    BrowserHistoryEntry historyEntry = new BrowserHistoryEntry();
                    try {
                        historyEntry.setUserSID(rs.getString("UserSID"));
                        historyEntry.setCorrelationGuid(rs.getString("CorrelationGuid"));
                        historyEntry.setTimestamp(rs.getString("Timestamp"));
                        historyEntry.setTagNames(rs.getString("TagNames").split(";"));
                        historyEntry.setEventNames(rs.getString("EventNames").split(";"));
                        historyEntry.setReferUrl(rs.getString("ReferURL"));
                        historyEntry.setUrl(rs.getString("URL"));
                        String pageTitlesStr = rs.getString("PageTitles");
                        historyEntry.setPageTitles(pageTitlesStr != null ? pageTitlesStr.split(";") : new String[] {""});
                        historyEntry.setAppName(rs.getString("App"));
                        historyEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return historyEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                rs.close();
                statement.close();
            } catch (SQLException e) {
                // swallow
            }            
        }
    }

    private class InventoryAppsIterable implements Iterable<InventoryAppsEntry>, Closeable {
        ResultSet rs;
        Statement statement;

        public InventoryAppsIterable(Connection connection, String query) throws SQLException {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
        }

        @Override
        public Iterator<InventoryAppsEntry> iterator() {
            return new Iterator<InventoryAppsEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public InventoryAppsEntry next() {
                    InventoryAppsEntry inventoryAppsEntry = new InventoryAppsEntry();
                    try {
                        inventoryAppsEntry.setTimestamp(rs.getString("Timestamp"));
                        inventoryAppsEntry.setTagName(rs.getString("TagName"));
                        inventoryAppsEntry.setEventName(rs.getString("EventName"));
                        inventoryAppsEntry.setType(rs.getString("Type"));
                        inventoryAppsEntry.setName(rs.getString("Name"));
                        inventoryAppsEntry.setPackageFullName(rs.getString("PackageFullName"));
                        inventoryAppsEntry.setVersion(rs.getString("Version"));
                        inventoryAppsEntry.setPublisher(rs.getString("Publisher"));
                        inventoryAppsEntry.setRootDirPath(rs.getString("RootDirPath"));
                        inventoryAppsEntry.setHidden(rs.getString("HiddenArp"));
                        inventoryAppsEntry.setInstallDate(rs.getString("InstallDate"));
                        inventoryAppsEntry.setSource(rs.getString("Source"));
                        inventoryAppsEntry.setOSVersionAtInstallTime(rs.getString("OSVersionAtInstallTime"));
                        inventoryAppsEntry.setUserSID(rs.getString("UserSID"));
                        inventoryAppsEntry.setUserID(rs.getString("UserID"));
                        inventoryAppsEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return inventoryAppsEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                rs.close();
                statement.close();
            } catch (SQLException e) {
                // swallow
            }            
        }
    }

    private class AppInteractivityIterable implements Iterable<AppInteractivityEntry>, Closeable {
        ResultSet rs;
        Statement statement;

        public AppInteractivityIterable(Connection connection, String query) throws SQLException {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
        }

        @Override
        public Iterator<AppInteractivityEntry> iterator() {
            return new Iterator<AppInteractivityEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public AppInteractivityEntry next() {
                    AppInteractivityEntry appInteractivityEntry = new AppInteractivityEntry();
                    try {
                        appInteractivityEntry.setApp(rs.getString("AppID"));
                        appInteractivityEntry.setTimestamp(rs.getString("Timestamp"));
                        appInteractivityEntry.setTagName(rs.getString("TagName"));
                        appInteractivityEntry.setEventName(rs.getString("EventName"));
                        appInteractivityEntry.setType(rs.getString("Type"));
                        appInteractivityEntry.setAggregationStartTime(rs.getString("AggregationStartTime"));
                        appInteractivityEntry.setAggregationDuration(rs.getString("AggregationDurationMS"));
                        appInteractivityEntry.setWindowSize(rs.getString("WindowSize(WxH)"));
                        appInteractivityEntry.setMouseInputSec(rs.getString("MouseInputSec"));
                        appInteractivityEntry.setInFocusDuration(rs.getString("InFocusDurationMS"));
                        appInteractivityEntry.setUserActiveDuration(rs.getString("UserActiveDurationMS"));
                        appInteractivityEntry.setUserOrDisplayActiveDuration(rs.getString("UserOrDisplayActiveDurationMS"));
                        appInteractivityEntry.setFocusLostCount(rs.getString("FocusLostCount"));
                        appInteractivityEntry.setProgramID(rs.getString("ProgramID"));
                        appInteractivityEntry.setUserSID(rs.getString("UserSID"));
                        appInteractivityEntry.setUserID(rs.getString("UserID"));
                        appInteractivityEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return appInteractivityEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                rs.close();
                statement.close();
            } catch (SQLException e) {
                // swallow
            }            
        }
    }
    

    private class DevicePnpIterable implements Iterable<DevicePnpEntry>, Closeable {
        ResultSet rs;
        Statement statement;

        public DevicePnpIterable(Connection connection, String query) throws SQLException {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
        }

        @Override
        public Iterator<DevicePnpEntry> iterator() {
            return new Iterator<DevicePnpEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public DevicePnpEntry next() {
                    DevicePnpEntry devicePnpEntry = new DevicePnpEntry();
                    try {
                        devicePnpEntry.setModel(rs.getString("Model"));
                        devicePnpEntry.setInstanceId(rs.getString("InstanceId"));
                        devicePnpEntry.setTimestamp(rs.getString("Timestamp"));
                        devicePnpEntry.setTagName(rs.getString("TagName"));
                        devicePnpEntry.setEventName(rs.getString("EventName"));
                        devicePnpEntry.setProvider(rs.getString("Provider"));
                        devicePnpEntry.setManufacturer(rs.getString("Manufacturer"));
                        devicePnpEntry.setEnumerator(rs.getString("Enumerator"));
                        devicePnpEntry.setInstallDate(rs.getString("InstallDate"));
                        devicePnpEntry.setFirstInstallDate(rs.getString("FirstInstallDate"));
                        devicePnpEntry.setUserSID(rs.getString("UserSID"));
                        devicePnpEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return devicePnpEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                rs.close();
                statement.close();
            } catch (SQLException e) {
                // swallow
            }            
        }
    }
}