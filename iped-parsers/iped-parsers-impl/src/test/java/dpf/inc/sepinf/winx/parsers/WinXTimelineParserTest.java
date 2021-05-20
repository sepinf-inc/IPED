package dpf.inc.sepinf.winx.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import junit.framework.TestCase;

public class WinXTimelineParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    //Need a valid activities cache file. 
    @Test
    public void testWinXTimelineParser() throws IOException, SAXException, TikaException{

        WinXTimelineParser parser = new WinXTimelineParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_activitiesCache.db");
        ParseContext context = new ParseContext();
        parser.setExtractEntries(true);
        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
//        
//        String hts = handler.toString();
//        
//        System.out.println(hts);
     
    }

}
