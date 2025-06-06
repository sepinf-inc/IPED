package iped.parsers.mail;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.content.TikaManager;
import iped.parsers.util.AbstractPkgTest;
import iped.properties.ExtraProperties;

public class MBoxParserTest extends AbstractPkgTest {

    protected EmbeddedMboxParser mboxtracker;
    protected ParseContext mboxContext;

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        try {
            TikaManager.initializeTikaConfig();
        } catch (IllegalStateException ignore) {
        }

        mboxtracker = new EmbeddedMboxParser();
        mboxContext = new ParseContext();
        mboxContext.set(Parser.class, mboxtracker);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedMboxParser extends AbstractParser {
        protected List<String> messagesubject = new ArrayList<String>();
        protected List<String> contenttype = new ArrayList<String>();
        protected List<String> contentmd5 = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            String hdigest = new DigestUtils(MD5).digestAsHex(stream);
            if (metadata.get(ExtraProperties.MESSAGE_SUBJECT) != null)
                messagesubject.add(metadata.get(ExtraProperties.MESSAGE_SUBJECT));
            if (metadata.get(HttpHeaders.CONTENT_TYPE) != null)
                contenttype.add(metadata.get(HttpHeaders.CONTENT_TYPE));
            contentmd5.add(hdigest.toUpperCase());

        }

    }

    @Test
    public void testMboxParsing() throws IOException, SAXException, TikaException {

        MboxParser parser = new MboxParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_mbox.mbox")) {
            parser.parse(stream, handler, metadata, context);

        }

    }

    @Test
    public void testMboxMetadata() throws IOException, SAXException, TikaException {

        MboxParser parser = new MboxParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_mbox.mbox")) {
            parser.parse(stream, handler, metadata, mboxContext);

            assertEquals(8, mboxtracker.messagesubject.size());
            assertEquals(8, mboxtracker.contenttype.size());
            assertEquals(8, mboxtracker.contentmd5.size());

            assertEquals("DADOS EXPERIMENTO 6", mboxtracker.messagesubject.get(0));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(0));
            assertEquals("C445685C851ED833DE708F74B5C7E9B0", mboxtracker.contentmd5.get(0));

            assertEquals("Re: Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(1));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(1));
            assertEquals("C5C26209B5CF4F369B4CDD754270A82B", mboxtracker.contentmd5.get(1));

            assertEquals("Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(2));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(2));
            assertEquals("BD869B271E3037E88739CE5D8131650C", mboxtracker.contentmd5.get(2));

            assertEquals("Linf++", mboxtracker.messagesubject.get(3));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(3));
            assertEquals("104907B3FDC01AD771263CA0FC79728A", mboxtracker.contentmd5.get(3));

            assertEquals("Trabalho CB Incompleto", mboxtracker.messagesubject.get(4));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(4));
            assertEquals("CFFC46163BB36FC3B5E730F2A7E27AB0", mboxtracker.contentmd5.get(4));

            // attachment Qualéamúsicapronto.zip
            assertEquals("Trabalho cb completo", mboxtracker.messagesubject.get(5));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(5));
            assertEquals("59DD6F57C182E2F67F4C6857073ECFB4", mboxtracker.contentmd5.get(5));

            assertEquals("FÌSICA EXPERIMENTAL", mboxtracker.messagesubject.get(6));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(6));
            assertEquals("AE06FBC3694648397C563ACEC269515E", mboxtracker.contentmd5.get(6));

            assertEquals("Trabalho 3 física experimental", mboxtracker.messagesubject.get(7));
            assertEquals("message/rfc822", mboxtracker.contenttype.get(7));
            assertEquals("D14BA8E896A274896CD835092FECB956", mboxtracker.contentmd5.get(7));

        }
    }
}
