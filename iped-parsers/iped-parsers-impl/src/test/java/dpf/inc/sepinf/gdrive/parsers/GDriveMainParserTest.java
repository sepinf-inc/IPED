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
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_cloudGraph.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-cloud-graph").toString());
       
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();

        assertTrue(hts.contains("Google Drive.lnk"));
        assertTrue(hts.contains("12jDbJlY6dA7uqMuiQAEDOD43QCKz3Ird"));
        assertTrue(hts.contains("testRFC822_quoted"));
        assertTrue(hts.contains("testRFC822-multipart"));
        assertTrue(hts.contains("Md5Checker.exe"));
        
        assertEquals("application/x-gdrive-cloud-graph", metadata.get(Metadata.CONTENT_TYPE));
     

    }
    
    @Test
    public void testGDriveMainSnapshotParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_snapshot.db");
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-snapshot").toString());
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();

        assertTrue(hts.contains("Segurança Computacional CIC"));
        assertTrue(hts.contains("Cheat Engine.lnk"));
        assertTrue(hts.contains("testRFC822_quoted"));
        assertTrue(hts.contains("mockrar5.rar"));
        assertTrue(hts.contains("a4tosticker9.png"));
        
        assertEquals("application/x-gdrive-snapshot", metadata.get(Metadata.CONTENT_TYPE));

    }
    
    @Test
    public void testGDriveMainAccInfoParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_global.db");
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-account-info").toString());
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("username_mapping"));
        assertTrue(hts.contains("2"));
        assertTrue(hts.contains("0"));
        assertTrue(hts.contains("global_preferences"));
        assertTrue(hts.contains("2"));
        assertTrue(hts.contains("3"));
        assertTrue(mts.contains("database:table_name=username_mapping database:table_name=global_preferences"));
        assertTrue(mts.contains("Content-Type=application/x-gdrive-account-info"));
        
    }
    
    @Test
    public void testGDriveMainSyncDbParsing() throws IOException, SAXException, TikaException{

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_syncConfig.db");
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-account-info").toString());
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("data"));
        assertTrue(hts.contains("3"));
        assertTrue(hts.contains("41"));
        assertTrue(mts.contains("database:table_name=data"));
        assertTrue(mts.contains("Content-Type=application/x-gdrive-account-info"));
        
    }
}
