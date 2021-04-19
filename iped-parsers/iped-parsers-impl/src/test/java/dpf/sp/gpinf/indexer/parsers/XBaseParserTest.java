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

        InputStream stream = getStream("test-files/testDBF.dbf");
        XBaseParser parser = new XBaseParser();
        ParseContext context = new ParseContext();
        BodyContentHandler handler = new BodyContentHandler(); 
        Metadata metadata = new Metadata();
        context.set(Parser.class, parser);
        parser.parse(stream, handler, metadata, context);
        
        String fileContent = handler.toString();
        assertTrue(fileContent.contains("Kauai Dive"));
        assertTrue(fileContent.contains("4-976"));
        assertTrue(fileContent.contains("Sugarloaf"));
        assertTrue(fileContent.contains("Hwy"));
        assertTrue(fileContent.contains("Kapaa"));
        assertTrue(fileContent.contains("HI"));
        assertTrue(fileContent.contains("94766"));
        assertTrue(fileContent.contains("U.S.A."));
        assertTrue(fileContent.contains("808-555-0269"));
        assertTrue(fileContent.contains("6215.0"));
        assertTrue(fileContent.contains("Underwater"));
        assertTrue(fileContent.contains("SCUBA Company"));
        assertTrue(fileContent.contains("PO"));
        assertTrue(fileContent.contains("B"));

    }

    @Test
    public void XbaseParserTestContentNonAscii() throws IOException, SAXException, TikaException {

        InputStream stream = getStream("test-files/testDBF.dbf");
        XBaseParser parser = new XBaseParser();
        ParseContext context = new ParseContext();
        BodyContentHandler handler = new BodyContentHandler(); 
        Metadata metadata = new Metadata();
        context.set(Parser.class, parser);
        parser.parse(stream, handler, metadata, context);
        
        String fileContent = handler.toString();
        assertTrue(fileContent.contains("Archimède et Lius à Châteauneu"));


    }
    
}
