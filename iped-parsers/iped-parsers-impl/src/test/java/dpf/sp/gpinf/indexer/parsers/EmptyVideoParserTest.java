package dpf.sp.gpinf.indexer.parsers;

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
import org.apache.tika.parser.mp4.ISO6709Converter;

public class EmptyVideoParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testEmptyVideoParserParsing() throws IOException, SAXException, TikaException{

        EmptyVideoParser parser = new EmptyVideoParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_videoMp4.mp4");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        ISO6709Converter converter = new ISO6709Converter();
        converter.populateLocation(metadata, getName());
        parser.parse(stream, handler, metadata, context);
           
    }

}
