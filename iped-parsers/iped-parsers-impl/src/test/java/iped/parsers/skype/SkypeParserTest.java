package iped.parsers.skype;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.BaseItemSearchContext;

public class SkypeParserTest extends BaseItemSearchContext {

    @Test
    public void testSkypeParserV12() throws IOException, SAXException, TikaException {

        String file = "test-files/test_skypeS4lStreeguil1.db";
        ParseContext skypeContext = getContext(file);
        SkypeParser parser = new SkypeParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, SkypeParser.SKYPE_MIME_V12.toString());
        parser.getSupportedTypes(skypeContext);
        parser.setExtractMessages(true);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, skypeContext);
            String hts = handler.toString();
            String mts = metadata.toString();
            assertTrue(hts.contains("conversationsv14"));
            assertTrue(hts.contains("5"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("internaldata"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("12"));
            assertTrue(hts.contains("cellularMessageInfo"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("engagementMessagesQueue"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("mediadrafts"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("0"));

            assertTrue(mts.contains("database:table_name=metadata"));
            assertTrue(mts.contains("database:table_name=messagesv12"));
            assertTrue(mts.contains("database:table_name=alertsv12"));
            assertTrue(mts.contains("database:table_name=popupcards"));
            assertTrue(mts.contains("database:table_name=conversationsv14"));
            assertTrue(mts.contains("Content-Type=" + SkypeParser.SKYPE_MIME_V12.toString()));
            assertTrue(mts.contains("database:table_name=profilecachev8_phoneNumbersIndex"));
            assertTrue(mts.contains("database:table_name=conversationsv14_searchTerms_segdir"));
            assertTrue(mts.contains("database:table_name=conversationsv14_searchTerms_segments"));
            assertTrue(mts.contains("database:table_name=conversationsv14_searchTerms_content"));

        }
    }

    @Test
    public void testSkypeParserV8() throws IOException, SAXException, TikaException {
        // Database created using SKYPERIOUS
        String file = "test-files/test_skypeMain.db";
        ParseContext skypeContext = getContext(file);
        SkypeParser parser = new SkypeParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, SkypeParser.SKYPE_MIME.toString());
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(skypeContext);
        parser.setExtractMessages(true);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, skypeContext);
            String hts = handler.toString();
            String mts = metadata.toString();
            assertTrue(hts.contains("Videos"));
            assertTrue(hts.contains("21"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("ConversationViews"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("Accounts"));
            assertTrue(hts.contains("104"));
            assertTrue(hts.contains("1"));
            assertTrue(hts.contains("Contacts"));
            assertTrue(hts.contains("111"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("Transfers"));
            assertTrue(hts.contains("30"));
            assertTrue(hts.contains("2"));

            assertTrue(mts.contains("database:table_name=Videos"));
            assertTrue(mts.contains("database:table_name=SMSes"));
            assertTrue(mts.contains("database:table_name=ConversationViews"));
            assertTrue(mts.contains("database:table_name=Accounts"));
            assertTrue(mts.contains("database:table_name=Contacts"));
            assertTrue(mts.contains("database:table_name=Participants"));
            assertTrue(mts.contains("Content-Type=" + SkypeParser.SKYPE_MIME.toString()));

        }
    }
}
