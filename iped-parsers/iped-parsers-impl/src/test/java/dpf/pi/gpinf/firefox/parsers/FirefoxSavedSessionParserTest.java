package dpf.pi.gpinf.firefox.parsers;

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

public class FirefoxSavedSessionParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testFirefoxSavedSessionParsing() throws IOException, SAXException, TikaException{

        FirefoxSavedSessionParser parser = new FirefoxSavedSessionParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sessionstore.jsonlz4");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        assertTrue(hts.contains("https://github.com/streeg/"));
        assertTrue(hts.contains("https://www.wikipedia.org/"));
        assertTrue(hts.contains("https://www.facebook.com/"));
        assertTrue(hts.contains("https://www.reddit.com/"));
        assertTrue(hts.contains("https://twitter.com/"));
        assertTrue(hts.contains(".wikipedia.org"));
        assertTrue(hts.contains("BR:DF:Bras__lia:-15.78:-47.93:v4"));
        assertTrue(hts.contains(".github.com"));
        assertTrue(hts.contains("America%2FSao_Paulo"));
        
    }
}
