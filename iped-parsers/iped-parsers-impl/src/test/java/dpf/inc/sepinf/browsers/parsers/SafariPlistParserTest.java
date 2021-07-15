package dpf.inc.sepinf.browsers.parsers;

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

public class SafariPlistParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testSafariPlistParser() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-plist").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.plist");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        try {
        parser.parse(stream, handler, metadata, safariContext);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
        } catch (Exception e) {
            System.out.println(e);
        }
        
    }

}
