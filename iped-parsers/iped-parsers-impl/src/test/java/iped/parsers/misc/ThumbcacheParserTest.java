package iped.parsers.misc;

import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

public class ThumbcacheParserTest extends TestCase {


    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testThumbcacheParserTest() throws Exception {
        ThumbcacheParser parser = new ThumbcacheParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_Thumbcache.db")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();



        }
    }
}
