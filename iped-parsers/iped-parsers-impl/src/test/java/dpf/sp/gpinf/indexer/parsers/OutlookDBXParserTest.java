package dpf.sp.gpinf.indexer.parsers;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class OutlookDBXParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testOutlookDBXParser() throws IOException, SAXException, TikaException{

        OutlookDBXParser parser = new OutlookDBXParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);

        try (InputStream stream = getStream("test-files/test_entryBox.dbx")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(368, tracker.subitemCount);
            // TODO: test embedded mails hash like done in MBoxParserTest
        }
    }
    
}