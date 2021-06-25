package dpf.mg.udi.gpinf.shareazaparser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.util.ExtraProperties;


public class ShareazaLibraryDatParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testShareazaLibrary1DatParser() throws IOException, SAXException, TikaException{

        ShareazaLibraryDatParser parser = new ShareazaLibraryDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_shareazaLibrary1.dat");
        parser.getSupportedTypes(shareazalibdatContext);
        parser.parse(stream, handler, metadata, shareazalibdatContext);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Cosmos Laundromat.torrent"));
        assertTrue(hts.contains("Termo-de-compromisso-de-estgio-nao-obrigatorio-2.odt"));
        assertTrue(hts.contains("Licor de Banana.png"));
        assertTrue(hts.contains("ImprimeGRUSimples.pdf"));
        assertTrue(hts.contains("Michael Jackson - Bad.mp3"));
        
        assertEquals("application/x-shareaza-library-dat", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("127", metadata.get(ExtraProperties.P2P_REGISTRY_COUNT));
//        assertEquals("sharedHashes=7889fa596625fc06972db9c24fcd84b6", shareazalibdattracker.sharedhashes.get(0));
        assertTrue(mts.contains("sharedHashes=7889fa596625fc06972db9c24fcd84b6"));
        assertTrue(mts.contains("sharedHashes=3f4d4e6f4e6d7618d61d238754937a43"));
        assertTrue(mts.contains("sharedHashes=5ff811cbb56fa306f01aca1890f1a70a"));
        
    }
 
    @Test
    public void testShareazaLibrary2DatParser() throws IOException, SAXException, TikaException{

        ShareazaLibraryDatParser parser = new ShareazaLibraryDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToTextContentHandler();
        InputStream stream = getStream("test-files/test_shareazaLibrary2.dat");
        parser.getSupportedTypes(shareazalibdatContext);
        parser.parse(stream, handler, metadata, shareazalibdatContext);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("High and Dry.mp3"));
        assertTrue(hts.contains("abacaxihortela.png"));
        assertTrue(hts.contains("857b8b7d09e96d26d23fe5df3492c07a57de373a"));
        assertTrue(hts.contains("Guilherme Andreuce.pdf"));
        assertTrue(hts.contains("Big Buck Bunny.torrent"));
        
        assertTrue(mts.contains("sharedHashes=5ff811cbb56fa306f01aca1890f1a70a"));
        assertTrue(mts.contains("Content-Type=application/x-shareaza-library-dat"));
        assertTrue(mts.contains("p2pHistoryEntries=127"));
        assertTrue(mts.contains("sharedHashes=7889fa596625fc06972db9c24fcd84b6"));
        assertTrue(mts.contains("sharedHashes=752a3365fd0606fcf940fc669514fd12"));
        
    }    

}
