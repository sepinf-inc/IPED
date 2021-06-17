package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class WhatsAppParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testWhatsAppParserAndroid() throws IOException, SAXException, TikaException{

        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-db");
        metadata.add(Metadata.RESOURCE_NAME_KEY, "msgstore-d4");
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/whatsapp/msgstore.db");
        parser.setExtractMessages(true);
        parser.setMergeDbs(false);
        parser.getSupportedTypes(whatsappContext);
        parser.parse(stream, handler, metadata, whatsappContext);
        stream.close();
        
        assertEquals(63, whatsapptracker.title.size());
        assertEquals(63, whatsapptracker.username.size());
        assertEquals(0, whatsapptracker.userphone.size());
        assertEquals(0, whatsapptracker.useraccount.size());
        assertEquals(0, whatsapptracker.usernotes.size());
        assertEquals(19, whatsapptracker.participants.size());
        assertEquals(4, whatsapptracker.messagefrom.size());
        assertEquals(44, whatsapptracker.messagebody.size());
        assertEquals(4, whatsapptracker.messageto.size());
        assertEquals(44, whatsapptracker.messagedate.size());
        
        assertEquals("WhatsApp Chat - 556183125151", whatsapptracker.title.get(0));
        assertEquals("WhatsApp Chat - 556183125151_message_0", whatsapptracker.title.get(1));
        assertEquals("WhatsApp Chat - 556183125151_message_1", whatsapptracker.title.get(2));
        assertEquals("WhatsApp Chat - 556183125151_message_2", whatsapptracker.title.get(3));
        assertEquals("WhatsApp Chat - 556183125151_message_3", whatsapptracker.title.get(4));
        assertEquals("WhatsApp Group - Lar_message_1", whatsapptracker.title.get(61));
        assertEquals("WhatsApp Group - Lar_message_2", whatsapptracker.title.get(62));
        
        assertEquals("unknownAccount@", whatsapptracker.participants.get(0));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(1));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(2));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(3));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(4));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(17));
        assertEquals("unknownAccount@", whatsapptracker.participants.get(18));
        
        assertEquals("unknownAccount@", whatsapptracker.messagefrom.get(0));
        assertEquals("unknownAccount@", whatsapptracker.messagefrom.get(1));
        assertEquals("unknownAccount@", whatsapptracker.messagefrom.get(2));
        assertEquals("556183125151@s.whatsapp.net", whatsapptracker.messagefrom.get(3));
        
        assertEquals("This is a test for the IPED Whatsapp Parser.", whatsapptracker.messagebody.get(0));
        assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(1));
        assertEquals("Would you still say you love me", whatsapptracker.messagebody.get(2));
        assertEquals("The game is over", whatsapptracker.messagebody.get(3));
        assertEquals("Teste", whatsapptracker.messagebody.get(4));
        assertEquals("! USER_JOINED_GROUP", whatsapptracker.messagebody.get(42));
        assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(43));
        
        assertEquals("556183125151@s.whatsapp.net", whatsapptracker.messageto.get(0));
        assertEquals("556183125151@s.whatsapp.net", whatsapptracker.messageto.get(1));
        assertEquals("556183125151@s.whatsapp.net", whatsapptracker.messageto.get(2));
        assertEquals("unknownAccount@", whatsapptracker.messageto.get(3));
        
        assertEquals("2021-06-14T18:55:54Z", whatsapptracker.messagedate.get(0));
        assertEquals("2021-06-14T18:55:54Z", whatsapptracker.messagedate.get(1));
        assertEquals("2021-06-14T19:02:25Z", whatsapptracker.messagedate.get(2));
        assertEquals("2021-06-14T19:02:33Z", whatsapptracker.messagedate.get(3));
        assertEquals("2021-06-16T17:01:34Z", whatsapptracker.messagedate.get(4));
        assertEquals("2016-04-29T21:15:08Z", whatsapptracker.messagedate.get(42));
        assertEquals("2021-06-14T18:52:46Z", whatsapptracker.messagedate.get(43));
        
    }

    @Test
    public void testWhatsAppParserMergeDBAndroid() throws IOException, SAXException, TikaException{

        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-db");
        metadata.add(Metadata.RESOURCE_NAME_KEY, "msgstore-d4");
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/whatsapp/msgstore.db");
        parser.setExtractMessages(true);
        parser.setMergeDbs(true);
        parser.getSupportedTypes(whatsappContext);
//        parser.parse(stream, handler, metadata, whatsappContext);
        stream.close();
    }
    
    @Test
  public void testWhatsAppParserWADBAndroid() throws IOException, SAXException, TikaException{

      WhatsAppParser parser = new WhatsAppParser();
      Metadata metadata = new Metadata();
      metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-wadb");
      ContentHandler handler = new BodyContentHandler(1<<20);
      InputStream stream = getStream("test-files/whatsapp/wa.db");
      parser.setExtractMessages(true);
      parser.setMergeDbs(false);
      parser.getSupportedTypes(whatsappContext);
      parser.parse(stream, handler, metadata, whatsappContext);
      stream.close();
      assertEquals(384, whatsapptracker.title.size());
      assertEquals(384, whatsapptracker.username.size());
      assertEquals(365, whatsapptracker.userphone.size());
      assertEquals(384, whatsapptracker.useraccount.size());
      assertEquals(165, whatsapptracker.usernotes.size());
      assertEquals(0, whatsapptracker.participants.size());
      assertEquals(0, whatsapptracker.messagefrom.size());
      assertEquals(0, whatsapptracker.messagebody.size());
      assertEquals(0, whatsapptracker.messageto.size());
      assertEquals(0, whatsapptracker.messagedate.size());
      
      assertEquals("WhatsApp Contact: 556192644086", whatsapptracker.title.get(0));
      assertEquals("WhatsApp Contact: Nwi Fibra Ótica", whatsapptracker.title.get(1));
      assertEquals("WhatsApp Contact: Hugo", whatsapptracker.title.get(2));
      assertEquals("WhatsApp Contact: Pedro Gonzaga", whatsapptracker.title.get(3));
      assertEquals("WhatsApp Contact: Vet", whatsapptracker.title.get(4));
      assertEquals("WhatsApp Contact: 556186713007", whatsapptracker.title.get(382));
      assertEquals("WhatsApp Contact: Cpf Mãe", whatsapptracker.title.get(383));
      
      assertEquals("556192644086", whatsapptracker.username.get(0));
      assertEquals("Nwi Fibra Ótica", whatsapptracker.username.get(1));
      assertEquals("Hugo", whatsapptracker.username.get(2));
      assertEquals("Pedro Gonzaga", whatsapptracker.username.get(3));
      assertEquals("Vet", whatsapptracker.username.get(4));
      assertEquals("556186713007", whatsapptracker.username.get(382));
      assertEquals("Cpf Mãe", whatsapptracker.username.get(383));

      assertEquals("+556192644086", whatsapptracker.userphone.get(0));
      assertEquals("+556133223200", whatsapptracker.userphone.get(1));
      assertEquals("+556181680369", whatsapptracker.userphone.get(2));
      assertEquals("+556199351995", whatsapptracker.userphone.get(3));
      assertEquals("+556182227557", whatsapptracker.userphone.get(4));
      assertEquals("+556186713007", whatsapptracker.userphone.get(363));
      assertEquals("+5502163674092549", whatsapptracker.userphone.get(364));
      
      assertEquals("556192644086@s.whatsapp.net", whatsapptracker.useraccount.get(0));
      assertEquals("556133223200@s.whatsapp.net", whatsapptracker.useraccount.get(1));
      assertEquals("556181680369@s.whatsapp.net", whatsapptracker.useraccount.get(2));
      assertEquals("556199351995@s.whatsapp.net", whatsapptracker.useraccount.get(3));
      assertEquals("556182227557@s.whatsapp.net", whatsapptracker.useraccount.get(4));
      assertEquals("556186713007@s.whatsapp.net", whatsapptracker.useraccount.get(382));
      assertEquals("5502163674092549@s.whatsapp.net", whatsapptracker.useraccount.get(383));
      
      assertEquals("|NWI TELECOM|", whatsapptracker.usernotes.get(0));
      assertEquals("†", whatsapptracker.usernotes.get(1));
      assertEquals("\"No man is an island, entire of itself\"", whatsapptracker.usernotes.get(2));
      assertEquals("Olá! Eu estou usando WhatsApp.", whatsapptracker.usernotes.get(3));
      assertEquals("\"Nobility is about being superior than your former self\"", whatsapptracker.usernotes.get(4));
      assertEquals("Leve seus sonhos a sério", whatsapptracker.usernotes.get(163));
      assertEquals("Disponível", whatsapptracker.usernotes.get(164));
      
  }

    @Test
  public void testWhatsAppParserUserXMLAndroid() throws IOException, SAXException, TikaException{

      WhatsAppParser parser = new WhatsAppParser();
      Metadata metadata = new Metadata();
      metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-user-xml");
      ContentHandler handler = new BodyContentHandler(1<<20);
      InputStream stream = getStream("test-files/whatsapp/com.whatsapp_preferences.xml");
      parser.setExtractMessages(true);
      parser.setMergeDbs(false);
      parser.getSupportedTypes(whatsappContext);
      parser.parse(stream, handler, metadata, whatsappContext);
      stream.close();
      
      assertEquals(1, whatsapptracker.title.size());
      assertEquals(1, whatsapptracker.username.size());
      assertEquals(0, whatsapptracker.userphone.size());
      assertEquals(1, whatsapptracker.useraccount.size());
      assertEquals(1, whatsapptracker.usernotes.size());
      assertEquals(0, whatsapptracker.participants.size());
      assertEquals(0, whatsapptracker.messagefrom.size());
      assertEquals(0, whatsapptracker.messagebody.size());
      assertEquals(0, whatsapptracker.messageto.size());
      assertEquals(0, whatsapptracker.messagedate.size());
      
      assertEquals("WhatsApp Account: ", whatsapptracker.title.get(0));
      assertEquals("", whatsapptracker.username.get(0));
      assertEquals("@s.whatsapp.net", whatsapptracker.useraccount.get(0));
      assertEquals("", whatsapptracker.usernotes.get(0));
      
  }    
    
}
