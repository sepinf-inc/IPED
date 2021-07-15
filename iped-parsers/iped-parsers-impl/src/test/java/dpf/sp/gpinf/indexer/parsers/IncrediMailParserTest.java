package dpf.sp.gpinf.indexer.parsers;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


public class IncrediMailParserTest  extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testIncrediMailParser() throws IOException, SAXException, TikaException{

        IncrediMailParser parser = new IncrediMailParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_inBox.imm");
        parser.getSupportedTypes(oleContext);
        parser.parse(stream, handler, metadata, oleContext);
        
        assertEquals(373, oletracker.documentfolder.size());
        assertEquals("Welcome to IncrediMail!", oletracker.documentfolder.get(0));
        assertEquals("Bem-vindo ao Outlook Express 6", oletracker.documentfolder.get(1));
        assertEquals("2 items from your Steam wishlist are on sale!", oletracker.documentfolder.get(2));
        assertEquals("Colocando tranquilidade no caos do dia a dia!", oletracker.documentfolder.get(3));
        assertEquals("5 blogs que valem a pena seguir", oletracker.documentfolder.get(4));
        assertEquals("[cic-bcc-l] Fwd: [cic-secretaria-l] ESTÁGIO - SELEÇÃO", oletracker.documentfolder.get(371));
        assertEquals("Os 5 blogs do seu futuro", oletracker.documentfolder.get(372));
        
        
    }
    
}