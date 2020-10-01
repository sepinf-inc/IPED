package dpf.inc.sepinf.gdrive.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;

/**
 * Google Drive Forensic artifacts main parser
 * This class selects the appropriate parser for the current artifact
 * 
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class GDriveMainParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType GDRIVE_CLOUD_GRAPH = MediaType.application("x-gdrive-cloud-graph");
    public static final MediaType GDRIVE_SNAPSHOT = MediaType.application("x-gdrive-snapshot");
    
    public static final MediaType GDRIVE_ACCOUNT_INFO = MediaType.application("x-gdrive-account-info");
    

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(GDRIVE_CLOUD_GRAPH, GDRIVE_SNAPSHOT, GDRIVE_ACCOUNT_INFO);

    // Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();
    
    // Option to extract each registry as a sub item.
    private boolean extractEntries = true;
    
    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    /**
     * Main Parser Method - Checks content type and calls appropriate parser
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
    	
    	try {		
	    	if(metadata.get(Metadata.CONTENT_TYPE).equals(GDRIVE_CLOUD_GRAPH.toString())) { 
	    		//Parser for cloud_graph.db
	    		GDriveCloudGraphParser gDriveCloudGraphParser = new GDriveCloudGraphParser();
	    		gDriveCloudGraphParser.setExtractEntries(extractEntries);
	    		gDriveCloudGraphParser.parse(stream, handler, metadata, context);
	    	}
	    	else if(metadata.get(Metadata.CONTENT_TYPE).equals(GDRIVE_SNAPSHOT.toString())) {
	    		//Parser for snapshot.db
	    		GDriveSnapshotParser gDriveSnapshotParser = new GDriveSnapshotParser();
	    		gDriveSnapshotParser.setExtractEntries(extractEntries);
	    		gDriveSnapshotParser.parse(stream, handler, metadata, context);
	    	} 
	    	else if(metadata.get(Metadata.CONTENT_TYPE).equals(GDRIVE_ACCOUNT_INFO.toString())) {
	    		//Using defalt SQLite parser for sync_config.db and global.db
	    		//Create an specific parser?
	    		sqliteParser.parse(stream, handler, metadata, context);
	    	} 
	    	else {
	    		//Fallback
	    		sqliteParser.parse(stream, handler, metadata, context);	
	    	}
	    	

        } catch (Exception e) {

            throw new TikaException("SQLite GDrive parsing exception", e);

        }
    }


}