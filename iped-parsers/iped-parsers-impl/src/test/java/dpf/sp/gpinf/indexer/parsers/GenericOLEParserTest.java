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


public class GenericOLEParserTest extends AbstractPkgTest{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 

    @Test
    public void testGenericOLEParserParsing() throws IOException, SAXException, TikaException{

        GenericOLEParser parser = new GenericOLEParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockDoc1.doc");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, oleContext);        
        String hts = handler.toString();
        
        assertEquals(6, oletracker.documentfolder.size());
        assertEquals("WordDocument", oletracker.documentfolder.get(0));
        assertEquals("DocumentSummaryInformation", oletracker.documentfolder.get(1).substring(1));
        assertEquals("SummaryInformation", oletracker.documentfolder.get(2).substring(1));
        assertEquals("1Table", oletracker.documentfolder.get(3));
        assertEquals("CompObj", oletracker.documentfolder.get(4).substring(1));
        assertEquals("Data", oletracker.documentfolder.get(5));
        
        assertTrue(hts.contains("Mockdoc1"));
        assertTrue(hts.contains("Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
        assertTrue(hts.contains("Tabela normal"));
        assertTrue(hts.contains("[Content_Types].xml"));
        assertTrue(hts.contains("theme/theme/themeManager.xml"));
        assertTrue(hts.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(hts.contains("Guilherme Andreúce Sobreira Monteiro"));
        assertTrue(hts.contains("Microsoft Office Word"));
        assertTrue(hts.contains("Documento do Microsoft Office Word 97-2003"));
        
    }

}
