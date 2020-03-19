package dpf.inc.sepinf.winx.parsers;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;

/**
 * Parser for the Windows 10 Timeline feature (v1803/1809/1903+)
 *  
 * Timeline is like a browser history, but for the whole computer:  
 * - websites visited, documents edited, images viewed, programs executed etc
 * 
 * https://cclgroupltd.com/2018/05/03/windows-10-timeline-forensic-artefacts/
 * 
 * The SQLite query to parse the "ActivitiesCache.db" file was adapted from: https://github.com/kacos2000/WindowsTimeline
 * 
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class WinXTimelineParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType WIN10_TIMELINE_SQLITE = MediaType.application("x-win10-timeline-sqlite");

    public static final String WIN10_TIMELINE_SQLITE_MIME = "application/" + WIN10_TIMELINE_SQLITE.toString();

    public static final MediaType WIN10_TIMELINE = MediaType.application("x-win10-timeline");

    public static final MediaType WIN10_TIMELINE_REG = MediaType.application("x-win10-timeline-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(WIN10_TIMELINE_SQLITE);

    //Option to extract each Timeline registry as a subitem. Maybe also set this boolean in ParserConfig.xml..
    private boolean extractEntries = true;


    //Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }

    // Conversion from Datetime Format String to Date
    private Date convertStringToDate(String datetime) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(datetime);
    }


    /**
     * Main Parser Method - Windows 10 Timeline - ActivitiesCache.db
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        //Set Connection
        Connection connection = getConnection(stream, metadata, context);

        //Run Query and Obtain the Timeline entry iterable
        try (TimelineEntryIterable entries = runQuery(connection)){            

            XHTMLContentHandler xHtmlOuput = startWriteTimelineEntries(handler, metadata);
            
            int i = 0;
            for (TimelineEntry entry: entries) {
                
                emitTimelineEntry(xHtmlOuput, entry);

                /**
                 * Optionally extract entries as subitems 
                 */
                if (extractEntries) {
                    Metadata metadataTimelineItem = getEntryMetadata(entry, i++);
                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataTimelineItem, true);
                }

            }
            
            endTimelineEntries(xHtmlOuput);

        } catch (Exception e) {

            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite Win10Timeline parsing exception", e);

        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (Exception e) {
                // swallow
            }

        }
    }
    
    private Metadata getEntryMetadata(TimelineEntry entry, int i) throws ParseException {
        
        Metadata metadataTimelineItem = new Metadata();

        metadataTimelineItem.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, WIN10_TIMELINE_REG.toString());
        metadataTimelineItem.add(Metadata.RESOURCE_NAME_KEY, "WinXTimeline Entry " + i);

        //These properties need to get a "Date" type as parameters, so it can correctly show times in UTC
        metadataTimelineItem.set(TikaCoreProperties.CREATED, convertStringToDate(entry.getStartTime()));
        metadataTimelineItem.set(TikaCoreProperties.MODIFIED, convertStringToDate(entry.getLastModified()));

        metadataTimelineItem.add((BasicProps.HASH), "");

        //Timeline data
        metadataTimelineItem.add("etag", entry.getEtag());
        metadataTimelineItem.add("order", entry.getOrder());
        metadataTimelineItem.add("application", entry.getApplication());
        metadataTimelineItem.add("displayName", entry.getDisplayName());
        metadataTimelineItem.add("fileOpened", entry.getFileOpened());
        metadataTimelineItem.add("fullPath", entry.getFullPath());
        metadataTimelineItem.add("content_url", entry.getContent_url());
        metadataTimelineItem.add("info", entry.getInfo());
        metadataTimelineItem.add("appActivityId", entry.getAppActivityId());
        metadataTimelineItem.add("payload_OriginalTimezone", entry.getPayload_Timezone());
        metadataTimelineItem.add("activity_type", entry.getActivity_type());
        metadataTimelineItem.add("group", entry.getGroup());
        metadataTimelineItem.add("synced", entry.getSynced());
        metadataTimelineItem.add("platform", entry.getPlatform());
        metadataTimelineItem.add("tileStatus", entry.getTileStatus());
        metadataTimelineItem.add("wasRemoved", entry.getWasRemoved());
        metadataTimelineItem.add("uploadQueue", entry.getUploadQueue());
        metadataTimelineItem.add("isLocalOnly", entry.getIsLocalOnly());
        metadataTimelineItem.add("app_Uri", entry.getApp_Uri());
        metadataTimelineItem.add("priority", entry.getPriority());
        metadataTimelineItem.add("activeDuration", entry.getActiveDuration());
        metadataTimelineItem.add("calculatedDuration", entry.getCalculatedDuration());
        metadataTimelineItem.add("startTime", entry.getStartTime());
        metadataTimelineItem.add("lastModified", entry.getLastModified());
        metadataTimelineItem.add("lastModifiedOnClient", entry.getLastModifiedOnClient());
        metadataTimelineItem.add("endTime", entry.getEndTime());
        metadataTimelineItem.add("createdInCloud", entry.getCreatedInCloud());
        metadataTimelineItem.add("expiresIndays", entry.getExpiresIndays());
        metadataTimelineItem.add("expirationOnPackageID", entry.getExpirationOnPackageID());
        metadataTimelineItem.add("expiration", entry.getExpiration());
        metadataTimelineItem.add("tag", entry.getTag());
        metadataTimelineItem.add("matchID", entry.getMatchID());
        metadataTimelineItem.add("deviceID", entry.getDeviceID());
        metadataTimelineItem.add("packageIdHash", entry.getPackageIdHash());
        metadataTimelineItem.add("id", entry.getId());
        metadataTimelineItem.add("clipboardTextBase64", entry.getClipboardTextBase64());
        metadataTimelineItem.add("groupAppActivityId", entry.getGroupAppActivityId());
        metadataTimelineItem.add("enterpriseId", entry.getEnterpriseId());
        metadataTimelineItem.add("parentActivityId", entry.getParentActivityId());
        metadataTimelineItem.add("originalPayload", entry.getOriginalPayload());
        
        return metadataTimelineItem;
    }
    
    private interface TimelineEntryIterable extends Iterable<TimelineEntry>, Closeable{
    }
    
    private TimelineEntryIterable runQuery(Connection connection) throws SQLException {

        return new TimelineEntryIterable() {
            
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(WINX_TIMELINE_QUERY);

            @Override
            public Iterator<TimelineEntry> iterator() {
                
                return new Iterator<TimelineEntry>() {
                    
                    @Override
                    public boolean hasNext() {
                        try {
                            return rs.next();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public TimelineEntry next() {
                        
                        TimelineEntry entry = new TimelineEntry();
                        
                        try {
                            entry.setEtag(rs.getString("Etag"));
                            entry.setOrder(rs.getString("Order"));
                            entry.setApplication(rs.getString("Application"));
                            entry.setDisplayName(rs.getString("DisplayName"));
                            entry.setFileOpened(rs.getString("File Opened"));
                            entry.setFullPath(rs.getString("Full Path"));
                            entry.setContent_url(rs.getString("content_url"));
                            entry.setInfo(rs.getString("Info"));
                            entry.setAppActivityId(rs.getString("AppActivityId"));
                            entry.setPayload_Timezone(rs.getString("Payload/Timezone"));
                            entry.setActivity_type(rs.getString("Activity_type"));
                            entry.setGroup(rs.getString("Group"));
                            entry.setSynced(rs.getString("Synced"));
                            entry.setPlatform(rs.getString("Platform"));
                            entry.setTileStatus(rs.getString("TileStatus"));
                            entry.setWasRemoved(rs.getString("WasRemoved"));
                            entry.setUploadQueue(rs.getString("UploadQueue"));
                            entry.setIsLocalOnly(rs.getString("IsLocalOnly"));
                            entry.setApp_Uri(rs.getString("App/Uri"));
                            entry.setPriority(rs.getString("Priority"));
                            entry.setActiveDuration(rs.getString("Active Duration"));
                            entry.setCalculatedDuration(rs.getString("Calculated Duration"));
                            entry.setStartTime(rs.getString("StartTime"));
                            entry.setLastModified(rs.getString("LastModified"));
                            entry.setLastModifiedOnClient(rs.getString("LastModifiedOnClient"));
                            entry.setEndTime(rs.getString("EndTime"));
                            entry.setCreatedInCloud(rs.getString("CreatedInCloud"));
                            entry.setExpiresIndays(rs.getString("Expires In days"));
                            entry.setExpirationOnPackageID(rs.getString("Expiration on PackageID"));
                            entry.setExpiration(rs.getString("Expiration"));
                            entry.setTag(rs.getString("Tag"));
                            entry.setMatchID(rs.getString("MatchID"));
                            entry.setDeviceID(rs.getString("Device ID"));
                            entry.setPackageIdHash(rs.getString("PackageIdHash"));
                            entry.setId(rs.getString("ID"));
                            entry.setClipboardTextBase64(rs.getString("Clipboard Text(Base64)"));
                            entry.setGroupAppActivityId(rs.getString("GroupAppActivityId"));
                            entry.setEnterpriseId(rs.getString("EnterpriseId"));
                            entry.setParentActivityId(rs.getString("ParentActivityId"));
                            entry.setOriginalPayload(rs.getString("Original Payload"));
                            
                        }catch(SQLException e) {
                            throw new RuntimeException(e);
                        }
                        return entry;
                    }
                
                };
            }

            @Override
            public void close() throws IOException {
                try {
                    rs.close();
                    statement.close();
                } catch (SQLException e) {
                    //swallow
                }                
            }
            
        };

    }

    private XHTMLContentHandler startWriteTimelineEntries(ContentHandler handler, Metadata metadata) throws SAXException {

        XHTMLContentHandler xHandler = null;

        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
        xHandler.endElement("style");
        xHandler.endElement("head");

        xHandler.startElement("h2 align=center");
        xHandler.characters("Windows 10 Timeline registries");
        xHandler.endElement("h2");
        xHandler.startElement("br");
        xHandler.startElement("br");

        xHandler.startElement("table");

        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("Etag");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Order");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Application");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("DisplayName");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("File Opened");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Full Path");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("content_url");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Info");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("AppActivityId");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Payload/Timezone");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Activity_type");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Group");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Synced");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Platform");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("TileStatus");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("WasRemoved");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("UploadQueue");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("IsLocalOnly");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("App/Uri");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Priority");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Active Duration");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Calculated Duration");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("StartTime");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("LastModified");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("LastModifiedOnClient");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("EndTime");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("CreatedInCloud");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Expires In days");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Expiration on PackageID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Expiration");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Tag");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("MatchID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Device ID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("PackageIdHash");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("ID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Clipboard Text(Base64)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("GroupAppActivityId");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("EnterpriseId");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("ParentActivityId");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Original Payload");
        xHandler.endElement("th");

        xHandler.endElement("tr");
        
        return xHandler;
        
    }
        
    private void emitTimelineEntry(XHTMLContentHandler xHandler, TimelineEntry entry) throws SAXException {

        xHandler.startElement("tr");

        xHandler.startElement("td");
        xHandler.characters(entry.getEtag());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getOrder());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getApplication());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDisplayName());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getFileOpened());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getFullPath());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getContent_url());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getInfo());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getAppActivityId());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPayload_Timezone());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getActivity_type());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getGroup());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getSynced());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPlatform());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getTileStatus());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getWasRemoved());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getUploadQueue());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getIsLocalOnly());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getApp_Uri());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPriority());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getActiveDuration());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getCalculatedDuration());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getStartTime());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLastModified());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLastModifiedOnClient());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getEndTime());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getCreatedInCloud());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getExpiresIndays());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getExpirationOnPackageID());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getExpiration());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getTag());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getMatchID());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDeviceID());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPackageIdHash());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getId());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getClipboardTextBase64());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getGroupAppActivityId());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getEnterpriseId());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getParentActivityId());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getOriginalPayload());
        xHandler.endElement("td");

        xHandler.endElement("tr");
    
    }
        
    private void endTimelineEntries(XHTMLContentHandler xHandler) throws SAXException {
        xHandler.endElement("table");
        xHandler.endDocument();
    }

    /**
     * SQLite query from https://github.com/kacos2000/WindowsTimeline/blob/master/WindowsTimeline.sql
     * Works with Windows 10 v1803/1809/1903+ 
     */

    private String WINX_TIMELINE_QUERY =
            " SELECT " +
                    " ActivityOperation.ETag as 'Etag', " +
                    " ActivityOperation.OperationOrder as 'Order', " +
                    " case " +
                    " when ActivityOperation.ActivityType in (2,3,11,12,15)  " +
                    " then json_extract(ActivityOperation.AppId, '$[0].application') " +
                    " when json_extract(ActivityOperation.AppId, '$[0].application') = '308046B0AF4A39CB'  " +
                    " then 'Mozilla Firefox-64bit' " +
                    " when json_extract(ActivityOperation.AppId, '$[0].application') = 'E7CF176E110C211B' " +
                    " then 'Mozilla Firefox-32bit' " +
                    " when json_extract(ActivityOperation.AppId, '$[1].application') = '308046B0AF4A39CB'  " +
                    " then 'Mozilla Firefox-64bit' " +
                    " when json_extract(ActivityOperation.AppId, '$[1].application') = 'E7CF176E110C211B' " +
                    " then 'Mozilla Firefox-32bit' " +
                    " when length (json_extract(ActivityOperation.AppId, '$[0].application')) between 17 and 22  " +
                    " then replace(replace(replace(replace(replace(replace(json_extract(ActivityOperation.AppId, '$[1].application'), " +
                    " '{'||'6D809377-6AF0-444B-8957-A3773F02200E'||'}', '*ProgramFiles (x64)' ),   " +
                    " '{'||'7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E'||'}', '*ProgramFiles (x32)'), " +
                    " '{'||'1AC14E77-02E7-4E5D-B744-2EB1AE5198B7'||'}', '*System' ), " +
                    " '{'||'F38BF404-1D43-42F2-9305-67DE0B28FC23'||'}', '*Windows'), " +
                    " '{'||'D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27'||'}', '*System32'), " +
                    " 'Microsoft.AutoGenerated.{923DD477-5846-686B-A659-0FCCD73851A8}', 'Microsoft.Windows.Shell.RunDialog')   " +
                    " else  replace(replace(replace(replace(replace(replace " +
                    " (json_extract(ActivityOperation.AppId, '$[0].application'), " +
                    " '{'||'6D809377-6AF0-444B-8957-A3773F02200E'||'}', '*ProgramFiles (x64)'), " +
                    " '{'||'7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E'||'}', '*ProgramFiles (x32)'), " +
                    " '{'||'1AC14E77-02E7-4E5D-B744-2EB1AE5198B7'||'}', '*System'), " +
                    " '{'||'F38BF404-1D43-42F2-9305-67DE0B28FC23'||'}', '*Windows'), " +
                    " '{'||'D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27'||'}', '*System32'), " +
                    " 'Microsoft.AutoGenerated.{923DD477-5846-686B-A659-0FCCD73851A8}', 'Microsoft.Windows.Shell.RunDialog') " +
                    " end as 'Application', " +
                    " case when ActivityOperation.ActivityType =5  " +
                    " then json_extract(ActivityOperation.Payload, '$.appDisplayName')  " +
                    " else '' " +
                    " end as 'DisplayName', " +
                    " case  " +
                    " when ActivityOperation.ActivityType in (5,6)  " +
                    " then json_extract(ActivityOperation.Payload, '$.displayText')  " +
                    " else ''  " +
                    " end as 'File Opened', " +
                    " case  " +
                    " when ActivityOperation.ActivityType in (5,6)  " +
                    " then json_extract(ActivityOperation.Payload, '$.description') " +
                    " else ''   " +
                    " end as 'Full Path', " +
                    " case when ActivityOperation.Activitytype = 5 and json_extract(ActivityOperation.Payload, '$.contentUri') like '%file://%'  then  " +
                    " replace((case when instr(json_extract(ActivityOperation.Payload, '$.contentUri'),'?') > 0 " +
                    " then substr(json_extract(ActivityOperation.Payload, '$.contentUri'),1,instr(json_extract(ActivityOperation.Payload, '$.contentUri'),'?')) end),'?','')  " +
                    " else (case when ActivityOperation.Activitytype = 5 then json_extract(ActivityOperation.Payload, '$.contentUri') end) end as 'content_url', " +
                    " case when ActivityOperation.Activitytype = 5 and json_extract(ActivityOperation.Payload, '$.contentUri') like '%file://%' then  " +
                    " replace((case when instr(json_extract(ActivityOperation.Payload, '$.contentUri'),'?') > 0 " +
                    " then substr(json_extract(ActivityOperation.Payload, '$.contentUri'),instr(json_extract(ActivityOperation.Payload, '$.contentUri'),'?')) end),'?','')  " +
                    " else (case when ActivityOperation.Activitytype = 10 then json_extract(ActivityOperation.Payload,'$.1.content') end) " +
                    " end as 'Info', " +
                    " trim(ActivityOperation.AppActivityId,'ECB32AF3-1440-4086-94E3-5311F97F89C4\')  as 'AppActivityId', " +
                    " case  " +
                    " when ActivityOperation.ActivityType = 10 and json_extract(ActivityOperation.Payload,'$') notnull " +
                    " then json_extract(ActivityOperation.Payload,'$.1[0].content') " +
                    " when ActivityOperation.ActivityType in (2,3) then ActivityOperation.Payload " +
                    " when ActivityOperation.ActivityType = 5 and json_extract(ActivityOperation.Payload, '$.shellContentDescription') like '%FileShellLink%'  " +
                    " then json_extract(ActivityOperation.Payload, '$.shellContentDescription.FileShellLink')  " +
                    " when ActivityOperation.ActivityType = 6  " +
                    " then case  " +
                    " when json_extract(ActivityOperation.Payload,'$.devicePlatform') notnull  " +
                    " then json_extract(ActivityOperation.Payload, '$.type')||' - ' ||json_extract(ActivityOperation.Payload,'$.devicePlatform') " +
                    " else json_extract(ActivityOperation.Payload, '$.type')||' - ' ||json_extract(ActivityOperation.Payload,'$.userTimezone') end " +
                    " else '' " +
                    " end as 'Payload/Timezone', " +
                    " case  " +
                    " when ActivityOperation.ActivityType = 2 then 'Notification('||ActivityOperation.ActivityType||')' " +
                    " when ActivityOperation.ActivityType = 3 then 'Mobile Backup('||ActivityOperation.ActivityType||')'  " +
                    " when ActivityOperation.ActivityType = 5 then 'Open App/File/Page('||ActivityOperation.ActivityType||')'  " +
                    " when ActivityOperation.ActivityType = 6 then 'App In Use/Focus  ('||ActivityOperation.ActivityType||')'   " +
                    " when ActivityOperation.ActivityType = 10 then 'Clipboard ('||ActivityOperation.ActivityType||')'   " +
                    " when ActivityOperation.ActivityType = 16 then 'Copy/Paste('||ActivityOperation.ActivityType||')'  " +
                    " when ActivityOperation.ActivityType in (11,12,15) then 'System ('||ActivityOperation.ActivityType||')'  " +
                    " else ActivityOperation.ActivityType  " +
                    " end as 'Activity_type', " +
                    " ActivityOperation.'Group' as 'Group', " +
                    " case json_extract(ActivityOperation.AppId, '$[0].platform')  " +
                    " when 'afs_crossplatform'  " +
                    " then 'Yes'  " +
                    " when 'host'  " +
                    " then (case json_extract(ActivityOperation.AppId, '$[1].platform')  " +
                    " when 'afs_crossplatform'  " +
                    " then 'Yes'  " +
                    " else ''  " +
                    " end)  " +
                    " else ''  " +
                    " end as 'Synced',    " +
                    " case  " +
                    " when json_extract(ActivityOperation.AppId, '$[0].platform') = 'afs_crossplatform'  " +
                    " then json_extract(ActivityOperation.AppId, '$[1].platform') " +
                    " else json_extract(ActivityOperation.AppId, '$[0].platform')  " +
                    " end as 'Platform', " +
                    " case ActivityOperation.OperationType  " +
                    " when 1 then 'Active'  " +
                    " when 2 then 'Updated'  " +
                    " when 3 then 'Deleted'  " +
                    " when 4 then 'Ignored'  " +
                    " end as 'TileStatus', " +
                    " case  " +
                    " when ActivityOperation.Id in " +
                    " (select Activity.Id from Activity where Activity.Id = ActivityOperation.Id)  " +
                    " then 'Removed'  " +
                    " end as 'WasRemoved', " +
                    " case  " +
                    " when ActivityOperation.Id in(select Activity.Id from Activity where Activity.Id = ActivityOperation.Id)  " +
                    " then ''  " +
                    " else 'In Queue'  " +
                    " end as 'UploadQueue', " +
                    " '' as 'IsLocalOnly', " +
                    " case  " +
                    " when ActivityOperation.ActivityType in (2,11,12,15)  " +
                    " then '' " +
                    " else coalesce(json_extract(ActivityOperation.Payload, '$.activationUri'),json_extract(ActivityOperation.Payload, '$.reportingApp'))  " +
                    " end as 'App/Uri', " +
                    " ActivityOperation.Priority as 'Priority',   " +
                    " case  " +
                    " when ActivityOperation.ActivityType != 6  " +
                    " then '' " +
                    " else time(json_extract(ActivityOperation.Payload, '$.activeDurationSeconds'),'unixepoch')  " +
                    " end as 'Active Duration', " +
                    " case  " +
                    " when ActivityOperation.ActivityType = 6  and cast((ActivityOperation.EndTime - ActivityOperation.StartTime) as integer) > 0  " +
                    " then time(cast((ActivityOperation.EndTime - ActivityOperation.StartTime) as integer),'unixepoch')  " +
                    " end as 'Calculated Duration', " +
                    " datetime(ActivityOperation.StartTime, 'unixepoch') as 'StartTime',  " +
                    " datetime(ActivityOperation.LastModifiedTime, 'unixepoch') as 'LastModified', " +
                    " case  " +
                    " when ActivityOperation.OriginalLastModifiedOnClient > 0  " +
                    " then datetime(ActivityOperation.OriginalLastModifiedOnClient, 'unixepoch')  " +
                    " else ''  " +
                    " end as 'LastModifiedOnClient', " +
                    " case  " +
                    " when ActivityOperation.EndTime > 0  " +
                    " then datetime(ActivityOperation.EndTime, 'unixepoch')  " +
                    " else ''  " +
                    " end as 'EndTime', " +
                    " case  " +
                    " when ActivityOperation.CreatedInCloud > 0  " +
                    " then datetime(ActivityOperation.CreatedInCloud, 'unixepoch')  " +
                    " else ''  " +
                    " end as 'CreatedInCloud', " +
                    " cast((ActivityOperation.ExpirationTime - ActivityOperation.LastModifiedTime) as integer) / '86400' as 'Expires In days', " +
                    " datetime(Activity_PackageId.ExpirationTime, 'unixepoch') as 'Expiration on PackageID', " +
                    " datetime(ActivityOperation.ExpirationTime, 'unixepoch') as 'Expiration', " +
                    " ActivityOperation.Tag as 'Tag', " +
                    " ActivityOperation.MatchId as 'MatchID', " +
                    " ActivityOperation.PlatformDeviceId as 'Device ID',  " +
                    " ActivityOperation.PackageIdHash as 'PackageIdHash', " +
                    " '{' || substr(hex(Activity_PackageId.ActivityId), 1, 8) || '-' ||  " +
                    " substr(hex(Activity_PackageId.ActivityId), 9, 4) || '-' ||  " +
                    " substr(hex(Activity_PackageId.ActivityId), 13, 4) || '-' ||  " +
                    " substr(hex(Activity_PackageId.ActivityId), 17, 4) || '-' ||  " +
                    " substr(hex(Activity_PackageId.ActivityId), 21, 12) || '}' as 'ID',  " +
                    " case  " +
                    " when ActivityOperation.ActivityType = 10  " +
                    " then json_extract(ActivityOperation.ClipboardPayload,'$[0].content') " +
                    " else '' " +
                    " end as 'Clipboard Text(Base64)', " +
                    " ActivityOperation.GroupAppActivityId as 'GroupAppActivityId', " +
                    " ActivityOperation.EnterpriseId as 'EnterpriseId', " +
                    " case  " +
                    " when hex(ActivityOperation.ParentActivityId) = '00000000000000000000000000000000' " +
                    " then '' else   " +
                    " '{' || substr(hex(ActivityOperation.ParentActivityId), 1, 8) || '-' ||  " +
                    " substr(hex(ActivityOperation.ParentActivityId), 9, 4) || '-' ||  " +
                    " substr(hex(ActivityOperation.ParentActivityId), 13, 4) || '-' ||  " +
                    " substr(hex(ActivityOperation.ParentActivityId), 17, 4) || '-' ||  " +
                    " substr(hex(ActivityOperation.ParentActivityId), 21, 12) || '}'  " +
                    " end as 'ParentActivityId', " +
                    " case  " +
                    " when ActivityOperation.ActivityType in (11,12,15)  " +
                    " then ActivityOperation.OriginalPayload " +
                    " else json_extract(ActivityOperation.OriginalPayload, '$')  " +
                    " end as 'Original Payload' " +
                    " from Activity_PackageId " +
                    " join ActivityOperation on Activity_PackageId.ActivityId = ActivityOperation.Id   " +
                    " where Activity_PackageId.Platform = json_extract(ActivityOperation.AppId, '$[0].platform')  " +
                    " and Activity_PackageId.ActivityId = ActivityOperation.Id " +
                    " union   " +
                    " select " +
                    " Activity.ETag as 'Etag', " +
                    " null as 'Order',   " +
                    " case " +
                    " when Activity.ActivityType in (2,3,11,12,15)  " +
                    " then json_extract(Activity.AppId, '$[0].application') " +
                    " when json_extract(Activity.AppId, '$[0].application') = '308046B0AF4A39CB'  " +
                    " then 'Mozilla Firefox-64bit' " +
                    " when json_extract(Activity.AppId, '$[0].application') = 'E7CF176E110C211B' " +
                    " then 'Mozilla Firefox-32bit' " +
                    " when json_extract(Activity.AppId, '$[1].application') = '308046B0AF4A39CB'  " +
                    " then 'Mozilla Firefox-64bit' " +
                    " when json_extract(Activity.AppId, '$[1].application') = 'E7CF176E110C211B' " +
                    " then 'Mozilla Firefox-32bit' " +
                    " when length (json_extract(Activity.AppId, '$[0].application')) between 17 and 22  " +
                    " then replace(replace(replace(replace(replace(replace(json_extract(Activity.AppId, '$[1].application'), " +
                    " '{'||'6D809377-6AF0-444B-8957-A3773F02200E'||'}', '*ProgramFiles (x64)' ),   " +
                    " '{'||'7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E'||'}', '*ProgramFiles (x32)'), " +
                    " '{'||'1AC14E77-02E7-4E5D-B744-2EB1AE5198B7'||'}', '*System' ), " +
                    " '{'||'F38BF404-1D43-42F2-9305-67DE0B28FC23'||'}', '*Windows'), " +
                    " '{'||'D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27'||'}', '*System32'), " +
                    " 'Microsoft.AutoGenerated.{923DD477-5846-686B-A659-0FCCD73851A8}', 'Microsoft.Windows.Shell.RunDialog')   " +
                    " else  replace(replace(replace(replace(replace(replace " +
                    " (json_extract(Activity.AppId, '$[0].application'), " +
                    " '{'||'6D809377-6AF0-444B-8957-A3773F02200E'||'}', '*ProgramFiles (x64)'), " +
                    " '{'||'7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E'||'}', '*ProgramFiles (x32)'), " +
                    " '{'||'1AC14E77-02E7-4E5D-B744-2EB1AE5198B7'||'}', '*System'), " +
                    " '{'||'F38BF404-1D43-42F2-9305-67DE0B28FC23'||'}', '*Windows'), " +
                    " '{'||'D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27'||'}', '*System32'), " +
                    " 'Microsoft.AutoGenerated.{923DD477-5846-686B-A659-0FCCD73851A8}', 'Microsoft.Windows.Shell.RunDialog') " +
                    " end as 'Application', " +
                    " case when Activity.ActivityType = 5  " +
                    " then json_extract(Activity.Payload, '$.appDisplayName')  " +
                    " else '' " +
                    " end as 'DisplayName', " +
                    " case  " +
                    " when Activity.ActivityType in (5,6)  " +
                    " then json_extract(Activity.Payload, '$.displayText')  " +
                    " else ''  " +
                    " end as 'File Opened', " +
                    " case  " +
                    " when Activity.ActivityType in (5,6)  " +
                    " then json_extract(Activity.Payload, '$.description')  " +
                    " else ''  " +
                    " end as 'Full Path', " +
                    " case when Activity.Activitytype = 5 and json_extract(Activity.Payload, '$.contentUri') like '%file://%'  then  " +
                    " replace((case when instr(json_extract(Activity.Payload, '$.contentUri'),'?') > 0 " +
                    " then substr(json_extract(Activity.Payload, '$.contentUri'),1,instr(json_extract(Activity.Payload, '$.contentUri'),'?')) end),'?','')  " +
                    " else (case when Activity.Activitytype = 5 then json_extract(Activity.Payload, '$.contentUri') end) end as 'content_url', " +
                    " case when Activity.Activitytype = 5 and json_extract(Activity.Payload, '$.contentUri') like '%file://%' then  " +
                    " replace((case when instr(json_extract(Activity.Payload, '$.contentUri'),'?') > 0 " +
                    " then substr(json_extract(Activity.Payload, '$.contentUri'),instr(json_extract(Activity.Payload, '$.contentUri'),'?')) end),'?','')  " +
                    " else (case when Activity.Activitytype = 10 then json_extract(Activity.Payload,'$.1.content') end) " +
                    " end as 'Info', " +
                    " trim(Activity.AppActivityId,'ECB32AF3-1440-4086-94E3-5311F97F89C4\')  as 'AppActivityId', " +
                    " case  " +
                    " when Activity.ActivityType = 10 and json_extract(Activity.Payload,'$') notnull " +
                    " then json_extract(Activity.Payload,'$.1[0].content') " +
                    " when Activity.ActivityType in (2,3) then Activity.Payload " +
                    " when Activity.ActivityType = 5 and json_extract(Activity.Payload, '$.shellContentDescription') like '%FileShellLink%'  " +
                    " then json_extract(Activity.Payload, '$.shellContentDescription.FileShellLink')  " +
                    " when Activity.ActivityType = 6 " +
                    " then case " +
                    " when json_extract(Activity.Payload,'$.devicePlatform') notnull  " +
                    " then json_extract(Activity.Payload, '$.type')||' - ' ||json_extract(Activity.Payload,'$.devicePlatform') " +
                    " else json_extract(Activity.Payload, '$.type')||' - ' ||json_extract(Activity.Payload,'$.userTimezone') end " +
                    " else '' " +
                    " end as 'Payload/Timezone', " +
                    " case  " +
                    " when Activity.ActivityType = 2 then 'Notification('||Activity.ActivityType||')'  " +
                    " when Activity.ActivityType = 3 then 'Mobile Backup('||Activity.ActivityType||')'  " +
                    " when Activity.ActivityType = 5 then 'Open App/File/Page('||Activity.ActivityType||')'  " +
                    " when Activity.ActivityType = 6 then 'App In Use/Focus  ('||Activity.ActivityType||')'   " +
                    " when Activity.ActivityType = 10 then 'Clipboard ('||Activity.ActivityType||')'   " +
                    " when Activity.ActivityType = 16 then 'Copy/Paste('||Activity.ActivityType||')'  " +
                    " when Activity.ActivityType in (11,12,15) then 'System ('||Activity.ActivityType||')'  " +
                    " else Activity.ActivityType  " +
                    " end as 'Activity_type', " +
                    " Activity.'Group' as 'Group', " +
                    " case json_extract(Activity.AppId, '$[0].platform')  " +
                    " when 'afs_crossplatform'  " +
                    " then 'Yes'  " +
                    " when 'host'  " +
                    " then (case json_extract(Activity.AppId, '$[1].platform')  " +
                    " when 'afs_crossplatform'  " +
                    " then'Yes'  " +
                    " else ''  " +
                    " end)  " +
                    " else ''  " +
                    " end as 'Synced', " +
                    " case  " +
                    " when json_extract(Activity.AppId, '$[0].platform') = 'afs_crossplatform'  " +
                    " then json_extract(Activity.AppId, '$[1].platform') " +
                    " else json_extract(Activity.AppId, '$[0].platform')  " +
                    " end as 'Platform', " +
                    " case Activity.ActivityStatus  " +
                    " when 1 then 'Active'  " +
                    " when 2 then 'Updated'  " +
                    " when 3 then 'Deleted'  " +
                    " when 4 then 'Ignored'  " +
                    " end as 'TileStatus', " +
                    " null as 'WasRemoved', " +
                    " 'No' as 'UploadQueue', " +
                    " case Activity.IsLocalOnly " +
                    " when 0 then 'No'  " +
                    " when 1 then 'Yes'  " +
                    " else Activity.IsLocalOnly  " +
                    " end as 'IsLocalOnly', " +
                    " case  " +
                    " when Activity.ActivityType in (2,11,12,15)  " +
                    " then '' " +
                    " else  coalesce(json_extract(Activity.Payload, '$.activationUri'),json_extract(Activity.Payload, '$.reportingApp'))  " +
                    " end as 'App/Uri', " +
                    " Activity.Priority as 'Priority',   " +
                    " case  " +
                    " when Activity.ActivityType != 6  " +
                    " then '' " +
                    " else time(json_extract(Activity.Payload, '$.activeDurationSeconds'),'unixepoch')  " +
                    " end as 'Active Duration', " +
                    " case  " +
                    " when Activity.ActivityType = 6  and cast((Activity.EndTime - Activity.StartTime) as integer) > 0  " +
                    " then time(cast((Activity.EndTime - Activity.StartTime) as integer),'unixepoch')  " +
                    " end as 'Calculated Duration', " +
                    " datetime(Activity.StartTime, 'unixepoch') as 'StartTime', " +
                    " datetime(Activity.LastModifiedTime, 'unixepoch') as 'LastModified', " +
                    " case  " +
                    " when Activity.OriginalLastModifiedOnClient > 0  " +
                    " then datetime(Activity.OriginalLastModifiedOnClient, 'unixepoch')  " +
                    " else '  -  '  " +
                    " end as 'LastModifiedOnClient', " +
                    " case  " +
                    " when Activity.EndTime > 0  " +
                    " then datetime(Activity.EndTime, 'unixepoch')  " +
                    " else '-' " +
                    " end as 'EndTime', " +
                    " case  " +
                    " when Activity.CreatedInCloud > 0  " +
                    " then datetime(Activity.CreatedInCloud, 'unixepoch')  " +
                    " else '-' " +
                    " end as 'CreatedInCloud', " +
                    " cast((Activity.ExpirationTime - Activity.LastModifiedTime) as integer) / '86400' as 'Expires In days', " +
                    " datetime(Activity_PackageId.ExpirationTime, 'unixepoch') as 'Expiration on PackageID', " +
                    " datetime(Activity.ExpirationTime, 'unixepoch') as 'Expiration', " +
                    " Activity.Tag as 'Tag', " +
                    " Activity.MatchId as 'MatchID', " +
                    " Activity.PlatformDeviceId as 'Device ID',  " +
                    " Activity.PackageIdHash as 'PackageIdHash', " +
                    " '{' || substr(hex(Activity_PackageId.ActivityId), 1, 8) || '-' || " +
                    " substr(hex(Activity_PackageId.ActivityId), 9, 4) || '-' || " +
                    " substr(hex(Activity_PackageId.ActivityId), 13, 4) || '-' || " +
                    " substr(hex(Activity_PackageId.ActivityId), 17, 4) || '-' || " +
                    " substr(hex(Activity_PackageId.ActivityId), 21, 12) || '}' as 'ID', " +
                    " case  " +
                    " when Activity.ActivityType = 10  " +
                    " then json_extract(Activity.ClipboardPayload,'$[0].content') " +
                    " else '' " +
                    " end as 'Clipboard Text(Base64)', " +
                    " Activity.GroupAppActivityId as 'GroupAppActivityId', " +
                    " Activity.EnterpriseId as 'EnterpriseId', " +
                    " case  " +
                    " when hex(Activity.ParentActivityId) = '00000000000000000000000000000000' " +
                    " then ''  " +
                    " else   " +
                    " '{' || substr(hex(Activity.ParentActivityId), 1, 8) || '-' ||  " +
                    " substr(hex(Activity.ParentActivityId), 9, 4) || '-' ||  " +
                    " substr(hex(Activity.ParentActivityId), 13, 4) || '-' ||  " +
                    " substr(hex(Activity.ParentActivityId), 17, 4) || '-' ||  " +
                    " substr(hex(Activity.ParentActivityId), 21, 12) || '}'  " +
                    " end as 'ParentActivityId', " +
                    " case  " +
                    " when Activity.ActivityType in (11,12,15)  " +
                    " then Activity.OriginalPayload " +
                    " else json_extract(Activity.OriginalPayload, '$')  " +
                    " end as 'Original Payload' " +
                    " " +
                    " from Activity_PackageId " +
                    " join Activity on Activity_PackageId.ActivityId = Activity.Id   " +
                    " where Activity_PackageId.Platform = json_extract(Activity.AppId, '$[0].platform') " +
                    " and Activity_PackageId.ActivityId = Activity.Id " +
                    " order by Etag desc  ";

}