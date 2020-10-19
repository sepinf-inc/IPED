package dpf.inc.sepinf.gdrive.parsers;

import java.io.Closeable;
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
 * Parser for snapshot.db Google Drive forensic artifact
 * This artifact contains information about the files that have been synced with the user’s Google Drive account.
 * Query adapted from https://github.com/kacos2000/Queries/blob/master/GDrive_snapshot.sql
 *  
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class GDriveSnapshotParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType GDRIVE_SNAPSHOT = MediaType.application("x-gdrive-snapshot");

    public static final MediaType GDRIVE_SNAPSHOT_REG = MediaType.application("x-gdrive-snapshot-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(GDRIVE_SNAPSHOT);

    // Option to extract each registry as a sub item.
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
     * Main Parser Method - Google Drive Cloud Graph - SNAPSHOT.db
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        // Set Connection
        Connection connection = getConnection(stream, metadata, context);

        // Run Query and Obtain the Snapshot entry iterable
        try (SnapshotEntryIterable entries = runQuery(connection)) {

            XHTMLContentHandler xHtmlOuput = startWriteSnapshotEntries(handler, metadata);

            int i = 0;
            for (SnapshotEntry entry : entries) {

                emitSnapshotEntry(xHtmlOuput, entry);

                /**
                 * Optionally extract entries as subitems
                 */
                if (extractEntries) {
                    Metadata metadataSnapshotItem = getEntryMetadata(entry, i++);
                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataSnapshotItem, true);
                }

            }

            endSnapshotEntries(xHtmlOuput);

        } catch (Exception e) {

            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite Gdrive Snapshot parsing exception", e);

        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (Exception e) {
                // swallow
            }

        }
    }

    private Metadata getEntryMetadata(SnapshotEntry entry, int i) throws ParseException {

        Metadata metadataSnapshotItem = new Metadata();

        metadataSnapshotItem.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, GDRIVE_SNAPSHOT_REG.toString());
        metadataSnapshotItem.add(Metadata.RESOURCE_NAME_KEY, "GDrive Snapshot Entry " + i);

        // These properties need to get a "Date" type as parameters, so it can correctly
        // show times in UTC
        metadataSnapshotItem.set(TikaCoreProperties.MODIFIED, GDriveCloudGraphParser.convertStringToDate(entry.getModified()));
        
        //.add(TikaCoreProperties.TITLE, h.getTitle());
        metadataSnapshotItem.add((BasicProps.HASH), "");

        // Snapshot data
        metadataSnapshotItem.add("acl_role", entry.getAclRole());
        metadataSnapshotItem.add("doc_type", entry.getDocType());
        metadataSnapshotItem.add("parent", entry.getParent());
        metadataSnapshotItem.add("filename", entry.getFilename());
        metadataSnapshotItem.set("md5_hash", entry.getMd5());
        metadataSnapshotItem.add("cloud_size", entry.getCloudSize());
        metadataSnapshotItem.add("original_size", entry.getOriginalSize());
        metadataSnapshotItem.add("removed", entry.getRemoved());
        metadataSnapshotItem.add("is_shared", entry.getShared());
        metadataSnapshotItem.add("is_folder", entry.getIsFolder());
        metadataSnapshotItem.add("local_parent", entry.getLocalParent());
        metadataSnapshotItem.add("local_filename", entry.getLocalFilename());
        metadataSnapshotItem.add("local_size", entry.getLocalSize());
        metadataSnapshotItem.add("local_md5", entry.getLocalMd5());
        metadataSnapshotItem.add("local_modified", entry.getLocalModified());
        metadataSnapshotItem.add("md5_check", entry.getMd5Check());
        metadataSnapshotItem.add("cloud_local_dates_check", entry.getCloudLocalDatesCheck());
        metadataSnapshotItem.add("volume", entry.getVolume());
        metadataSnapshotItem.add("child_volume", entry.getChildVolume());
        metadataSnapshotItem.add("parent_volume", entry.getParentVolume());
        

        return metadataSnapshotItem;
    }

    private interface SnapshotEntryIterable extends Iterable<SnapshotEntry>, Closeable {
    }

    private SnapshotEntryIterable runQuery(Connection connection) throws SQLException {

        return new SnapshotEntryIterable() {

            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(getGDriveSnapshotQuery(connection));

            @Override
            public Iterator<SnapshotEntry> iterator() {

                return new Iterator<SnapshotEntry>() {

                    @Override
                    public boolean hasNext() {
                        try {
                            return rs.next();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public SnapshotEntry next() {

                    	SnapshotEntry entry = new SnapshotEntry();

                        try {
                        	
                            entry.setAclRole(rs.getString("acl_role"));
                            entry.setDocType(rs.getString("doc_type"));
                            entry.setParent(rs.getString("parent"));
                            entry.setFilename(rs.getString("filename"));
                            entry.setMd5(rs.getString("md5"));
                            entry.setModified(rs.getString("modified"));
                            entry.setCloudSize(rs.getString("cloud_size"));
                            entry.setRemoved(rs.getString("removed"));
                            entry.setShared(rs.getString("is_shared"));
                            entry.setIsFolder(rs.getString("is_folder"));
                            entry.setLocalParent(rs.getString("local_parent"));
                            entry.setLocalFilename(rs.getString("local_filename"));
                            entry.setLocalSize(rs.getString("local_size"));
                            entry.setLocalMd5(rs.getString("local_md5"));
                            entry.setLocalModified(rs.getString("local_modified"));
                            entry.setMd5Check(rs.getString("md5_check"));
                            entry.setCloudLocalDatesCheck(rs.getString("cloud_local_dates_check"));
                            
                            entry.setOriginalSize(getStringIfExists(rs, "original_size"));
                            entry.setVolume(getStringIfExists(rs, "volume"));
                            entry.setChildVolume(getStringIfExists(rs, "child_volume"));
                            entry.setParentVolume(getStringIfExists(rs, "parent_volume"));
                            
                        } catch (SQLException e) {
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
                    // swallow
                }
            }

        };

    }

    private XHTMLContentHandler startWriteSnapshotEntries(ContentHandler handler, Metadata metadata)
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
        xHandler.characters("Google Drive Snapshot registries");
        xHandler.endElement("h2");
        xHandler.startElement("br");
        xHandler.startElement("br");

        xHandler.startElement("table");

        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("Acl role");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Doc type");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Parent");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("filename");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Md5");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Modified(UTC)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Cloud size");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Original size");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Removed");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Is shared");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Is folder");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Local parent");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Local filename");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Local size");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Local md5");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Local modified");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Md5 check");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Cloud local dates check");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Volume");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Child volume");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Parent volume");
        xHandler.endElement("th");

        
        xHandler.endElement("tr");

        return xHandler;

    }

    private void emitSnapshotEntry(XHTMLContentHandler xHandler, SnapshotEntry entry) throws SAXException {

        xHandler.startElement("tr");

        xHandler.startElement("td");
        xHandler.characters(entry.getAclRole());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDocType());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getParent());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getFilename());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getMd5());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getModified());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getCloudSize());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getOriginalSize());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getRemoved());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getShared());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getIsFolder());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLocalParent());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLocalFilename());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLocalSize());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLocalMd5());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getLocalModified());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getMd5Check());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getCloudLocalDatesCheck());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getVolume());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getChildVolume());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getParentVolume());
        xHandler.endElement("td");
        

        xHandler.endElement("tr");

    }

    private void endSnapshotEntries(XHTMLContentHandler xHandler) throws SAXException {
        xHandler.endElement("table");
        xHandler.endDocument();
    }

    /**
     * SQLite query from
     * https://github.com/kacos2000/Queries/blob/master/GDrive_snapshot.sql
     */
    private String getGDriveSnapshotQuery(Connection connection) {
        boolean originalSizeExists = checkIfColumnExists(connection, "cloud_entry", "original_size");
        boolean volumeExists = checkIfColumnExists(connection, "local_entry", "volume");
        String inode = "inode";
        String parent_inode = "parent_inode";
        if(!checkIfColumnExists(connection, "local_entry", "inode")) {
            inode += "_number";
            parent_inode += "_number";
        }
        return " select  "
    		+ " 	case cloud_entry.acl_role "
    		+ " 		when 2 then 'Can View' "
    		+ " 		when 1 then 'Can Contribute' "
    		+ " 		when 0 then 'Private' "
    		+ " 	else cloud_entry.acl_role  "
    		+ " 	end as acl_role, "
    		+ " 	case cloud_entry.doc_type "
    		+ " 		when 0 then 'Folder' "
    		+ " 		when 1 then 'File' "
    		+ " 		when 4 then 'Google Spreadsheet' "
    		+ " 		when 6 then 'Google Document' "
    		+ " 		when 12 then 'Google My Maps' "
    		+ " 		else cloud_entry.doc_type "
    		+ " 	end as doc_type, "
    		+ " 	(select filename from cloud_entry where cloud_entry.doc_id = cloud_relations.parent_doc_id) as 'parent', "
    		+ " 	cloud_entry.filename as 'filename', "
    		+ " 	cloud_entry.checksum as 'md5', "
    		+ " 	datetime(cloud_entry.modified, 'unixepoch') as 'modified', "
    		+ " 	cloud_entry.size as 'cloud_size', "
            + (originalSizeExists ? " cloud_entry.original_size as 'original_size', " : "")
    		+ " 	case cloud_entry.removed  "
    		+ " 		when 0 then 'No'  "
    		+ " 		when 1 then 'Yes'  "
    		+ " 	end as 'removed' , "
    		+ " 	case cloud_entry.shared  "
    		+ " 		when 0 then 'No'  "
    		+ " 		when 1 then 'Yes'  "
    		+ " 	end as 'is_shared', "
    		+ " 	case local_entry.is_folder  "
    		+ " 		when 0 then 'No'  "
    		+ " 		when 1 then 'Yes'  "
    		+ " 	end as 'is_folder', "
    		+ " 	(select filename from local_entry where local_entry." + inode + " = local_relations." + parent_inode + ") as 'local_parent', "
    		+ " 	local_entry.filename as 'local_filename', "
    		+ " 	local_entry.size as 'local_size', "
    		+ " 	local_entry.checksum as 'local_md5', "
    		+ " 	datetime(local_entry.modified, 'unixepoch') as 'local_modified', "
    		+ " 	case  "
    		+ " 		when cloud_entry.checksum = local_entry.checksum  "
    		+ " 		then 'MD5 Match'  "
    		+ " 		else (case  "
    		+ " 				when cloud_entry.checksum notnull  "
    		+ " 				then 'No_Match' "
    		+ " 				else ''  "
    		+ " 			  end)  "
    		+ " 	end as 'md5_check', "
    		+ " 	case  "
    		+ " 		when cloud_entry.modified = local_entry.modified  "
    		+ " 		then 'Dates Match'  "
    		+ " 		else 'No Match'  "
    		+ " 	end as 'cloud_local_dates_check' "
    		+ (volumeExists ? ", case  "
    		+ " 		when local_entry.volume = volume_info.volume  "
    		+ " 		then volume_info.full_path||' - '||volume_info.device_type||' - Volume Name: ('||volume_info.label||')'  "
    		+ " 		else local_entry.volume  "
    		+ " 	end as 'volume', "
    		+ " 	local_relations.child_volume as 'child_volume', "
    		+ " 	local_relations.parent_volume as 'parent_volume' "
    		 : "")
    		+ "  "
    		+ " from cloud_entry "
    		+ " join mapping on cloud_entry.doc_id = mapping.doc_id "
    		+ " join cloud_relations on cloud_entry.doc_id = cloud_relations.child_doc_id "
    		+ " join local_entry on local_entry." + inode + " = mapping." + inode
    		+ " join local_relations on local_relations.child_" + inode + " = local_entry." + inode
    		+ (volumeExists ? " left join volume_info on volume_info.volume = local_entry.volume " : "")
    		+ " --order by local_modified desc ";
    }
}