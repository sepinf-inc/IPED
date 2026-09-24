package iped.parsers.telegram;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;

public class TelegramParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testTelegramParser() throws IOException, SAXException, TikaException {

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramContext);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, TelegramParser.TELEGRAM_DB.toString());
        try (InputStream stream = getStream("test-files/test_telegramCache4.db")) {
            parser.parse(stream, handler, metadata, telegramContext);

            assertEquals(516, telegramtracker.title.size());
            assertEquals(257, telegramtracker.username.size());
            assertEquals(236, telegramtracker.userphone.size());
            assertEquals(261, telegramtracker.useraccount.size());
            assertEquals(118, telegramtracker.usernotes.size());
            assertEquals(32, telegramtracker.groupParticipants.size());
            assertEquals(69, telegramtracker.privateParticipants.size());
            assertEquals(151, telegramtracker.messagefrom.size());
            assertEquals(151, telegramtracker.messagebody.size());
            assertEquals(151, telegramtracker.messageto.size());
            assertEquals(151, telegramtracker.messagedate.size());

            assertEquals("Tiago", telegramtracker.title.get(0));
            assertEquals("Karol Braz", telegramtracker.title.get(1));
            assertEquals("Budi", telegramtracker.title.get(2));
            assertEquals("Nickerida", telegramtracker.title.get(3));
            assertEquals("Telegram Chat - Marilê (+5561981993740)", telegramtracker.title.get(505));
            assertEquals("Telegram Chat - Marilê (+5561981993740)_message_0", telegramtracker.title.get(506));
            assertEquals("Telegram Chat - Rafael CiC (+5562991688700)", telegramtracker.title.get(507));
            assertEquals("Telegram Chat - Rafael CiC (+5562991688700)_message_0", telegramtracker.title.get(508));

            assertEquals("Tiago", telegramtracker.username.get(0));
            assertEquals("Karol Braz", telegramtracker.username.get(1));
            assertEquals("Budi", telegramtracker.username.get(2));

            assertEquals("5561981124921", telegramtracker.userphone.get(0));
            assertEquals("5561992311125", telegramtracker.userphone.get(1));
            assertEquals("5561983125151", telegramtracker.userphone.get(3));

            assertEquals("1289498844", telegramtracker.useraccount.get(0));
            assertEquals("165119446", telegramtracker.useraccount.get(1));
            assertEquals("53985588", telegramtracker.useraccount.get(3));

            assertEquals("maju_chuchu", telegramtracker.usernotes.get(0));
            assertEquals("RafaelCampos", telegramtracker.usernotes.get(1));
            assertEquals("gif", telegramtracker.usernotes.get(3));

            assertThat(telegramtracker.groupParticipants, hasItems(
                    "Bruno Chaves (+33667514279)",
                    "Nake Douglas (+5561982616052)",
                    "Guilherme Andreúce (@guileb | +5561986143035)"));
            assertThat(telegramtracker.privateParticipants, hasItems(
                    "Telegram (+42777)",
                    "Nickerida (+5561983125151)",
                    "Yan Victor (@YanVictor | +5561995328012)"));

            assertEquals("Vayu (+5561985757632)", telegramtracker.messagefrom.get(0));
            assertEquals("Felix (@Very_Hungery_Boye | +5561982943741)", telegramtracker.messagefrom.get(1));
            assertEquals("Caio Brandão (@CaioBrandao)", telegramtracker.messagefrom.get(3));

            assertEquals("Group Constelação Familiar e Barras de Access (ID:1459548546)", telegramtracker.messageto.get(0));
            assertEquals("Group Le Chad et les Chadettes (ID:1391969250)", telegramtracker.messageto.get(1));
            assertEquals("Rafael CiC (+5562991688700)", telegramtracker.messageto.get(150));

            assertTrue(telegramtracker.messagebody.get(1).contains("KKKKK"));
            assertTrue(telegramtracker.messagebody.get(2).contains("Parece outro carro até"));

            assertEquals("2021-06-08T21:20:21Z", telegramtracker.messagedate.get(0));
            assertEquals("2020-12-31T19:07:53Z", telegramtracker.messagedate.get(1));
            assertEquals("2021-02-09T13:46:55Z", telegramtracker.messagedate.get(150));

        }

    }

    @Test
    public void testTelegramParserAndroidAcc() throws IOException, SAXException, TikaException {

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1 << 20);
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramUserContext);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, TelegramParser.TELEGRAM_USER_CONF.toString());
        try (InputStream stream = getStream("test-files/test_telegramUserConfing.xml")) {
            parser.parseAndroidAccount(stream, handler, metadata, telegramUserContext);

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
}
