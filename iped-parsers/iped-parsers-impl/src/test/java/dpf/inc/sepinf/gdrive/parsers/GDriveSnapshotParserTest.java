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

public class GDriveSnapshotParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    // Main Parser Method - Google Drive Cloud Graph - SNAPSHOT.db
    @Test
    public void testGDriveSnapshotParsing() throws IOException, SAXException, TikaException{

        GDriveSnapshotParser parser = new GDriveSnapshotParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-snapshot").toString());
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_snapshot.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        
        assertTrue(hts.contains("Segurança Computacional CIC"));
        assertTrue(hts.contains("Cheat Engine.lnk"));
        assertTrue(hts.contains("testRFC822_quoted"));
        assertTrue(hts.contains("mockrar5.rar"));
        assertTrue(hts.contains("a4tosticker9.png"));
        
    }

}
