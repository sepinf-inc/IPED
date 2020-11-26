package dpf.inc.sepinf.gdrive.parsers;

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
import java.util.List;
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
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser for cloud_graph.db Google Drive forensic artifact
 * This artifact contains information about the files that have been synced with the user’s Google Drive account.
 * Query adapted from https://github.com/kacos2000/Queries/blob/master/GDrive_cloudgraph.sql
 * 
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class GDriveCloudGraphParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType GDRIVE_CLOUD_GRAPH = MediaType.application("x-gdrive-cloud-graph");

    public static final MediaType GDRIVE_CLOUD_GRAPH_REG = MediaType.application("x-gdrive-cloud-graph-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(GDRIVE_CLOUD_GRAPH);

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

    // Conversion from Datetime Format String to Date
    static Date convertStringToDate(String datetime) throws ParseException {
        if (datetime == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(datetime);
    }

    /**
     * Main Parser Method - Google Drive Cloud Graph - cloud_graph.db
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        // Set Connection
        Connection connection = getConnection(stream, metadata, context);

        // Run Query and Obtain the CloudGraph entry iterable
        try (CloudGraphEntryIterable entries = runQuery(connection)) {

            XHTMLContentHandler xHtmlOuput = startWriteCloudGraphEntries(handler, metadata);

            int i = 0;
            for (CloudGraphEntry entry : entries) {
                
                List<String> hashSets = ChildPornHashLookup.lookupHash(entry.getMd5());

                emitCloudGraphEntry(xHtmlOuput, entry, hashSets);

                /**
                 * Optionally extract entries as subitems
                 */
                if (extractEntries) {
                    Metadata metadataCloudGraphItem = getEntryMetadata(entry, i++, hashSets);
                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataCloudGraphItem, true);
                }

            }

            endCloudGraphEntries(xHtmlOuput);

        } catch (Exception e) {

            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite Gdrive CloudGraph parsing exception", e);

        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (Exception e) {
                // swallow
            }

        }
    }

    private Metadata getEntryMetadata(CloudGraphEntry entry, int i, List<String> hashSets) throws ParseException {

        Metadata metadataCloudGraphItem = new Metadata();

        metadataCloudGraphItem.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, GDRIVE_CLOUD_GRAPH_REG.toString());
        metadataCloudGraphItem.add(Metadata.RESOURCE_NAME_KEY, "GDrive CloudGraph Entry " + i);

        // These properties need to get a "Date" type as parameters, so it can correctly
        // show times in UTC
        metadataCloudGraphItem.set(TikaCoreProperties.MODIFIED, convertStringToDate(entry.getModified()));
        
        metadataCloudGraphItem.add((BasicProps.HASH), "");

        // CloudGraph data
        metadataCloudGraphItem.add("parent", entry.getParent());
        metadataCloudGraphItem.add("filename", entry.getFilename());
        metadataCloudGraphItem.add("size_bytes", entry.getSize());
        metadataCloudGraphItem.set("md5_hash", entry.getMd5());
        metadataCloudGraphItem.add("doc_type", entry.getDoc_type());
        metadataCloudGraphItem.add("is_shared", entry.getShared());
        metadataCloudGraphItem.add("version", entry.getVersion());
        metadataCloudGraphItem.add("acl_role", entry.getAcl_role());
        metadataCloudGraphItem.add("download_restricted", entry.getDownload_restricted());
        metadataCloudGraphItem.add("photos_storage_policy", entry.getPhotos_storage_policy());
        metadataCloudGraphItem.add("down_sample_status", entry.getDown_sample_status());
        metadataCloudGraphItem.add("doc_id", entry.getDoc_id());
        metadataCloudGraphItem.add("parent_doc_id", entry.getParent_doc_id());
        if (!hashSets.isEmpty()) {
            metadataCloudGraphItem.set("kffstatus", "pedo");
            for (String set : hashSets) {
                metadataCloudGraphItem.add("kffgroup", set);
            }
        }
        if("yes".equalsIgnoreCase(entry.getShared()) || Boolean.valueOf(entry.getShared())) {
            metadataCloudGraphItem.set(ExtraProperties.SHARED_HASHES, entry.getMd5());
        }

        return metadataCloudGraphItem;
    }

    private interface CloudGraphEntryIterable extends Iterable<CloudGraphEntry>, Closeable {
    }

    private CloudGraphEntryIterable runQuery(Connection connection) throws SQLException {

        return new CloudGraphEntryIterable() {

            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(getGDriveCloudGraphQuery(connection));

            @Override
            public Iterator<CloudGraphEntry> iterator() {

                return new Iterator<CloudGraphEntry>() {

                    @Override
                    public boolean hasNext() {
                        try {
                            return rs.next();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public CloudGraphEntry next() {

                    	CloudGraphEntry entry = new CloudGraphEntry();

                        try {
                        	
                            entry.setParent(rs.getString("parent"));
                            entry.setFilename(rs.getString("filename"));
                            entry.setSize(rs.getString("size"));
                            entry.setMd5(rs.getString("md5"));
                            entry.setDoc_type(rs.getString("doc_type"));
                            entry.setShared(rs.getString("shared"));
                            entry.setModified(rs.getString("modified"));
                            entry.setVersion(rs.getString("version"));
                            entry.setAcl_role(rs.getString("acl_role"));
                            entry.setDownload_restricted(rs.getString("download_restricted"));
                            entry.setDown_sample_status(rs.getString("down_sample_status"));
                            entry.setDoc_id(rs.getString("doc_id"));
                            entry.setParent_doc_id(rs.getString("parent_doc_id"));
                            
                            entry.setPhotos_storage_policy(getStringIfExists(rs, "photos_storage_policy"));

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

    private XHTMLContentHandler startWriteCloudGraphEntries(ContentHandler handler, Metadata metadata)
            throws SAXException {

        XHTMLContentHandler xHandler = null;

        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        xHandler.startElement("head");
        xHandler.startElement("style");
        xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}");
        xHandler.characters(".ra {vertical-align: middle;}");
        xHandler.characters(".rr {background-color:#E77770; vertical-align: middle;}");
        xHandler.endElement("style");
        xHandler.endElement("head");

        xHandler.startElement("h2 align=center");
        xHandler.characters("Google Drive CloudGraph registries");
        xHandler.endElement("h2");
        xHandler.newline();
        
        xHandler.startElement("p");
        xHandler.characters(Messages.getString("P2P.PedoHashHit"));
        xHandler.endElement("p");
        xHandler.newline();

        xHandler.startElement("table");

        xHandler.startElement("tr");

        xHandler.startElement("th");
        xHandler.characters("Parent");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Filename");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Size");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("MD5");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Doc Type");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Shared");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Modified(UTC)");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Version");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Acl Role");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Download Restricted");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Photos Storage Policy");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Down Sample Status");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Doc ID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Parent Doc ID");
        xHandler.endElement("th");
        xHandler.startElement("th");
        xHandler.characters("Found in Child Porn Alert Hashset");
        xHandler.endElement("th");

        xHandler.endElement("tr");

        return xHandler;

    }

    private void emitCloudGraphEntry(XHTMLContentHandler xHandler, CloudGraphEntry entry, List<String> hashSets)
            throws SAXException {

        String trClass = !hashSets.isEmpty() ? "rr" : "ra";
        xHandler.startElement("tr", "class", trClass);

        xHandler.startElement("td");
        xHandler.characters(entry.getParent());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getFilename());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getSize());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getMd5());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDoc_type());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getShared());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getModified());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getVersion());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getAcl_role());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDownload_restricted());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getPhotos_storage_policy());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDown_sample_status());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getDoc_id());
        xHandler.endElement("td");
        xHandler.startElement("td");
        xHandler.characters(entry.getParent_doc_id());
        xHandler.endElement("td");
        xHandler.startElement("td");
        if (!hashSets.isEmpty()) {
            xHandler.characters(hashSets.toString());
        }
        xHandler.endElement("td");
        

        xHandler.endElement("tr");

    }

    private void endCloudGraphEntries(XHTMLContentHandler xHandler) throws SAXException {
        xHandler.endElement("table");
        xHandler.endDocument();
    }

    /**
     * SQLite query from
     * https://github.com/kacos2000/Queries/blob/master/GDrive_cloudgraph.sql
     */

    private String getGDriveCloudGraphQuery(Connection connection){
        boolean col_exists = checkIfColumnExists(connection, "cloud_graph_entry", "photos_storage_policy");
        return
    		  " Select  "
    		+ " 	case  "
    		+ " 		when doc_id = 'root' then 'root' "
    		+ " 		when parent_doc_id = 'root' then 'root' "
    		+ " 		else (select filename from cloud_graph_entry where parent_doc_id = doc_id)  "
    		+ " 	end as parent, "
    		+ " 	filename, "
    		+ " 	size, "
    		+ " 	checksum as md5, "
    		+ " 	case doc_type "
    		+ " 		when 0 then 'Folder' "
    		+ " 		when 1 then 'File' "
    		+ " 		when 4 then 'Google Spreadsheet' "
    		+ " 		when 6 then 'Google Document' "
    		+ " 		when 12 then 'Google My Maps' "
    		+ " 		else doc_type "
    		+ " 	end as doc_type, "
    		+ " 	case shared "
    		+ " 		when 0 then '' "
    		+ " 		when 1 then 'Yes' "
    		+ " 	end as shared, "
    		+ " 	datetime(modified,'unixepoch') as modified, "
    		+ " 	version, "
    		+ " 	case acl_role "
    		+ " 		when 2 then 'Can View' "
    		+ " 		when 1 then 'Can Contribute' "
    		+ " 		when 0 then 'Private' "
    		+ " 		else acl_role  "
    		+ " 	end as acl_role, "
    		+ " 	case download_restricted "
    		+ " 		when 0 then '' "
    		+ " 		when 1 then 'Yes' "
    		+ " 		else download_restricted "
    		+ " 	end as download_restricted, "
    		+ (col_exists ? " photos_storage_policy, " : "")
    		+ " 	down_sample_status, "
    		+ " 	doc_id, "
    		+ " 	cloud_relations.parent_doc_id "
    		+ "  "
    		+ " From cloud_graph_entry "
    		+ " left join cloud_relations on cloud_graph_entry.doc_id = cloud_relations.child_doc_id "
    		+ " order by modified desc ";
    }
}