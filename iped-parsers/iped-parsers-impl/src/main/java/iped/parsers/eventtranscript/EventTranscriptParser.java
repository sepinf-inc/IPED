package iped.parsers.eventtranscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
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
 * @author Felipe Farias da Costa <felipecostasdc@gmail.com>
 */
public class EventTranscriptParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType EVENT_TRANSCRIPT = MediaType.application("x-event-transcript");

    public static final MediaType EVENT_TRANSCRIPT_HIST = MediaType.application("x-event-transcript-history");
    public static final MediaType EVENT_TRANSCRIPT_HIST_REG = MediaType.application("x-event-transcript-history-registry");

    public static final MediaType EVENT_TRANSCRIPT_SW_INVENTORY = MediaType.application("x-event-transcript-sw-inventory");
    public static final MediaType EVENT_TRANSCRIPT_SW_INVENTORY_REG = MediaType.application("x-event-transcript-sw-inventory-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(EVENT_TRANSCRIPT);

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
        File softwareInventoryFile = tmp.createTemporaryFile();

        if (new SQLiteContainerDetector().detect(tis, metadata) != EVENT_TRANSCRIPT) {
            sqliteParser.parse(stream, handler, metadata, context);
            return;
        }

        try (Connection connection = getConnection(tis, metadata, context)) {

            try (FileOutputStream tmpHistoryFile = new FileOutputStream(browserHistoryFile)) {

                ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-16");

                Metadata metadataHistory = new Metadata();
                metadataHistory.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_HIST.toString());
                metadataHistory.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript History");
                metadataHistory.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                metadataHistory.set(BasicProps.HASCHILD, "true");
                metadataHistory.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (BrowserHistoryIterable historyEntriesIterable = new BrowserHistoryIterable(connection, HISTORY_QUERY)) {
                    XHTMLContentHandler xHandler = startBrowserHistoryParsing(historyHandler, metadataHistory);

                    int i = 0;
                    for (BrowserHistoryEntry historyEntry : historyEntriesIterable) {
                        emitHistoryEntry(xHandler, historyEntry, i++);

                        if (extractEntries) {
                            Metadata metadataHistoryEntry = getHistoryEntryMetadata(historyEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistoryEntry, true);
                        }
                    }

                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(browserHistoryFile)) {
                        extractor.parseEmbedded(fis, handler, metadataHistory, true);
                    }
                }
            }

            try (FileOutputStream tmpSoftwareInventoryFile = new FileOutputStream(softwareInventoryFile)) {

                ToXMLContentHandler softwareInventoryHandler = new ToXMLContentHandler(tmpSoftwareInventoryFile, "UTF-16");

                Metadata metadataSwSetup = new Metadata();
                metadataSwSetup.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_SW_INVENTORY.toString());
                metadataSwSetup.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Software Inventory");
                metadataSwSetup.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                metadataSwSetup.set(BasicProps.HASCHILD, "true");
                metadataSwSetup.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                try (SoftwareInventoryIterable swInventoryEntriesIterable = new SoftwareInventoryIterable(connection, SOFTWARE_INVENTORY_QUERY)) {
                    XHTMLContentHandler xHandler = startSoftwareInventoryParsing(softwareInventoryHandler, metadataSwSetup);

                    int i = 0;
                    for (SoftwareInventoryEntry inventoryEntry : swInventoryEntriesIterable) {
                        emitSoftwareInventoryEntry(xHandler, inventoryEntry, i++);

                        if (extractEntries) {
                            Metadata metadataInventoryEntry = getInventoryEntryMetadata(inventoryEntry, i);
                            extractor.parseEmbedded(new EmptyInputStream(), handler, metadataInventoryEntry, true);
                        }
                    }

                    xHandler.endElement("table");
                    xHandler.endDocument();

                    try (FileInputStream fis = new FileInputStream(softwareInventoryFile)) {
                        extractor.parseEmbedded(fis, handler, metadataSwSetup, true);
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

    private Metadata getHistoryEntryMetadata(BrowserHistoryEntry historyEntry, int i) throws ParseException {
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
        // metadataHistoryEntry.add("originalPayload", historyEntry.getJSONPayload());

        return metadataHistoryEntry;
    }

    private XHTMLContentHandler startBrowserHistoryParsing(ContentHandler handler, Metadata metadata) throws SAXException {
        XHTMLContentHandler xHandler = null;

        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
        xHandler.endElement("style");
        xHandler.endElement("head");

        xHandler.startElement("h2 align=center");
        xHandler.characters("Event Transcript Browser History");
        xHandler.endElement("h2");
        xHandler.startElement("br");
        xHandler.startElement("br");

        xHandler.startElement("table");
        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("");    // idx
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Page Title(s)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Visit Date (UTC)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("URL");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("App");
        xHandler.endElement("th");

        xHandler.endElement("tr");

        return xHandler;
    }

    private void emitHistoryEntry(XHTMLContentHandler xHandler, BrowserHistoryEntry historyEntry, int idx) throws SQLException, TikaException, SAXException {
        xHandler.startElement("tr");

        xHandler.startElement("td");
        xHandler.characters(String.valueOf(idx));
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(String.join("; ", historyEntry.getPageTitles()));
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(historyEntry.getTimestampStr());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(historyEntry.getUrl());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(historyEntry.getAppName());
        xHandler.endElement("td");

        xHandler.endElement("tr");
    }

    private Metadata getInventoryEntryMetadata(SoftwareInventoryEntry entry, int i) throws ParseException {
        Metadata metadataEntry = new Metadata();

        metadataEntry.add(StandardParser.INDEXER_CONTENT_TYPE, EVENT_TRANSCRIPT_SW_INVENTORY_REG.toString());
        metadataEntry.add(TikaCoreProperties.RESOURCE_NAME_KEY, "Event Transcript Software Inventory Entry " + i);
        metadataEntry.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
        metadataEntry.set(BasicProps.LENGTH, "");

        metadataEntry.set(TikaCoreProperties.CREATED, entry.getTimestamp());
        metadataEntry.add(TikaCoreProperties.TITLE, entry.getName());
        metadataEntry.set(ExtraProperties.DOWNLOAD_DATE, entry.getInstallDate());
        metadataEntry.set(TikaCoreProperties.SOURCE_PATH, entry.getRootDirPath());
        // metadataHistoryEntry.add("originalPayload", entry.getJSONPayload());

        return metadataEntry;
    }

    private XHTMLContentHandler startSoftwareInventoryParsing(ContentHandler handler, Metadata metadata) throws SAXException {
        XHTMLContentHandler xHandler = null;

        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
        xHandler.endElement("style");
        xHandler.endElement("head");

        xHandler.startElement("h2 align=center");
        xHandler.characters("Event Transcript Software Inventory");
        xHandler.endElement("h2");
        xHandler.startElement("br");
        xHandler.startElement("br");

        xHandler.startElement("table");
        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("");    // idx
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Name");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Timestamp (UTC)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Version");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Publisher");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Root Directory Path");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Install Date");
        xHandler.endElement("th");

        xHandler.endElement("tr");

        return xHandler;
    }

    private void emitSoftwareInventoryEntry(XHTMLContentHandler xHandler, SoftwareInventoryEntry entry, int idx) throws SQLException, TikaException, SAXException {
        xHandler.startElement("tr");

        xHandler.startElement("td");
        xHandler.characters(String.valueOf(idx));
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getName());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getTimestampStr());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getVersion());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPublisher());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getRootDirPath());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getInstallDateStr());
        xHandler.endElement("td");

        xHandler.endElement("tr");
    }


    private class BrowserHistoryIterable extends AbstractDBIterable<BrowserHistoryEntry> {

        public BrowserHistoryIterable(Connection connection, String query) throws SQLException {
            super(connection, query);
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
    }

    private class SoftwareInventoryIterable extends AbstractDBIterable<SoftwareInventoryEntry> {

        public SoftwareInventoryIterable(Connection connection, String query) throws SQLException {
            super(connection, query);
        }

        @Override
        public Iterator<SoftwareInventoryEntry> iterator() {
            return new Iterator<SoftwareInventoryEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public SoftwareInventoryEntry next() {
                    SoftwareInventoryEntry softwareInventoryEntry = new SoftwareInventoryEntry();

                    try {
                        softwareInventoryEntry.setTimestamp(rs.getString("Timestamp"));
                        softwareInventoryEntry.setTagName(rs.getString("TagName"));
                        softwareInventoryEntry.setEventName(rs.getString("EventName"));
                        softwareInventoryEntry.setType(rs.getString("Type"));
                        softwareInventoryEntry.setName(rs.getString("Name"));
                        softwareInventoryEntry.setPackageFullName(rs.getString("PackageFullName"));
                        softwareInventoryEntry.setVersion(rs.getString("Version"));
                        softwareInventoryEntry.setPublisher(rs.getString("Publisher"));
                        softwareInventoryEntry.setRootDirPath(rs.getString("RootDirPath"));
                        softwareInventoryEntry.setHidden(rs.getString("HiddenArp"));
                        softwareInventoryEntry.setInstallDate(rs.getString("InstallDate"));
                        softwareInventoryEntry.setSource(rs.getString("Source"));
                        softwareInventoryEntry.setOSVersionAtInstallTime(rs.getString("OSVersionAtInstallTime"));
                        softwareInventoryEntry.setUserSID(rs.getString("UserSID"));
                        softwareInventoryEntry.setUserID(rs.getString("UserID"));
                        softwareInventoryEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return softwareInventoryEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private String HISTORY_QUERY = "SELECT"
        + " UserSID,"
        + " json_extract(JSONPayload,'$.data.CorrelationGuid') AS CorrelationGuid,"
        + " Timestamp,"
        + " replace(group_concat(DISTINCT TagName), ',', ';') AS TagNames,"
        + " replace(group_concat(DISTINCT EventName), ',', ';') AS EventNames,"
        + " URL,"
        + " App,"
        + " replace(group_concat(DISTINCT nullif(PageTitle, '')), ',', ';') AS PageTitles,"
        + " JSONPayload"
        + " FROM ("
        + " SELECT events_persisted.sid AS UserSID,"
        + "     datetime((events_persisted.timestamp/10000000) - 11644473600, 'unixepoch', 'UTC') AS Timestamp,"
        + "     tag_descriptions.tag_name AS TagName,"
        + "     events_persisted.full_event_name AS FullEventName,"
        + "     replace(replace(substr(distinct events_persisted.full_event_name,39),'Microsoft.',''),'WebBrowser.HistoryJournal.HJ_','') AS 'EventName',"
        + "     json_extract(events_persisted.payload,'$.ext.app.name') AS App,"
        + "     events_persisted.compressed_payload_size AS CompressedPayloadSize,"
        + "     json_extract(events_persisted.payload,'$.data.navigationUrl') AS URL,"
        + "     json_extract(events_persisted.payload,'$.data.PageTitle') AS PageTitle,"
        + "     events_persisted.payload AS JSONPayload"
        + " FROM"
        + "     events_persisted"
        + "     LEFT JOIN event_tags ON events_persisted.full_event_name_hash = event_tags.full_event_name_hash"
        + "     LEFT JOIN tag_descriptions ON event_tags.tag_id = tag_descriptions.tag_id"
        + "     INNER JOIN provider_groups ON events_persisted.provider_group_id = provider_groups.group_id"
        + " WHERE"
        + "     (tag_descriptions.tag_name='Browsing History' AND events_persisted.full_event_name LIKE '%HistoryAddUrl') OR"
        + "     (tag_descriptions.tag_name='Product and Service Usage' AND events_persisted.full_event_name LIKE '%HistoryAddUrlEx')"
        + " )"
        + " GROUP BY CorrelationGuid"
        + " ORDER BY Timestamp DESC";


        private String SOFTWARE_INVENTORY_QUERY = "SELECT"
            + " datetime( ( events_persisted.timestamp / 10000000 ) - 11644473600, 'unixepoch' ) AS 'Timestamp',"
            + " json_extract(events_persisted.payload,'$.ext.utc.seq') as 'seq', "
            + " tag_descriptions.tag_name AS TagName,"
            + " replace(events_persisted.full_event_name,'Microsoft.Windows.Inventory.Core.Inventory','') as 'EventName',"
            + " events_persisted.full_event_name as 'FullEventName',"
            + " json_extract(events_persisted.payload,'$.data.Type') as 'Type',"
            + " json_extract(events_persisted.payload,'$.data.Name') as 'Name',"
            + " json_extract(events_persisted.payload,'$.data.PackageFullName') as 'PackageFullName',"
            + " json_extract(events_persisted.payload,'$.data.Version') as 'Version',"
            + " json_extract(events_persisted.payload,'$.data.Publisher') as 'Publisher',"
            + " json_extract(events_persisted.payload,'$.data.RootDirPath') as 'RootDirPath',"
            + " json_extract(events_persisted.payload,'$.data.HiddenArp') as 'HiddenArp',"
            + " json_extract(events_persisted.payload,'$.data.InstallDate') as 'InstallDate',"
            + " json_extract(events_persisted.payload,'$.data.Source') as 'Source',"
            + " json_extract(events_persisted.payload,'$.data.OSVersionAtInstallTime') as 'OSVersionAtInstallTime',"
            + " json_extract(events_persisted.payload,'$.data.InstallDateMsi') as 'MsiInstallDate',"
            + " json_extract(events_persisted.payload,'$.data.MsiPackageCode') as 'MsiPackageCode',"
            + " json_extract(events_persisted.payload,'$.data.MsiProductCode') as 'MsiProductCode',"
            + " case json_extract(events_persisted.payload,'$.data.baseData.action') "
            + "     when 1 then 'Add'"
            + "     when 2 then 'Remove'"
            + "     else json_extract(events_persisted.payload,'$.data.baseData.action') "
            + " end as 'action',"
            + " json_extract(events_persisted.payload,'$.data.baseData.objectInstanceId') as 'InstanceId',"
            + " trim(json_extract(events_persisted.payload,'$.ext.user.localId'),'m:') as 'UserId',"
            + " sid as 'UserSID',"
            + " events_persisted.payload AS JSONPayload"
            + " FROM"
            + "     events_persisted"
            + "     LEFT JOIN event_tags ON events_persisted.full_event_name_hash = event_tags.full_event_name_hash"
            + "     LEFT JOIN tag_descriptions ON event_tags.tag_id = tag_descriptions.tag_id"
            + " WHERE"
            + " events_persisted.full_event_name like 'Microsoft.Windows.Inventory.Core.Inventory%' and"
            + " TagName = 'Software Setup and Inventory'"
            + " order by cast(events_persisted.timestamp as integer) desc";
}