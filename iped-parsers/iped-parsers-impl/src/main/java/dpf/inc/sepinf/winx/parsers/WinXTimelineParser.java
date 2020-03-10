package dpf.inc.sepinf.winx.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;


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
    public static final MediaType WIN10_TIMELINE_MEDIA_TYPE = MediaType.application("x-win10-timeline");
    private static final Set < MediaType > SUPPORTED_TYPES = Collections.singleton(WIN10_TIMELINE_MEDIA_TYPE);
    public static final String WIN10_TIMELINE_MIME = "application/x-win10-timeline";

    //Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set < MediaType > getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
    throws IOException, SAXException, TikaException {

    	Connection connection = null;
        connection = getConnection(stream, metadata, context);

        Statement statement = null;
        try {
            statement = connection.createStatement();

            ResultSet rs = statement.executeQuery(WINX_TIMELINE_QUERY);

            ResultSetMetaData columnNames = rs.getMetaData();

            parseTimelineQuery(handler, metadata, rs, columnNames);

        } catch (Exception e) {

            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite Win10Timeline parsing exception", e);

        } finally {
            try {
                if (statement != null)
                    statement.close();
                if (connection != null)
                    connection.close();
            } catch (Exception e) {
                // swallow
            }

        }
    }


    private void parseTimelineQuery(ContentHandler handler, Metadata metadata, ResultSet rs, ResultSetMetaData columnNames) throws IOException, SAXException, TikaException, SQLException {

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

        int columnCount = columnNames.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            xHandler.startElement("th"); 
            xHandler.characters(columnNames.getColumnName(i)); 
            xHandler.endElement("th"); 
        }

        xHandler.endElement("tr"); 


        while (rs.next()) {
            xHandler.startElement("tr"); 
            for (int i = 1; i <= columnCount; i++) {
                xHandler.startElement("td"); 
                xHandler.characters(rs.getString(i));
                xHandler.endElement("td");                                        
            }
            xHandler.endElement("tr"); 
        }

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
        " datetime(ActivityOperation.StartTime, 'unixepoch', 'localtime') as 'StartTime',  " +
        " datetime(ActivityOperation.LastModifiedTime, 'unixepoch', 'localtime') as 'LastModified', " +
        " case  " +
        " when ActivityOperation.OriginalLastModifiedOnClient > 0  " +
        " then datetime(ActivityOperation.OriginalLastModifiedOnClient, 'unixepoch', 'localtime')  " +
        " else ''  " +
        " end as 'LastModifiedOnClient', " +
        " case  " +
        " when ActivityOperation.EndTime > 0  " +
        " then datetime(ActivityOperation.EndTime, 'unixepoch', 'localtime')  " +
        " else ''  " +
        " end as 'EndTime', " +
        " case  " +
        " when ActivityOperation.CreatedInCloud > 0  " +
        " then datetime(ActivityOperation.CreatedInCloud, 'unixepoch', 'localtime')  " +
        " else ''  " +
        " end as 'CreatedInCloud', " +
        " cast((ActivityOperation.ExpirationTime - ActivityOperation.LastModifiedTime) as integer) / '86400' as 'Expires In days', " +
        " datetime(Activity_PackageId.ExpirationTime, 'unixepoch', 'localtime') as 'Expiration on PackageID', " +
        " datetime(ActivityOperation.ExpirationTime, 'unixepoch', 'localtime') as 'Expiration', " +
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
        " datetime(Activity.StartTime, 'unixepoch', 'localtime') as 'StartTime', " +
        " datetime(Activity.LastModifiedTime, 'unixepoch', 'localtime') as 'LastModified', " +
        " case  " +
        " when Activity.OriginalLastModifiedOnClient > 0  " +
        " then datetime(Activity.OriginalLastModifiedOnClient, 'unixepoch', 'localtime')  " +
        " else '  -  '  " +
        " end as 'LastModifiedOnClient', " +
        " case  " +
        " when Activity.EndTime > 0  " +
        " then datetime(Activity.EndTime, 'unixepoch', 'localtime')  " +
        " else '-' " +
        " end as 'EndTime', " +
        " case  " +
        " when Activity.CreatedInCloud > 0  " +
        " then datetime(Activity.CreatedInCloud, 'unixepoch', 'localtime')  " +
        " else '-' " +
        " end as 'CreatedInCloud', " +
        " cast((Activity.ExpirationTime - Activity.LastModifiedTime) as integer) / '86400' as 'Expires In days', " +
        " datetime(Activity_PackageId.ExpirationTime, 'unixepoch', 'localtime') as 'Expiration on PackageID', " +
        " datetime(Activity.ExpirationTime, 'unixepoch', 'localtime') as 'Expiration', " +
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