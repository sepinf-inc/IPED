package dpf.sp.gpinf.indexer.parsers;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
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

            Parser parser = new XMLParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream("test-files/testXML.xml");
            parser.parse(stream, handler, metadata, context);
            String bodyText = handler.toString();
            assertTrue(bodyText.contains("<dc:title>Tika test document</dc:title>"));
            assertTrue(bodyText.contains("<dc:creator>Rida Benjelloun</dc:creator>"));
            assertTrue(bodyText.contains("<dc:subject>Java</dc:subject>"));
            assertTrue(bodyText.contains("<dc:subject>XML</dc:subject>"));
            assertTrue(bodyText.contains("<dc:subject>XSLT</dc:subject>"));
            assertTrue(bodyText.contains("<dc:subject>JDOM</dc:subject>"));
            assertTrue(bodyText.contains("<dc:subject>Indexation</dc:subject>"));
            assertTrue(bodyText.contains("<dc:description>Framework d'indexation des documents XML, HTML, PDF etc.. </dc:description>"));
            assertTrue(bodyText.contains("<dc:identifier>http://www.apache.org</dc:identifier>"));
            assertTrue(bodyText.contains("<dc:date>2000-12-01T00:00:00.000Z</dc:date>"));
            assertTrue(bodyText.contains("<dc:type>test</dc:type>"));           
            assertTrue(bodyText.contains("<dc:format>application/msword</dc:format>"));
            assertTrue(bodyText.contains("<dc:language>Fr</dc:language>"));
            assertTrue(bodyText.contains("<dc:rights>Archimède et Lius à Châteauneuf testing chars en été</dc:rights>"));
            stream.close();
    }
    
    @Test
    public void testXMLParserNonAsciiChars() throws Exception {
        InputStream stream = getStream("test-files/testXML.xml");
        Metadata metadata = new Metadata();
        Parser parser = new XMLParser();
        ParseContext context = new ParseContext();
        ContentHandler handler = new BodyContentHandler();

        parser.parse(stream, handler, metadata, context);    
        final String expected = "Archim\u00E8de et Lius \u00E0 Ch\u00E2teauneuf testing chars en \u00E9t\u00E9";
        String bodyText = handler.toString();
        assertTrue(bodyText.contains(expected));
        stream.close();
        
    }

}
