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
import junit.framework.TestCase;

public class LNKShortcutParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
        
        @Test
        public void testLNKShortcutParserParsingLink() throws IOException, SAXException, TikaException{

            LNKShortcutParser parser = new LNKShortcutParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            InputStream stream = getStream("test-files/test_lnk.lnk");
            ParseContext context = new ParseContext();
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            
            String hts = handler.toString();
//            assertTrue(hts.contains("Data de Criação"));
//            assertTrue(hts.contains("05/09/2020 02:04:27"));
            assertTrue(hts.contains("Caminho Local"));
            assertTrue(hts.contains("C:\\Users\\guilh\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe"));
            assertTrue(hts.contains("Root Folder Shell - CLSID"));
            assertTrue(hts.contains("59031a47-3f72-44a7-89c5-5595fe6b30ee"));
            assertTrue(hts.contains("Nome Primário"));
            assertTrue(hts.contains("Telegram.exe"));
            assertTrue(hts.contains("Nome Secundário"));
            assertTrue(hts.contains("Telegram Desktop"));
//            assertTrue(hts.contains("Data do Último Acesso"));
//            assertTrue(hts.contains("05/09/2020 02:04:30"));


           }
        
        
        @Test
        public void testLNKShortcutParserParsingLinkTracker() throws IOException, SAXException, TikaException{

            LNKShortcutParser parser = new LNKShortcutParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            InputStream stream = getStream("test-files/test_lnkLinkTracker.lnk");
            ParseContext context = new ParseContext();
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            
            String hts = handler.toString();
//            assertTrue(hts.contains("Data de Criação"));
//            assertTrue(hts.contains("31/03/2021 01:54:53"));
            assertTrue(hts.contains("Caminho Local"));
            assertTrue(hts.contains("C:\\Program Files (x86)\\PokerStars\\PokerStarsUpdate.exe"));
            assertTrue(hts.contains("Root Folder Shell - CLSID"));
            assertTrue(hts.contains("20d04fe0-3aea-1069-a2d8-08002b30309d"));
            assertTrue(hts.contains("Nome Primário"));
            assertTrue(hts.contains("PokerStarsUpdate.exe"));
            assertTrue(hts.contains("Nome Secundário"));
            assertTrue(hts.contains("PokerStars"));
//            assertTrue(hts.contains("Data do Último Acesso"));
//            assertTrue(hts.contains("31/03/2021 01:54:53"));


           }


}
