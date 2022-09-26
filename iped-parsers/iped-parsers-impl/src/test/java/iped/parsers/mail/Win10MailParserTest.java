package iped.parsers.mail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.apache.tika.parser.Parser;
import org.xml.sax.SAXException;

import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;

public class Win10MailParserTest {
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testWin10MailParser() throws IOException, SAXException, TikaException {

        EmbeddedWin10MailParser win10MailTracker = new EmbeddedWin10MailParser();
        ParseContext edgeContext = new ParseContext();
        edgeContext.set(Parser.class, win10MailTracker);

        Win10MailParser parser = new Win10MailParser();
        assumeFalse(parser.getSupportedTypes(edgeContext).isEmpty());

        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, Win10MailParser.WIN10_MAIL_DB.toString());
        ContentHandler handler = new BodyContentHandler(1 << 25);
        String fileName = "test_store.vol";
        ItemInfo itemInfo = new ItemInfo(0, "", null, null, fileName, false);
        edgeContext.set(ItemInfo.class, itemInfo);
        parser.setExtractEntries(true);

        try (InputStream stream = getStream("test-files/private_mail_sample/" + fileName)) {

        //    parser.parse(stream, handler, metadata, edgeContext);

        }

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedWin10MailParser extends AbstractParser {

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

        }
    }

}
