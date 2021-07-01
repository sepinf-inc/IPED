package gpinf.emule;

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

public class PreferencesDatParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testKnownMetParsing() throws IOException, SAXException, TikaException{

        PreferencesDatParser parser = new PreferencesDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_preferences.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("20"));
        assertTrue(hts.contains("02237df8aa0e92ae78f7bfe1d79a6fb2"));

        

        
    }

    @Test
    public void testKnownMetEmbedded() throws IOException, SAXException, TikaException{

        PreferencesDatParser parser = new PreferencesDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_preferences.dat");
        ParseContext context = new ParseContext();
        parser.parse(stream, handler, metadata, context);
        String mts = metadata.toString();
        System.out.println(mts);
        

    }
}
