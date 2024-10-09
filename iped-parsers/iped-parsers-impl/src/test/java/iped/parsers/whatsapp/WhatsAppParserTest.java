package iped.parsers.whatsapp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.parsers.util.ConversationUtils;

public class WhatsAppParserTest extends AbstractPkgTest {

    @Test
    public void testWhatsAppParserAndroid() throws IOException, SAXException, TikaException {

        String testFile = "test-files/test_whatsAppMsgStore.db";
        ParseContext whatsappContext = getContext(testFile);
        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, WhatsAppParser.MSG_STORE.toString());
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "msgstore-d4");
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setMergeBackups(false);
        parser.getSupportedTypes(whatsappContext);
        try (InputStream stream = getStream(testFile)) {
            parser.parse(stream, handler, metadata, whatsappContext);
            assertEquals(103, whatsapptracker.title.size());
            assertEquals(0, whatsapptracker.username.size());
            assertEquals(0, whatsapptracker.userphone.size());
            assertEquals(0, whatsapptracker.useraccount.size());
            assertEquals(0, whatsapptracker.usernotes.size());
            assertEquals(29, whatsapptracker.participants.size());
            assertEquals(4, whatsapptracker.messagefrom.size());
            assertEquals(74, whatsapptracker.messagebody.size());
            assertEquals(4, whatsapptracker.messageto.size());
            assertEquals(74, whatsapptracker.messagedate.size());

            assertEquals(11, Collections.frequency(whatsapptracker.type, ConversationUtils.TYPE_PRIVATE));
            assertEquals(18, Collections.frequency(whatsapptracker.type, ConversationUtils.TYPE_GROUP));
            assertEquals(0, Collections.frequency(whatsapptracker.type, ConversationUtils.TYPE_BROADCAST));
            assertEquals(0, Collections.frequency(whatsapptracker.type, ConversationUtils.TYPE_UNKONWN));

            assertEquals("WhatsApp Chat - Nickerida - 556183125151", whatsapptracker.title.get(0));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_0", whatsapptracker.title.get(1));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_1", whatsapptracker.title.get(2));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_2", whatsapptracker.title.get(3));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_3", whatsapptracker.title.get(4));
            assertEquals("WhatsApp Group - Lar - 556185747642-1461964508_message_1", whatsapptracker.title.get(61));
            assertEquals("WhatsApp Group - Lar - 556185747642-1461964508_message_2", whatsapptracker.title.get(62));

            // Test Private Chats

            assertEquals(ConversationUtils.TYPE_PRIVATE, whatsapptracker.type.get(0));
            assertEquals(ConversationUtils.TYPE_PRIVATE, whatsapptracker.type.get(19));
            assertEquals(ConversationUtils.TYPE_PRIVATE, whatsapptracker.type.get(20));
            assertEquals(ConversationUtils.TYPE_PRIVATE, whatsapptracker.type.get(21));

            assertEquals(2, whatsapptracker.participants.get(0).size());
            assertEquals(2, whatsapptracker.participants.get(19).size());
            assertEquals(2, whatsapptracker.participants.get(20).size());
            assertEquals(2, whatsapptracker.participants.get(21).size());

            assertThat(whatsapptracker.participants.get(0), hasItems("unknownAccount", "Nickerida (556183125151@s.whatsapp.net)"));
            assertThat(whatsapptracker.participants.get(19), hasItems("unknownAccount", "Hotdog412 (556181704627@s.whatsapp.net)"));
            assertThat(whatsapptracker.participants.get(20), hasItems("unknownAccount", "Nwi Fibra Ótica (556133223200@s.whatsapp.net)"));
            assertThat(whatsapptracker.participants.get(21), hasItems("unknownAccount", "Xavier (556135952111@s.whatsapp.net)"));

            assertEquals(0, whatsapptracker.admins.get(0).size());
            assertEquals(0, whatsapptracker.admins.get(19).size());
            assertEquals(0, whatsapptracker.admins.get(20).size());
            assertEquals(0, whatsapptracker.admins.get(21).size());

            // Test Group Chats

            assertEquals(ConversationUtils.TYPE_GROUP, whatsapptracker.type.get(1));
            assertEquals(ConversationUtils.TYPE_GROUP, whatsapptracker.type.get(2));
            assertEquals(ConversationUtils.TYPE_GROUP, whatsapptracker.type.get(3));
            assertEquals(ConversationUtils.TYPE_GROUP, whatsapptracker.type.get(4));

            assertEquals(23, whatsapptracker.participants.get(1).size());
            assertEquals(21, whatsapptracker.participants.get(2).size());
            assertEquals(5, whatsapptracker.participants.get(3).size());
            assertEquals(3, whatsapptracker.participants.get(4).size());

            assertThat(whatsapptracker.participants.get(1), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(2), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(3), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(4), hasItems("unknownAccount"));

            assertEquals(21, whatsapptracker.admins.get(1).size());
            assertEquals(19, whatsapptracker.admins.get(2).size());
            assertEquals(1, whatsapptracker.admins.get(3).size());
            assertEquals(0, whatsapptracker.admins.get(4).size());

            assertThat(whatsapptracker.admins.get(1), hasItems("(556192644086@s.whatsapp.net)"));
            assertThat(whatsapptracker.admins.get(2), hasItems("Pedro Gonzaga (556199351995@s.whatsapp.net)"));

            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(0));
            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(1));
            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(2));
            assertEquals("Nickerida (556183125151@s.whatsapp.net)", whatsapptracker.messagefrom.get(3));

            assertEquals("This is a test for the IPED Whatsapp Parser.", whatsapptracker.messagebody.get(0));
            assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(1));
            assertEquals("Would you still say you love me", whatsapptracker.messagebody.get(2));
            assertEquals("The game is over", whatsapptracker.messagebody.get(3));
            assertEquals("Teste", whatsapptracker.messagebody.get(4));
            assertEquals("! USER_ADDED_TO_GROUP", whatsapptracker.messagebody.get(42));
            assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(43));

            assertEquals("Nickerida (556183125151@s.whatsapp.net)", whatsapptracker.messageto.get(0));
            assertEquals("Nickerida (556183125151@s.whatsapp.net)", whatsapptracker.messageto.get(1));
            assertEquals("Nickerida (556183125151@s.whatsapp.net)", whatsapptracker.messageto.get(2));
            assertEquals("unknownAccount", whatsapptracker.messageto.get(3));

            assertEquals("2021-06-14T18:55:54Z", whatsapptracker.messagedate.get(0));
            assertEquals("2021-06-14T18:55:54Z", whatsapptracker.messagedate.get(1));
            assertEquals("2021-06-14T19:02:25Z", whatsapptracker.messagedate.get(2));
            assertEquals("2021-06-14T19:02:33Z", whatsapptracker.messagedate.get(3));
            assertEquals("2021-06-16T17:01:34Z", whatsapptracker.messagedate.get(4));
            assertEquals("2016-04-29T21:15:08Z", whatsapptracker.messagedate.get(42));
            assertEquals("2021-06-14T18:52:46Z", whatsapptracker.messagedate.get(43));

        }

    }

    @Test
    public void testWhatsAppParserMergeDBAndroid() throws IOException, SAXException, TikaException {

        String testFile = "test-files/test_whatsAppMsgStore.db";
        ParseContext whatsappContext = getContext(testFile);
        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, WhatsAppParser.MSG_STORE.toString());
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "msgstore-d4");
        parser.setExtractMessages(true);
        parser.setMergeBackups(true);
        parser.getSupportedTypes(whatsappContext);
        try (InputStream stream = getStream(testFile)) {
            // parser.parse(stream, handler, metadata, whatsappContext);

        }
    }

    @Test
    public void testWhatsAppParserWADBAndroid() throws IOException, SAXException, TikaException {

        String testFile = "test-files/test_whatsApp.db";
        ParseContext whatsappContext = getContext(testFile);
        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-wadb");
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setMergeBackups(false);
        parser.getSupportedTypes(whatsappContext);
        try (InputStream stream = getStream(testFile)) {
            parser.parse(stream, handler, metadata, whatsappContext);

            assertEquals(384, whatsapptracker.title.size());
            assertEquals(384, whatsapptracker.username.size());
            assertEquals(365, whatsapptracker.userphone.size());
            assertEquals(384, whatsapptracker.useraccount.size());
            assertEquals(166, whatsapptracker.usernotes.size());
            assertEquals(0, whatsapptracker.participants.size());
            assertEquals(0, whatsapptracker.admins.size());
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
            assertEquals("\"Nobility is about being superior than your former self\"",
                    whatsapptracker.usernotes.get(4));
            assertEquals("Leve seus sonhos a sério", whatsapptracker.usernotes.get(164));
            assertEquals("Disponível", whatsapptracker.usernotes.get(165));

        }

    }

    @Test
    public void testWhatsAppParserUserXMLAndroid() throws IOException, SAXException, TikaException {

        String testFile = "test-files/test_whatsAppPreferences.xml";
        ParseContext whatsappContext = getContext(testFile);
        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-user-xml");
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setMergeBackups(false);
        parser.getSupportedTypes(whatsappContext);
        try (InputStream stream = getStream(testFile)) {
            parser.parse(stream, handler, metadata, whatsappContext);

            assertEquals(1, whatsapptracker.title.size());
            assertEquals(1, whatsapptracker.username.size());
            assertEquals(1, whatsapptracker.userphone.size());
            assertEquals(1, whatsapptracker.useraccount.size());
            assertEquals(1, whatsapptracker.usernotes.size());
            assertEquals(0, whatsapptracker.participants.size());
            assertEquals(0, whatsapptracker.messagefrom.size());
            assertEquals(0, whatsapptracker.messagebody.size());
            assertEquals(0, whatsapptracker.messageto.size());
            assertEquals(0, whatsapptracker.messagedate.size());

            assertEquals("WhatsApp Account: Mr. Roboto", whatsapptracker.title.get(0));
            assertEquals("Mr. Roboto", whatsapptracker.username.get(0));
            assertEquals("554499887766@s.whatsapp.net", whatsapptracker.useraccount.get(0));
            assertEquals("Domo arigato!", whatsapptracker.usernotes.get(0));

        }
    }

}
