package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class LibpffPSTParserTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testLibpffPSTParser() throws IOException, SAXException, TikaException {

        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.setExtractOnlyActive(true);
        parser.setExtractOnlyDeleted(false);
        parser.getSupportedTypes(context);
        assumeFalse(parser.getSupportedTypes(context).isEmpty());
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, context);

	        String hts = handler.toString();
	        String mts = metadata.toString();
	
	        // TODO remove print below and test assertions
	        System.out.println(hts + "\n" + mts);
	        stream.close();
        }catch (Exception e) {
        	System.out.println(e);
        }
    }

}
