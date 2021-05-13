package dpf.inc.sepinf.gdrive.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import junit.framework.TestCase;

public class GDriveMainParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
  //This Parser tries multiple parsers for the following items:
  //Parser for cloud_graph.db
  //Parser for snapshot.db
  //Parser for sync_config.db and global.db
    
    @Test
    public void testGDriveMainGraphParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-gdrive-cloud-graph").toString());
        ContentHandler handler = new BodyContentHandler();
//        InputStream stream = getStream("test-files/test_cloudGraph.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);

    }
    
    @Test
    public void testGDriveMainSnapshotParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-gdrive-snapshot").toString());
        ContentHandler handler = new BodyContentHandler();
//        InputStream stream = getStream("test-files/test_snapshot.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);

    }
    
    @Test
    public void testGDriveMainAccInfoParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-gdrive-account-info").toString());
        ContentHandler handler = new BodyContentHandler();
//        InputStream stream = getStream("test-files/test_global.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);

    }
}
