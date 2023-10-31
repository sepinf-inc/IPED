package iped.parsers.eventtranscript;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
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
import iped.parsers.util.IgnoreContentHandler;
import iped.parsers.util.MetadataUtil;
import iped.parsers.util.ToXMLContentHandler;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;

/**
 * Parser for the EventTranscript.db file in Windows 10
 * 
 * Data collected:
 * 
 * Browser History through HJ_HistoryAddUrl and HJ_HistoryAddUrlEx events
 * 
 * Inventory Applications (Installed apps state)
 * https://docs.microsoft.com/en-us/windows/privacy/basic-level-windows-diagnostic-events-and-fields-1709#microsoftwindowsinventorycoreinventoryapplicationadd
 *
 * Devices (PNP and Container)
 * https://docs.microsoft.com/en-us/windows/privacy/basic-level-windows-diagnostic-events-and-fields-1709#microsoftwindowsinventorycoreinventorydevicepnpadd
 * https://docs.microsoft.com/en-us/windows/privacy/basic-level-windows-diagnostic-events-and-fields-1709#microsoftwindowsinventorycoreinventorydevicecontaineradd
 * 
 * App Interactivity
 * https://docs.microsoft.com/en-us/windows/privacy/enhanced-diagnostic-data-windows-analytics-events-and-fields#win32ktraceloggingappinteractivitysummary
 *
 * Census
 * https://docs.microsoft.com/en-us/windows/privacy/basic-level-windows-diagnostic-events-and-fields-1809#census-events
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

    public static final MediaType EVENT_TRANSCRIPT_DEVICES = MediaType.application("x-event-transcript-devices");
    public static final MediaType EVENT_TRANSCRIPT_DEVICES_REG = MediaType.application("x-event-transcript-devices-registry");

    public static final MediaType EVENT_TRANSCRIPT_CENSUS = MediaType.application("x-event-transcript-census");
    public static final MediaType EVENT_TRANSCRIPT_CENSUS_REG = MediaType.application("x-event-transcript-census-registry");

    public static final MediaType EVENT_TRANSCRIPT_NETWORKING = MediaType.application("x-event-transcript-networking");
    public static final MediaType EVENT_TRANSCRIPT_NETWORKING_REG = MediaType.application("x-event-transcript-networking-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(EVENT_TRANSCRIPT);

    private static final String[] HISTORY_COLUMN_NAMES = new String[] { "Page Titles", "Visit Date (UTC)", "Local Time", "Timezone", "Referrer URL", "URL", "App" };
    private static final String[] INVENTORY_APPS_COLUMN_NAMES = new String[] { "Name", "Timestamp (UTC)", "Local Time", "Timezone", "Version", "Publisher", "Root Directory Path", "Install Date" };
    private static final String[] APP_INTERACT_COLUMN_NAMES = new String[] { "App", "Timestamp (UTC)", "Local Time", "Timezone", "Type", "Window size (WxH)", "MouseInput (sec)", "InFocusDuration (ms)", "UserActiveDuration (ms)" };
    private static final String[] DEVICES_COLUMN_NAMES = new String[] { "Model", "Timestamp (UTC)", "Local Time", "Timezone", "InstanceId", "Provider", "Manufacturer", "Install Date", "Enumerator" };
    private static final String[] CENSUS_COLUMN_NAMES = new String[] { "Timestamp (UTC)", "Local Time", "Timezone", "Event", "State \\ Settings" };
    private static final String[] NETWORK_COLUMN_NAMES = new String[] { "Timestamp (UTC)", "Local Time", "Timezone", "Event", "Event Source", "Event Reason", "JSON Data" };

    // extract each history entry as a subitem.
    private boolean extractEntries = true;

    // Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();

    static {
        MetadataUtil.setMetadataType("aggregationDuration", Long.class);
        MetadataUtil.setMetadataType("userActiveDuration", Long.class);
        MetadataUtil.setMetadataType("userOrDisplayActiveDuration", Long.class);
        MetadataUtil.setMetadataType("inFocusDurationMS", Long.class);
        MetadataUtil.setMetadataType("installDate", Date.class);
        MetadataUtil.setMetadataType("firstInstallDate", Date.class);
        MetadataUtil.setMetadataType("TranscriptDBEvent", Date.class);
    }

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
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File browserHistoryFile = tmp.createTemporaryFile();
        File inventoryAppsFile = tmp.createTemporaryFile();
        File appInteractivityFile = tmp.createTemporaryFile();
        File devicesFile = tmp.createTemporaryFile();
        File censusFile = tmp.createTemporaryFile();
        File networkingFile = tmp.createTemporaryFile();

        if (new SQLiteContainerDetector().detect(tis, metadata) != EVENT_TRANSCRIPT) {
            sqliteParser.parse(stream, handler, metadata, context);
            return;
        }

        try (Connection connection = getConnection(tis, metadata, context)) {

            Metadata metadataHistory = new Metadata();
            try (BufferedOutputStream tmpHistoryFile = new BufferedOutputStream(new FileOutputStream(browserHistoryFile))) {
                ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8");
                String title = "Event Transcript Browser History";
                metadataHistory.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_HIST.toString());
                metadataHistory.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                metadataHistory.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                metadataHistory.set(BasicProps.HASCHILD, "true");
                metadataHistory.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (HistoryIterator historyEntriesIterator = new HistoryIterator(connection, DBQueries.HISTORY)) {
                    XHTMLContentHandler xHandler = emitHeader(historyHandler, metadataHistory, title, HISTORY_COLUMN_NAMES);

                    int i = 0;
                    while (historyEntriesIterator.hasNext()) {
                        BrowserHistoryEntry historyEntry = historyEntriesIterator.next();
                        String[] rowValues = new String[] { String.join("; ", historyEntry.getPageTitles()), historyEntry.getTimestampStr(), historyEntry.getLocalTime(), historyEntry.getTimezone(), historyEntry.getReferUrl(),
                                historyEntry.getUrl(), historyEntry.getAppName() };
                        emitEntry(xHandler, ++i, rowValues);
                    }

                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(browserHistoryFile))) {
                extractor.parseEmbedded(fis, handler, metadataHistory, true);
            }
            if (extractEntries) {
                try (HistoryIterator historyEntriesIterator = new HistoryIterator(connection, DBQueries.HISTORY)) {
                    int i = 0;
                    while (historyEntriesIterator.hasNext()) {
                        BrowserHistoryEntry historyEntry = historyEntriesIterator.next();
                        Metadata historySubitem = getHistoryEntryMetadata(historyEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), historySubitem, true);
                    }
                }
            }

            Metadata inventoryAppsMeta = new Metadata();
            try (BufferedOutputStream tmpInventoryAppFile = new BufferedOutputStream(new FileOutputStream(inventoryAppsFile))) {
                ToXMLContentHandler inventoryAppHandler = new ToXMLContentHandler(tmpInventoryAppFile, "UTF-8");
                String title = "Event Transcript Apps Inventory";
                inventoryAppsMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_INVENTORY_APP.toString());
                inventoryAppsMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                inventoryAppsMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                inventoryAppsMeta.set(BasicProps.HASCHILD, "true");
                inventoryAppsMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (InventoryAppsIterator inventoryAppsIterator = new InventoryAppsIterator(connection, DBQueries.INVENTORY_APPS)) {
                    XHTMLContentHandler xHandler = emitHeader(inventoryAppHandler, inventoryAppsMeta, title, INVENTORY_APPS_COLUMN_NAMES);

                    int i = 0;
                    while (inventoryAppsIterator.hasNext()) {
                        InventoryAppsEntry appInvEntry = inventoryAppsIterator.next();
                        String[] values = new String[] { appInvEntry.getName(), appInvEntry.getTimestampStr(), appInvEntry.getLocalTime(), appInvEntry.getTimezone(), appInvEntry.getVersion(), appInvEntry.getPublisher(),
                                appInvEntry.getRootDirPath(), appInvEntry.getInstallDateStr() };
                        emitEntry(xHandler, ++i, values);
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(inventoryAppsFile))) {
                extractor.parseEmbedded(fis, handler, inventoryAppsMeta, true);
            }
            if (extractEntries) {
                try (InventoryAppsIterator inventoryAppsIterator = new InventoryAppsIterator(connection, DBQueries.INVENTORY_APPS)) {
                    int i = 0;
                    while (inventoryAppsIterator.hasNext()) {
                        InventoryAppsEntry appInvEntry = inventoryAppsIterator.next();
                        Metadata inventorySubitem = getInventoryEntryMetadata(appInvEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), inventorySubitem, true);
                    }
                }
            }

            Metadata appInteractMeta = new Metadata();
            try (BufferedOutputStream tmpAppInteractFile = new BufferedOutputStream(new FileOutputStream(appInteractivityFile))) {
                ToXMLContentHandler appInteractivityHandler = new ToXMLContentHandler(tmpAppInteractFile, "UTF-8");
                String title = "Event Transcript App Interactivity";
                appInteractMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_APP_INTERACT.toString());
                appInteractMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                appInteractMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(2));
                appInteractMeta.set(BasicProps.HASCHILD, "true");
                appInteractMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (AppInteractivityIterator appInteractivityIterator = new AppInteractivityIterator(connection, DBQueries.APP_INTERACTIVITY)) {
                    XHTMLContentHandler xHandler = emitHeader(appInteractivityHandler, appInteractMeta, title, APP_INTERACT_COLUMN_NAMES);

                    int i = 0;
                    while (appInteractivityIterator.hasNext()) {
                        AppInteractivityEntry appIntEntry = appInteractivityIterator.next();
                        String[] values = new String[] { appIntEntry.getApp(), appIntEntry.getTimestampStr(), appIntEntry.getLocalTime(), appIntEntry.getTimezone(), appIntEntry.getType(), appIntEntry.getWindowSize(),
                                appIntEntry.getMouseInputSec(), appIntEntry.getInFocusDuration(), appIntEntry.getUserActiveDuration() };
                        emitEntry(xHandler, ++i, values);
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(appInteractivityFile))) {
                extractor.parseEmbedded(fis, handler, appInteractMeta, true);
            }
            if (extractEntries) {
                try (AppInteractivityIterator appInteractivityIterator = new AppInteractivityIterator(connection, DBQueries.APP_INTERACTIVITY)) {
                    int i = 0;
                    while (appInteractivityIterator.hasNext()) {
                        AppInteractivityEntry appIntEntry = appInteractivityIterator.next();
                        Metadata appInteractSubitem = getAppInteractEntryMetadata(appIntEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), appInteractSubitem, true);
                    }
                }
            }

            Metadata devicesMeta = new Metadata();
            try (BufferedOutputStream tmpDevicesFile = new BufferedOutputStream(new FileOutputStream(devicesFile))) {
                ToXMLContentHandler devicesHandler = new ToXMLContentHandler(tmpDevicesFile, "UTF-8");
                String title = "Event Transcript Devices Inventory";
                devicesMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_DEVICES.toString());
                devicesMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                devicesMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(3));
                devicesMeta.set(BasicProps.HASCHILD, "true");
                devicesMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (DevicesIterator devicesIterator = new DevicesIterator(connection, DBQueries.DEVICES)) {
                    XHTMLContentHandler xHandler = emitHeader(devicesHandler, devicesMeta, title, DEVICES_COLUMN_NAMES);

                    int i = 0;
                    while (devicesIterator.hasNext()) {
                        DevicesEntry deviceEntry = devicesIterator.next();
                        String[] values = new String[] { deviceEntry.getModel(), deviceEntry.getTimestampStr(), deviceEntry.getLocalTime(), deviceEntry.getTimezone(), deviceEntry.getInstanceId(), deviceEntry.getProvider(),
                                deviceEntry.getManufacturer(), deviceEntry.getInstallDateStr(), deviceEntry.getEnumerator() };
                        emitEntry(xHandler, ++i, values);
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(devicesFile))) {
                extractor.parseEmbedded(fis, handler, devicesMeta, true);
            }
            if (extractEntries) {
                try (DevicesIterator devicesIterator = new DevicesIterator(connection, DBQueries.DEVICES)) {
                    int i = 0;
                    while (devicesIterator.hasNext()) {
                        DevicesEntry deviceEntry = devicesIterator.next();
                        Metadata deviceSubitem = getDevicesMetadata(deviceEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), deviceSubitem, true);
                    }
                }
            }

            Metadata censusMeta = new Metadata();
            try (BufferedOutputStream tmpCensusFile = new BufferedOutputStream(new FileOutputStream(censusFile))) {
                ToXMLContentHandler censusHandler = new ToXMLContentHandler(tmpCensusFile, "UTF-8");
                String title = "Event Transcript Census";
                censusMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_CENSUS.toString());
                censusMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                censusMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(4));
                censusMeta.set(BasicProps.HASCHILD, "true");
                censusMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (CensusIterator censusIterator = new CensusIterator(connection, DBQueries.CENSUS)) {
                    XHTMLContentHandler xHandler = emitHeader(censusHandler, censusMeta, title, CENSUS_COLUMN_NAMES);

                    int i = 0;
                    while (censusIterator.hasNext()) {
                        CensusEntry censusEntry = censusIterator.next();
                        String[] values = new String[] { censusEntry.getTimestampStr(), censusEntry.getLocalTime(), censusEntry.getTimezone(), censusEntry.getEventName(), censusEntry.getDataJSON() };
                        emitEntry(xHandler, ++i, values);
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(censusFile))) {
                extractor.parseEmbedded(fis, handler, censusMeta, true);
            }
            if (extractEntries) {
                try (CensusIterator censusIterator = new CensusIterator(connection, DBQueries.CENSUS)) {
                    int i = 0;
                    while (censusIterator.hasNext()) {
                        CensusEntry censusEntry = censusIterator.next();
                        Metadata censusSubitem = getCensusEntryMetadata(censusEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), censusSubitem, true);
                    }
                }
            }

            Metadata networkingMeta = new Metadata();
            try (BufferedOutputStream tmpNetworkingFile = new BufferedOutputStream(new FileOutputStream(networkingFile))) {
                ToXMLContentHandler networkingHandler = new ToXMLContentHandler(tmpNetworkingFile, "UTF-8");
                String title = "Event Transcript Networking";
                networkingMeta.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_NETWORKING.toString());
                networkingMeta.add(TikaCoreProperties.RESOURCE_NAME_KEY, title);
                networkingMeta.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(5));
                networkingMeta.set(BasicProps.HASCHILD, "true");
                networkingMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (NetworkingIterator networkingIterator = new NetworkingIterator(connection, DBQueries.NETWORKING)) {
                    XHTMLContentHandler xHandler = emitHeader(networkingHandler, networkingMeta, title, NETWORK_COLUMN_NAMES);

                    int i = 0;
                    while (networkingIterator.hasNext()) {
                        NetworkingEntry netEntry = networkingIterator.next();
                        String[] values = new String[] { netEntry.getUTCTimestampStr(), netEntry.getLocalTime(), netEntry.getTimezone(), netEntry.getEventName(), netEntry.getEventSource(), netEntry.getEventReason(),
                                netEntry.getDataJSON() };
                        emitEntry(xHandler, ++i, values);
                    }
                    xHandler.endElement("table");
                    xHandler.endDocument();
                }
            }
            try (InputStream fis = new BufferedInputStream(new FileInputStream(networkingFile))) {
                extractor.parseEmbedded(fis, handler, networkingMeta, true);
            }
            if (extractEntries) {
                try (NetworkingIterator networkingIterator = new NetworkingIterator(connection, DBQueries.NETWORKING)) {
                    int i = 0;
                    while (networkingIterator.hasNext()) {
                        NetworkingEntry netEntry = networkingIterator.next();
                        Metadata networkingSubitem = getNetworkingMetadata(netEntry, ++i);
                        extractor.parseEmbedded(new EmptyInputStream(), new IgnoreContentHandler(), networkingSubitem, true);
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

    private XHTMLContentHandler emitHeader(ContentHandler handler, Metadata metadata, String title, String[] colnames) throws SAXException {
        XHTMLContentHandler xHandler = null;

        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;}" + " table, td, th {border: 1px solid black; max-width:500px; word-wrap: break-word;}" + " th {white-space: nowrap; padding: 5px;}");
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
        xHandler.characters(""); // idx
        xHandler.endElement("th");

        for (String colname : colnames) {
            xHandler.startElement("th");
            xHandler.characters(colname);
            xHandler.endElement("th");
        }
        xHandler.endElement("tr");

        return xHandler;
    }

    private void emitEntry(XHTMLContentHandler xHandler, int idx, String[] values) throws SQLException, TikaException, SAXException {
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

    private Metadata getHistoryEntryMetadata(BrowserHistoryEntry historyEntry, int i) throws ParseException {
        Metadata metadataHistoryEntry = new Metadata();

        metadataHistoryEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_HIST_REG.toString());
        metadataHistoryEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript History Entry " + i);
        metadataHistoryEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataHistoryEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));
        metadataHistoryEntry.set(BasicProps.LENGTH, "");

        metadataHistoryEntry.add(TikaCoreProperties.TITLE, String.join("; ", historyEntry.getPageTitles()));
        metadataHistoryEntry.set(ExtraProperties.ACCESSED, historyEntry.getTimestamp());
        metadataHistoryEntry.set(ExtraProperties.VISIT_DATE, historyEntry.getTimestamp());
        metadataHistoryEntry.add(ExtraProperties.URL, historyEntry.getUrl());
        metadataHistoryEntry.add("referUrl", historyEntry.getReferUrl());
        if (historyEntry.getEventNames() != null) {
            for (String eventName : historyEntry.getEventNames()) {
                metadataHistoryEntry.add("eventName", eventName);
            }
        }
        metadataHistoryEntry.add("originalPayload", historyEntry.getJSONPayload());

        return metadataHistoryEntry;
    }

    private Metadata getInventoryEntryMetadata(InventoryAppsEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_INVENTORY_APP_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Inventory App Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.add(TikaCoreProperties.TITLE, entry.getName());
        metadataEntry.set(TikaCoreProperties.SOURCE_PATH, entry.getRootDirPath());
        metadataEntry.set(getEventName(entry.getEventName()), format(entry.getTimestamp()));
        metadataEntry.set("installDate", format(entry.getInstallDate()));
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
        metadataEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(2));
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.add(TikaCoreProperties.TITLE, entry.getApp());
        metadataEntry.set(getEventName(entry.getEventName()), format(entry.getTimestamp()));
        metadataEntry.set("eventName", entry.getEventName());
        metadataEntry.add("aggregationDuration", entry.getAggregationDuration());
        metadataEntry.add("userActiveDuration", entry.getUserActiveDuration());
        metadataEntry.add("userOrDisplayActiveDuration", entry.getUserOrDisplayActiveDuration());
        metadataEntry.add("inFocusDurationMS", entry.getInFocusDuration());
        metadataEntry.add("programID", entry.getProgramID());
        metadataEntry.add("userID", entry.getUserID());
        metadataEntry.add("userSID", entry.getUserSID());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private Metadata getDevicesMetadata(DevicesEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_DEVICES_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Devices Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(3));
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.add(TikaCoreProperties.TITLE, entry.getModel());
        metadataEntry.set(getEventName(entry.getEventName()), format(entry.getTimestamp()));
        metadataEntry.add("instanceId", entry.getInstanceId());
        metadataEntry.set("installDate", format(entry.getInstallDate()));
        metadataEntry.add("firstInstallDate", format(entry.getFirstInstallDate()));
        metadataEntry.set("manufacturer", entry.getManufacturer());
        metadataEntry.set("provider", entry.getProvider());
        metadataEntry.set("eventName", entry.getEventName());
        metadataEntry.set("enumerator", entry.getEnumerator());
        metadataEntry.add("userSID", entry.getUserSID());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private Metadata getCensusEntryMetadata(CensusEntry entry, int i) throws ParseException {
        Metadata metadataCensusEntry = new Metadata();

        metadataCensusEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_CENSUS_REG.toString());
        metadataCensusEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Census Entry " + i);
        metadataCensusEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataCensusEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(4));
        metadataCensusEntry.set(BasicProps.LENGTH, "");

        metadataCensusEntry.add(getEventName(entry.getEventName()), format(entry.getTimestamp()));
        metadataCensusEntry.add("eventName", entry.getEventName());
        metadataCensusEntry.add("jsonData", entry.getDataJSON());
        metadataCensusEntry.add("originalPayload", entry.getJSONPayload());

        return metadataCensusEntry;
    }

    private Metadata getNetworkingMetadata(NetworkingEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_NETWORKING_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Networking Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(5));
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.set(getEventName(entry.getEventName()), format(entry.getUTCTimestamp()));
        metadataEntry.set("seq", entry.getSeq());
        metadataEntry.add("eventName", entry.getEventName());
        metadataEntry.add("eventSource", entry.getEventSource());
        metadataEntry.add("eventReason", entry.getEventReason());
        metadataEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private static String getEventName(String eventName) {
        if (eventName != null && !eventName.trim().isEmpty()) {
            eventName = eventName.trim() + "Event";
            MetadataUtil.setMetadataType(eventName, Date.class);
            return eventName;
        }
        return "TranscriptDBEvent";
    }

    private static String format(Date date) {
        if (date != null) {
            return DateUtil.dateToString(date);
        }
        return null;
    }

    // iterators

    private class HistoryIterator extends DBIterator<BrowserHistoryEntry> {

        public HistoryIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public BrowserHistoryEntry next() {
            BrowserHistoryEntry historyEntry = new BrowserHistoryEntry();
            try {
                historyEntry.setUserSID(rs.getString("UserSID"));
                historyEntry.setCorrelationGuid(rs.getString("CorrelationGuid"));
                historyEntry.setTimestamp(rs.getString("UTCTimestamp"));
                historyEntry.setLocalTime(rs.getString("LocalTimestamp"));
                historyEntry.setTimezone(rs.getString("Timezone"));
                historyEntry.setTagNames(rs.getString("TagNames").split(";"));
                historyEntry.setEventNames(rs.getString("EventNames").split(";"));
                historyEntry.setReferUrl(rs.getString("ReferURL"));
                historyEntry.setUrl(rs.getString("URL"));
                String pageTitlesStr = rs.getString("PageTitles");
                historyEntry.setPageTitles(pageTitlesStr != null ? pageTitlesStr.split(";") : new String[] { "" });
                historyEntry.setAppName(rs.getString("App"));
                historyEntry.setJSONPayload(rs.getString("JSONPayload"));
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return historyEntry;
        }
    }

    private class InventoryAppsIterator extends DBIterator<InventoryAppsEntry> {
        public InventoryAppsIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public InventoryAppsEntry next() {
            InventoryAppsEntry inventoryAppsEntry = new InventoryAppsEntry();
            try {
                inventoryAppsEntry.setTimestamp(rs.getString("UTCTimestamp"));
                inventoryAppsEntry.setLocalTime(rs.getString("LocalTimestamp"));
                inventoryAppsEntry.setTimezone(rs.getString("Timezone"));
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
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return inventoryAppsEntry;
        }

    }

    private class AppInteractivityIterator extends DBIterator<AppInteractivityEntry> {
        public AppInteractivityIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public AppInteractivityEntry next() {
            AppInteractivityEntry appInteractivityEntry = new AppInteractivityEntry();
            try {
                appInteractivityEntry.setApp(rs.getString("AppID"));
                appInteractivityEntry.setTimestamp(rs.getString("UTCTimestamp"));
                appInteractivityEntry.setLocalTime(rs.getString("LocalTimestamp"));
                appInteractivityEntry.setTimezone(rs.getString("Timezone"));
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
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return appInteractivityEntry;
        }
    }

    private class DevicesIterator extends DBIterator<DevicesEntry> {

        public DevicesIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public DevicesEntry next() {
            DevicesEntry devicesEntry = new DevicesEntry();
            try {
                devicesEntry.setModel(rs.getString("Model"));
                devicesEntry.setInstanceId(rs.getString("InstanceId"));
                devicesEntry.setTimestamp(rs.getString("UTCTimestamp"));
                devicesEntry.setLocalTime(rs.getString("LocalTimestamp"));
                devicesEntry.setTimezone(rs.getString("Timezone"));
                devicesEntry.setTagName(rs.getString("TagName"));
                devicesEntry.setEventName(rs.getString("EventName"));
                devicesEntry.setProvider(rs.getString("Provider"));
                devicesEntry.setManufacturer(rs.getString("Manufacturer"));
                devicesEntry.setEnumerator(rs.getString("Enumerator"));
                devicesEntry.setInstallDate(rs.getString("InstallDate"));
                devicesEntry.setFirstInstallDate(rs.getString("FirstInstallDate"));
                devicesEntry.setUserSID(rs.getString("UserSID"));
                devicesEntry.setJSONPayload(rs.getString("JSONPayload"));
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return devicesEntry;
        }
    }

    private class CensusIterator extends DBIterator<CensusEntry> {

        public CensusIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public CensusEntry next() {
            CensusEntry censusEntry = new CensusEntry();
            try {
                censusEntry.setTimestamp(rs.getString("UTCTimestamp"));
                censusEntry.setLocalTime(rs.getString("LocalTimestamp"));
                censusEntry.setTimezone(rs.getString("Timezone"));
                censusEntry.setTagName(rs.getString("TagName"));
                censusEntry.setEventName(rs.getString("EventName"));
                censusEntry.setDataJSON(rs.getString("State \\ Settings"));
                censusEntry.setUserID(rs.getString("UserId"));
                censusEntry.setUserSID(rs.getString("UserSID"));
                censusEntry.setJSONPayload(rs.getString("JSONPayload"));
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return censusEntry;
        }
    }

    private class NetworkingIterator extends DBIterator<NetworkingEntry> {

        public NetworkingIterator(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public NetworkingEntry next() {
            NetworkingEntry networkingEntry = new NetworkingEntry();
            try {
                networkingEntry.setUTCTimestamp(rs.getString("UTCTimestamp"));
                networkingEntry.setLocalTime(rs.getString("LocalTimestamp"));
                networkingEntry.setTimezone(rs.getString("Timezone"));
                networkingEntry.setEventName(rs.getString("Event"));
                networkingEntry.setEventSource(rs.getString("EventSource"));
                networkingEntry.setEventReason(rs.getString("EventReason"));
                networkingEntry.setSeq(rs.getString("seq"));
                networkingEntry.setDataJSON(rs.getString("dataJSON"));
                networkingEntry.setJSONPayload(rs.getString("JSONPayload"));
            } catch (SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
            return networkingEntry;
        }
    }
}
