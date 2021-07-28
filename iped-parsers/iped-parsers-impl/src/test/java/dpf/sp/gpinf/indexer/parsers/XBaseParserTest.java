package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.SAXException;

public class XBaseParserTest {
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void XbaseParserTestContent() throws IOException, SAXException, TikaException {

        InputStream stream = getStream("test-files/test_dbf.dbf");
        XBaseParser parser = new XBaseParser();
        ParseContext context = new ParseContext();
        BodyContentHandler handler = new BodyContentHandler(); 
        Metadata metadata = new Metadata();
        context.set(Parser.class, parser);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        assertTrue(hts.contains("Kauai Dive"));
        assertTrue(hts.contains("4-976"));
        assertTrue(hts.contains("Sugarloaf"));
        assertTrue(hts.contains("Hwy"));
        assertTrue(hts.contains("Kapaa"));
        assertTrue(hts.contains("HI"));
        assertTrue(hts.contains("94766"));
        assertTrue(hts.contains("U.S.A."));
        assertTrue(hts.contains("808-555-0269"));
        assertTrue(hts.contains("6215.0"));
        assertTrue(hts.contains("Underwater"));
        assertTrue(hts.contains("SCUBA Company"));
        assertTrue(hts.contains("PO"));
        assertTrue(hts.contains("B"));

    }

    @Test
    public void XbaseParserTestContentNonAscii() throws IOException, SAXException, TikaException {

        InputStream stream = getStream("test-files/test_dbf.dbf");
        XBaseParser parser = new XBaseParser();
        ParseContext context = new ParseContext();
        BodyContentHandler handler = new BodyContentHandler(); 
        Metadata metadata = new Metadata();
        context.set(Parser.class, parser);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        assertTrue(hts.contains("Archimède et Lius à Châteauneu"));


    }
    
}
