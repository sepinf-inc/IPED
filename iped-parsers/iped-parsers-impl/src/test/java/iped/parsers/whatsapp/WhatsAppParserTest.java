package iped.parsers.whatsapp;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ConversationConstants;
import iped.parsers.util.ItemInfo;
import iped.search.IItemSearcher;

public class WhatsAppParserTest extends AbstractPkgTest {

    boolean waDbParsed = false;

    @Test
    public void testWhatsAppParserAndroid() throws IOException, SAXException, TikaException {

        if (!waDbParsed) {
            testWhatsAppParserWADBAndroid();
        }

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

            assertEquals(11, Collections.frequency(whatsapptracker.type, ConversationConstants.TYPE_ONEONONE));
            assertEquals(18, Collections.frequency(whatsapptracker.type, ConversationConstants.TYPE_GROUP));
            assertEquals(0, Collections.frequency(whatsapptracker.type, ConversationConstants.TYPE_BROADCAST));
            assertEquals(0, Collections.frequency(whatsapptracker.type, ConversationConstants.TYPE_UNKNOWN));

            // account owner is admin ("status_me" with rank > 0) in 8 groups, detected
            // even if the account itself is unknown
            assertEquals(8, whatsapptracker.ownerAdminCount);

            assertEquals("WhatsApp Chat - Nickerida - 556183125151", whatsapptracker.title.get(0));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_0", whatsapptracker.title.get(1));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_1", whatsapptracker.title.get(2));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_2", whatsapptracker.title.get(3));
            assertEquals("WhatsApp Chat - Nickerida - 556183125151_message_3", whatsapptracker.title.get(4));
            assertEquals("WhatsApp Group - Lar - 556185747642-1461964508_message_1", whatsapptracker.title.get(61));
            assertEquals("WhatsApp Group - Lar - 556185747642-1461964508_message_2", whatsapptracker.title.get(62));

            // Test OneOnOne Chats

            assertEquals(ConversationConstants.TYPE_ONEONONE, whatsapptracker.type.get(0));
            assertEquals(ConversationConstants.TYPE_ONEONONE, whatsapptracker.type.get(19));
            assertEquals(ConversationConstants.TYPE_ONEONONE, whatsapptracker.type.get(20));
            assertEquals(ConversationConstants.TYPE_ONEONONE, whatsapptracker.type.get(21));

            assertEquals(2, whatsapptracker.participants.get(0).size());
            assertEquals(2, whatsapptracker.participants.get(19).size());
            assertEquals(2, whatsapptracker.participants.get(20).size());
            assertEquals(2, whatsapptracker.participants.get(21).size());

            assertThat(whatsapptracker.participants.get(0), hasItems("unknownAccount", "Nickerida (+556183125151)"));
            assertThat(whatsapptracker.participants.get(19), hasItems("unknownAccount", "Hotdog412 (+556181704627)"));
            assertThat(whatsapptracker.participants.get(20), hasItems("unknownAccount", "Nwi Fibra Ótica (+556133223200)"));
            assertThat(whatsapptracker.participants.get(21), hasItems("unknownAccount", "Xavier (+556135952111)"));

            assertEquals(0, whatsapptracker.admins.get(0).size());
            assertEquals(0, whatsapptracker.admins.get(19).size());
            assertEquals(0, whatsapptracker.admins.get(20).size());
            assertEquals(0, whatsapptracker.admins.get(21).size());

            // Test Group Chats

            assertEquals(ConversationConstants.TYPE_GROUP, whatsapptracker.type.get(1));
            assertEquals(ConversationConstants.TYPE_GROUP, whatsapptracker.type.get(2));
            assertEquals(ConversationConstants.TYPE_GROUP, whatsapptracker.type.get(3));
            assertEquals(ConversationConstants.TYPE_GROUP, whatsapptracker.type.get(4));

            assertEquals(23, whatsapptracker.participants.get(1).size());
            assertEquals(21, whatsapptracker.participants.get(2).size());
            assertEquals(5, whatsapptracker.participants.get(3).size());
            assertEquals(2, whatsapptracker.participants.get(4).size());

            assertThat(whatsapptracker.participants.get(1), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(2), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(3), hasItems("unknownAccount"));
            assertThat(whatsapptracker.participants.get(4), hasItems("unknownAccount"));

            assertEquals(22, whatsapptracker.admins.get(1).size());
            assertEquals(20, whatsapptracker.admins.get(2).size());
            assertEquals(1, whatsapptracker.admins.get(3).size());
            assertEquals(1, whatsapptracker.admins.get(4).size());

            assertThat(whatsapptracker.admins.get(1), hasItems("+556192644086"));
            // account owner is admin ("status_me" with rank > 0) in these groups
            assertThat(whatsapptracker.admins.get(1), hasItems("unknownAccount"));
            assertThat(whatsapptracker.admins.get(2), hasItems("unknownAccount"));
            assertThat(whatsapptracker.admins.get(4), hasItems("unknownAccount"));
            assertThat(whatsapptracker.admins.get(2), hasItems("Pedro Gonzaga (+556199351995)"));

            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(0));
            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(1));
            assertEquals("unknownAccount", whatsapptracker.messagefrom.get(2));
            assertEquals("Nickerida (+556183125151)", whatsapptracker.messagefrom.get(3));

            assertEquals("This is a test for the IPED Whatsapp Parser.", whatsapptracker.messagebody.get(0));
            assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(1));
            assertEquals("Would you still say you love me", whatsapptracker.messagebody.get(2));
            assertEquals("The game is over", whatsapptracker.messagebody.get(3));
            assertEquals("Teste", whatsapptracker.messagebody.get(4));
            assertEquals("! USER_ADDED_TO_GROUP", whatsapptracker.messagebody.get(42));
            assertEquals("! MESSAGES_NOW_ENCRYPTED", whatsapptracker.messagebody.get(43));

            assertEquals("Nickerida (+556183125151)", whatsapptracker.messageto.get(0));
            assertEquals("Nickerida (+556183125151)", whatsapptracker.messageto.get(1));
            assertEquals("Nickerida (+556183125151)", whatsapptracker.messageto.get(2));
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

            waDbParsed = true;

            assertEquals(384, whatsapptracker.title.size());
            assertEquals(384, whatsapptracker.username.size());
            assertEquals(365, whatsapptracker.userphone.size());
            assertEquals(384, whatsapptracker.useraccount.size());
            assertEquals(165, whatsapptracker.usernotes.size());
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
            assertEquals("WhatsApp Contact: 556186713007", whatsapptracker.title.get(381));
            assertEquals("WhatsApp Contact: Cpf Mãe", whatsapptracker.title.get(382));

            assertEquals("556192644086", whatsapptracker.username.get(0));
            assertEquals("Nwi Fibra Ótica", whatsapptracker.username.get(1));
            assertEquals("Hugo", whatsapptracker.username.get(2));
            assertEquals("Pedro Gonzaga", whatsapptracker.username.get(3));
            assertEquals("Vet", whatsapptracker.username.get(4));
            assertEquals("556186713007", whatsapptracker.username.get(381));
            assertEquals("Cpf Mãe", whatsapptracker.username.get(382));

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
            assertEquals("556186713007@s.whatsapp.net", whatsapptracker.useraccount.get(381));
            assertEquals("5502163674092549@s.whatsapp.net", whatsapptracker.useraccount.get(382));

            assertEquals("|NWI TELECOM|", whatsapptracker.usernotes.get(0));
            assertEquals("†", whatsapptracker.usernotes.get(1));
            assertEquals("\"No man is an island, entire of itself\"", whatsapptracker.usernotes.get(2));
            assertEquals("Olá! Eu estou usando WhatsApp.", whatsapptracker.usernotes.get(3));
            assertEquals("\"Nobility is about being superior than your former self\"",
                    whatsapptracker.usernotes.get(4));
            assertEquals("Leve seus sonhos a sério", whatsapptracker.usernotes.get(163));
            assertEquals("Disponível", whatsapptracker.usernotes.get(164));

        }

    }

    /**
     * Since #2294, the Android account is not parsed from the preferences XML
     * itself: it is created as a child of wa.db, looking for the preferences XML
     * (in "shared_prefs", as in the real app data folder) with the item searcher.
     */
    @Test
    public void testWhatsAppParserAccountFromPreferencesXMLAndroid() throws IOException, SAXException, TikaException {

        String testFile = "test-files/test_whatsApp.db";
        ParseContext whatsappContext = getContext(testFile);
        whatsappContext.set(ItemInfo.class, new ItemInfo(0, "", Collections.emptySet(), Collections.emptySet(),
                "/data/data/com.whatsapp/databases/wa.db", false));

        File prefsFile = getFile("test-files/test_whatsAppPreferences.xml");
        IItemReader prefsItem = mock(IItemReader.class);
        when(prefsItem.getName()).thenReturn("com.whatsapp_preferences_light.xml");
        when(prefsItem.getPath())
                .thenReturn("/data/data/com.whatsapp/shared_prefs/com.whatsapp_preferences_light.xml");
        when(prefsItem.getBufferedInputStream())
                .thenAnswer(inv -> new BufferedInputStream(Files.newInputStream(prefsFile.toPath())));

        IItemSearcher searcher = mock(IItemSearcher.class);
        when(searcher.escapeQuery(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(searcher.searchIterable(anyString())).thenReturn(Collections.emptyList());
        when(searcher.search(anyString())).thenAnswer(inv -> {
            String query = inv.getArgument(0);
            List<IItemReader> result = new ArrayList<>();
            if (query.contains("\"com.whatsapp_preferences_light.xml\"")) {
                result.add(prefsItem);
            }
            return result;
        });
        whatsappContext.set(IItemSearcher.class, searcher);

        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, WhatsAppParser.WA_DB.toString());
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setMergeBackups(false);
        parser.getSupportedTypes(whatsappContext);
        try (InputStream stream = getStream(testFile)) {
            parser.parse(stream, handler, metadata, whatsappContext);

            // the account is the last item created
            assertEquals("WhatsApp Account: Mr. Roboto", last(whatsapptracker.title));
            assertEquals("Mr. Roboto", last(whatsapptracker.username));
            assertEquals("554499887766@s.whatsapp.net", last(whatsapptracker.useraccount));
            assertEquals("Domo arigato!", last(whatsapptracker.usernotes));
        }
    }

    private static String last(List<String> list) {
        return list.get(list.size() - 1);
    }

    @Test
    public void testIsBackupDatabase() {
        String dir = "/data/media/0/Android/media/com.whatsapp/WhatsApp/Databases/";
        assertFalse(WhatsAppParser.isBackupDatabase("/data/data/com.whatsapp/databases/msgstore.db"));
        assertFalse(WhatsAppParser.isBackupDatabase("AppDomainGroup-group.net.whatsapp.WhatsApp.shared/ChatStorage.sqlite"));
        assertFalse(WhatsAppParser.isBackupDatabase(null));
        assertTrue(WhatsAppParser.isBackupDatabase(dir + "msgstore-2023-10-03.1.db"));
        assertTrue(WhatsAppParser.isBackupDatabase(dir + "msgstore-2023-10-03.1.db.crypt15>>msgstore-2023-10-03.1.db"));
        assertTrue(WhatsAppParser.isBackupDatabase(dir + "msgstore.db.crypt15>>msgstore.db"));
        // a folder named like a backup does not make the main database a backup
        assertFalse(WhatsAppParser.isBackupDatabase("/extraction/msgstore-2023-10-03/data/data/com.whatsapp/databases/msgstore.db"));
    }
}
