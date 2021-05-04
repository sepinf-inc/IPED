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

import iped3.util.ExtraProperties;
import junit.framework.TestCase;

public class AresParserTest extends TestCase{



    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testAresParserParsingShareL() throws IOException, SAXException, TikaException{

        AresParser parser = new AresParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/testShareL.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        assertTrue(hts.contains("mocktext2"));
        assertTrue(hts.contains("mocktext5"));
        assertTrue(hts.contains("mocktext1"));

        assertTrue(hts.contains("c:\\Users\\guilh\\OneDrive\\Área de Trabalho\\PF\\test-files\\cpio\\mocktext2.txt"));
        assertTrue(hts.contains("c:\\Users\\guilh\\OneDrive\\Área de Trabalho\\PF\\test-files\\cpio\\mockfolder\\mocktext5.txt"));
        assertTrue(hts.contains("c:\\Users\\guilh\\OneDrive\\Área de Trabalho\\PF\\test-files\\cpio\\mocktext1.txt"));
    }
    
    
    public void testAresParserEmbeddedShareL() throws IOException, SAXException, TikaException{

        AresParser parser = new AresParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/testShareL.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        assertEquals("4", metadata.get(ExtraProperties.P2P_REGISTRY_COUNT));
        assertEquals("3caf8ead5f326604bfbc7eae5274a15fb7840d08", metadata.get(ExtraProperties.SHARED_HASHES));
        
        
        
    }
    
    public void testAresParserParsingShareH() throws IOException, SAXException, TikaException{

        AresParser parser = new AresParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/testShareH.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        assertTrue(hts.contains("Undisclosed Desires"));
        assertTrue(hts.contains("8a199330e3882ff26a4b"));
        
    }
    
    public void testAresParserEmbeddedShareH() throws IOException, SAXException, TikaException{

        AresParser parser = new AresParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/testShareH.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        assertEquals("1", metadata.get(ExtraProperties.P2P_REGISTRY_COUNT));
        assertEquals("8a199330e3882ff26a4b02e8285f5a9dc20d1ef1", metadata.get(ExtraProperties.SHARED_HASHES));
        
        
        
    }
}
