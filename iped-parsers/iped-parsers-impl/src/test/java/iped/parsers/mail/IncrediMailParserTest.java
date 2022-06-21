package iped.parsers.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.AbstractPkgTest;

public class IncrediMailParserTest extends AbstractPkgTest {

    protected ParseContext context;
    protected EmbeddedOLEParser tracker;

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        tracker = new EmbeddedOLEParser();
        context = new ParseContext();
        context.set(Parser.class, tracker);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedOLEParser extends AbstractParser {

        protected List<String> documentfolder = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                documentfolder.add(metadata.get(TikaCoreProperties.TITLE));
        }
    }

    @Test
    public void testIncrediMailParser() throws IOException, SAXException, TikaException {

        IncrediMailParser parser = new IncrediMailParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_inBox.imm")) {
            parser.parse(stream, handler, metadata, context);

            assertEquals(373, tracker.documentfolder.size());
            assertEquals("Welcome to IncrediMail!", tracker.documentfolder.get(0));
            assertEquals("Bem-vindo ao Outlook Express 6", tracker.documentfolder.get(1));
            assertEquals("2 items from your Steam wishlist are on sale!", tracker.documentfolder.get(2));
            assertEquals("Colocando tranquilidade no caos do dia a dia!", tracker.documentfolder.get(3));
            assertEquals("5 blogs que valem a pena seguir", tracker.documentfolder.get(4));
            assertEquals("[cic-bcc-l] Fwd: [cic-secretaria-l] ESTÁGIO - SELEÇÃO", tracker.documentfolder.get(371));
            assertEquals("Os 5 blogs do seu futuro", tracker.documentfolder.get(372));

        }
    }

}