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

public class GDriveCloudGraphParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testGDriveCloudGraphParsing() throws IOException, SAXException, TikaException{

        GDriveCloudGraphParser parser = new GDriveCloudGraphParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-gdrive-cloud-graph").toString());
        ContentHandler handler = new BodyContentHandler(10000000);
        InputStream stream = getStream("test-files/test_cloudGraph.db");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        
        assertTrue(hts.contains("Google Drive.lnk"));
        assertTrue(hts.contains("12jDbJlY6dA7uqMuiQAEDOD43QCKz3Ird"));
        assertTrue(hts.contains("testRFC822_quoted"));
        assertTrue(hts.contains("testRFC822-multipart"));
        assertTrue(hts.contains("Md5Checker.exe"));
     
    }
}
