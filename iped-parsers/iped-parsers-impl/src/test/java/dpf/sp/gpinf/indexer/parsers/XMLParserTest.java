package dpf.sp.gpinf.indexer.parsers;

import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import junit.framework.TestCase;

public class XMLParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testXMLParserAsciiChars() throws Exception {

            XMLParser parser = new XMLParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream("test-files/test_xml.xml");
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            
            String hts = handler.toString();
            assertTrue(hts.contains("<dc:title>Tika test document</dc:title>"));
            assertTrue(hts.contains("<dc:creator>Rida Benjelloun</dc:creator>"));
            assertTrue(hts.contains("<dc:subject>Java</dc:subject>"));
            assertTrue(hts.contains("<dc:subject>XML</dc:subject>"));
            assertTrue(hts.contains("<dc:subject>XSLT</dc:subject>"));
            assertTrue(hts.contains("<dc:subject>JDOM</dc:subject>"));
            assertTrue(hts.contains("<dc:subject>Indexation</dc:subject>"));
            assertTrue(hts.contains("<dc:description>Framework d'indexation des documents XML, HTML, PDF etc.. </dc:description>"));
            assertTrue(hts.contains("<dc:identifier>http://www.apache.org</dc:identifier>"));
            assertTrue(hts.contains("<dc:date>2000-12-01T00:00:00.000Z</dc:date>"));
            assertTrue(hts.contains("<dc:type>test</dc:type>"));           
            assertTrue(hts.contains("<dc:format>application/msword</dc:format>"));
            assertTrue(hts.contains("<dc:language>Fr</dc:language>"));
            assertTrue(hts.contains("<dc:rights>Archimède et Lius à Châteauneuf testing chars en été</dc:rights>"));
            stream.close();
    }
    
    @Test
    public void testXMLParserNonAsciiChars() throws Exception {
        InputStream stream = getStream("test-files/test_xml.xml");
        Metadata metadata = new Metadata();
        XMLParser parser = new XMLParser();
        ParseContext context = new ParseContext();
        ContentHandler handler = new BodyContentHandler();

        parser.parse(stream, handler, metadata, context);    
        final String expected = "Archim\u00E8de et Lius \u00E0 Ch\u00E2teauneuf testing chars en \u00E9t\u00E9";
        String hts = handler.toString();
        assertTrue(hts.contains(expected));
        stream.close();
        
    }

}
