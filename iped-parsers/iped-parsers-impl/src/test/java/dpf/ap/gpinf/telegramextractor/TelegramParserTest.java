package dpf.ap.gpinf.telegramextractor;


import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class TelegramParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testTelegramParser() throws IOException, SAXException, TikaException{

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/test_telegramCache4.db");
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramContext);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-telegram-db");
        parser.parse(stream, handler, metadata, telegramContext);
        stream.close();
        
        assertEquals(509, telegramtracker.title.size());
        assertEquals(513, telegramtracker.username.size());
        assertEquals(236, telegramtracker.userphone.size());
        assertEquals(261, telegramtracker.useraccount.size());
        assertEquals(118, telegramtracker.usernotes.size());
        assertEquals(32, telegramtracker.participants.size());
        assertEquals(151, telegramtracker.messagefrom.size());
        assertEquals(151, telegramtracker.messagebody.size());
        assertEquals(151, telegramtracker.messageto.size());
        assertEquals(151, telegramtracker.messagedate.size());
        
        assertEquals("Tiago", telegramtracker.title.get(0));
        assertEquals("Karol Braz", telegramtracker.title.get(1));
        assertEquals("Budi", telegramtracker.title.get(2));
        assertEquals("Nickerida", telegramtracker.title.get(3));
        assertEquals("Telegram_Chat_Marcoscachos", telegramtracker.title.get(505));
        assertEquals("Telegram_Chat_Marcoscachos_message_0", telegramtracker.title.get(506));
        assertEquals("Telegram_Group_mixirica e noronhe-se", telegramtracker.title.get(507));
        assertEquals("Telegram_Group_mixirica e noronhe-se_message_0", telegramtracker.title.get(508));

        assertEquals("Tiago", telegramtracker.username.get(0));
        assertEquals("Karol Braz", telegramtracker.username.get(1));
        assertEquals("Budi", telegramtracker.username.get(3));
        
        assertEquals("5561981124921", telegramtracker.userphone.get(0));
        assertEquals("5561992311125", telegramtracker.userphone.get(1));
        assertEquals("5561983125151", telegramtracker.userphone.get(3));
        
        assertEquals("1289498844", telegramtracker.useraccount.get(0));
        assertEquals("165119446", telegramtracker.useraccount.get(1));
        assertEquals("53985588", telegramtracker.useraccount.get(3));
        
        assertEquals("maju_chuchu", telegramtracker.usernotes.get(0));
        assertEquals("RafaelCampos", telegramtracker.usernotes.get(1));
        assertEquals("gif", telegramtracker.usernotes.get(3));
        
        assertEquals("Bruno Chaves (phone: 33667514279)", telegramtracker.participants.get(0));
        assertEquals("Nake Douglas (phone: 5561982616052)", telegramtracker.participants.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.participants.get(2));

        assertEquals("Telegram (phone: 42777)", telegramtracker.messagefrom.get(0));
        assertEquals("Nickerida (phone: 5561983125151)", telegramtracker.messagefrom.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.messagefrom.get(3));

        assertEquals("Telegram (phone: 42777)", telegramtracker.messageto.get(0));
        assertEquals("Nickerida (phone: 5561983125151)", telegramtracker.messageto.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.messageto.get(150));
        
        assertTrue(telegramtracker.messagebody.get(0).contains("Código de login: 73632. Não envie esse código para ninguém, nem mesmo que eles digam que são do Telegram!"));
        assertTrue(telegramtracker.messagebody.get(1).contains("Sacou?"));
 
//        assertEquals("2021-06-09T18:56:52Z", telegramtracker.messagedate.get(0));
//        assertEquals("2021-06-09T01:34:33Z", telegramtracker.messagedate.get(1));
//        assertEquals("2019-04-23T18:40:10Z", telegramtracker.messagedate.get(150));
       
    }
    
    @Test
    public void testTelegramParserAndroidAcc() throws IOException, SAXException, TikaException{

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/test_telegramUserConfing.xml");
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramUserContext);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-telegram-user-conf");
        parser.parseAndroidAccount(stream, handler, metadata, telegramUserContext);
        stream.close();
        
        assertEquals(1, telegramusertracker.title.size());
        assertEquals(1, telegramusertracker.username.size());
        assertEquals(1, telegramusertracker.userphone.size());
        assertEquals(1, telegramusertracker.useraccount.size());

        assertEquals("Telegram - Guilherme Andreúce", telegramusertracker.title.get(0));
        assertEquals("Guilherme", telegramusertracker.username.get(0));
        assertEquals("5561986143035", telegramusertracker.userphone.get(0));
        assertEquals("guileb", telegramusertracker.useraccount.get(0));
    }
}
