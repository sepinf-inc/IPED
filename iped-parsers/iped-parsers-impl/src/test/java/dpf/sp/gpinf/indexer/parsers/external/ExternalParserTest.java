package dpf.sp.gpinf.indexer.parsers.external;


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

public class ExternalParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 

    @Test
    public void testGenericOLEParserParsing() throws IOException, SAXException, TikaException{

        ExternalParser parser = new ExternalParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_pdfResumes.pdf");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try {
//        parser.parse(stream, handler, metadata, context);        
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
        }catch (Exception e) {
            System.out.println(e);
        }
    }

}
