package iped.parsers.mail;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
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
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.util.AbstractPkgTest;
import iped.properties.ExtraProperties;

public class OutlookPSTParserTest extends AbstractPkgTest {

    protected EmbeddedPSTParser psttracker;
    protected ParseContext pstContext;

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    public void setUp() throws Exception {
        super.setUp();
        psttracker = new EmbeddedPSTParser();
        pstContext = new ParseContext();
        pstContext.set(Parser.class, psttracker);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedPSTParser extends AbstractParser {

        protected List<String> foldertitle = new ArrayList<String>();
        protected List<String> foldercreated = new ArrayList<String>();
        protected List<String> foldermodified = new ArrayList<String>();
        protected List<String> foldercomment = new ArrayList<String>();
        protected List<String> messagesubject = new ArrayList<String>();
        protected List<String> messagebody = new ArrayList<String>();
        protected List<String> messagedate = new ArrayList<String>();
        protected List<String> isattachment = new ArrayList<String>();
        protected List<String> attachmentname = new ArrayList<String>();
        protected List<String> numberofattachments = new ArrayList<String>();
        protected List<String> useraccount = new ArrayList<String>();
        protected List<String> username = new ArrayList<String>();
        protected List<String> useremail = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useraddress = new ArrayList<String>();
        protected List<String> userbirth = new ArrayList<String>();
        protected List<String> userorganization = new ArrayList<String>();
        protected List<String> userurls = new ArrayList<String>();
        protected List<String> usernotes = new ArrayList<String>();
        protected List<String> contentmd5 = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            // avoiding filling the string vector with nullified info may cause the
            // information to be
            // in the wrong position! Have to be cautious whenever trying to associate the
            // information
            // for testing
            // md5
            String hdigest = new DigestUtils(MD5).digestAsHex(stream);
            // folder
            if (metadata.get(TikaCoreProperties.TITLE) != null)
                foldertitle.add(metadata.get(TikaCoreProperties.TITLE));
            if (metadata.get(TikaCoreProperties.CREATED) != null)
                foldercreated.add(metadata.get(TikaCoreProperties.CREATED));
            if (metadata.get(TikaCoreProperties.MODIFIED) != null)
                foldermodified.add(metadata.get(TikaCoreProperties.MODIFIED));
            if (metadata.get(Metadata.COMMENT) != null)
                foldercomment.add(metadata.get(Metadata.COMMENT));
            // messages
            if (metadata.get(ExtraProperties.MESSAGE_SUBJECT) != null)
                messagesubject.add(metadata.get(ExtraProperties.MESSAGE_SUBJECT));
            if (metadata.get(ExtraProperties.MESSAGE_BODY) != null)
                messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
            if (metadata.get(ExtraProperties.MESSAGE_DATE) != null)
                messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
            // attachment
            if (metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT) != null || false)
                isattachment.add(metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
            if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) != null)
                attachmentname.add(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            if ((metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT) != null))
                numberofattachments.add(metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
            // contact
            if (metadata.get(ExtraProperties.USER_ACCOUNT) != null)
                useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
            if (metadata.get(ExtraProperties.USER_NAME) != null)
                username.add(metadata.get(ExtraProperties.USER_NAME));
            if (metadata.get(ExtraProperties.USER_EMAIL) != null)
                useremail.add(metadata.get(ExtraProperties.USER_EMAIL));
            if (metadata.get(ExtraProperties.USER_PHONE) != null)
                userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            if (metadata.get(ExtraProperties.USER_ADDRESS) != null)
                useraddress.add(metadata.get(ExtraProperties.USER_ADDRESS));
            if (metadata.get(ExtraProperties.USER_BIRTH) != null)
                userbirth.add(metadata.get(ExtraProperties.USER_BIRTH));
            if (metadata.get(ExtraProperties.USER_ORGANIZATION) != null)
                userorganization.add(metadata.get(ExtraProperties.USER_ORGANIZATION));
            if (metadata.get(ExtraProperties.USER_URLS) != null)
                userurls.add(metadata.get(ExtraProperties.USER_URLS));
            if (metadata.get(ExtraProperties.USER_NOTES) != null)
                usernotes.add(metadata.get(ExtraProperties.USER_NOTES));

            contentmd5.add(hdigest.toUpperCase());

        }

    }

    @Test
    public void testOutlookPSTParser() throws IOException, SAXException, TikaException {
        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("thisisatest", metadata.get(TikaCoreProperties.TITLE));

        }
    }

    @Test
    public void testOutlookPSTParserMetadata() throws IOException, SAXException, TikaException {

        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals(27, psttracker.foldertitle.size());
            assertEquals(1, psttracker.foldercreated.size());
            assertEquals(0, psttracker.foldermodified.size());
            assertEquals(18, psttracker.foldercomment.size());
            assertEquals(4, psttracker.messagesubject.size());
            assertEquals(4, psttracker.messagebody.size());
            assertEquals(3, psttracker.messagedate.size());
            assertEquals(1, psttracker.isattachment.size());
            assertEquals(4, psttracker.numberofattachments.size());
            assertEquals(1, psttracker.attachmentname.size());
            assertEquals(0, psttracker.useraccount.size());
            assertEquals(1, psttracker.username.size());
            assertEquals(1, psttracker.useremail.size());
            assertEquals(1, psttracker.userphone.size());
            assertEquals(1, psttracker.useraddress.size());
            assertEquals(0, psttracker.userbirth.size());
            assertEquals(1, psttracker.userorganization.size());
            assertEquals(1, psttracker.userurls.size());
            assertEquals(1, psttracker.usernotes.size());
            assertEquals(32, psttracker.contentmd5.size());

        }
    }

    @Test
    public void testOutlookPSTParserMetadataSimpleMessage() throws IOException, SAXException, TikaException {

        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("Caixa de Entrada", psttracker.foldertitle.get(9));
            assertEquals("Pasta Caixa de Entrada", psttracker.foldercomment.get(4));
            assertEquals("2021-04-27", psttracker.foldercreated.get(0).substring(0, 10));
            assertEquals("this is a test message", psttracker.messagesubject.get(0));
            assertEquals("Hello, this\nis a test message.\n\n\n  ", psttracker.messagebody.get(0));

        }
    }

    @Test
    public void testOutlookPSTParserMetadataRealMessages() throws IOException, SAXException, TikaException {

        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("Re: [sepinf-inc/IPED] WIP Parsers tests (#481)", psttracker.messagesubject.get(1));
            assertEquals("Solved by using my abstrackpackage instead of "
                    + "hardcoding the parser. Sorry, my mistake. Thanks for the tip!! " + "Good. I also confu(...)",
                    psttracker.messagebody.get(1));
            assertEquals("2021-04-26", psttracker.messagedate.get(0).substring(0, 10));

            assertEquals("Re: Solicita documentação para contrato de estágio na DITEC/PF.",
                    psttracker.messagesubject.get(2));
            assertEquals(
                    "Bom dia, Guilherme! A UnB assinou o TCE/PA? At.te,"
                            + " ELIZÃ‚NGELA RIBEIRO DE ANDRADE Fiscal do Contrato substituta" + " NAD/SELO/DITEC/(...)",
                    psttracker.messagebody.get(2));
            assertEquals("2021-03-29", psttracker.messagedate.get(1).substring(0, 10));

        }
    }

    @Test
    public void testOutlookPSTParserMetadataMessageAttach() throws IOException, SAXException, TikaException {

        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("This is a test message with attachment!!! ", psttracker.messagesubject.get(3));
            assertEquals("Hi there, it’s me again. Take a look\n" + "in this attachment. It is awesome.",
                    psttracker.messagebody.get(3));
            assertEquals("2021-04-27", psttracker.messagedate.get(2).substring(0, 10));
            assertEquals("lionel-animals-to-follow-on-instagram-1568319926.jpg", psttracker.attachmentname.get(0));
            assertEquals("true", psttracker.isattachment.get(0));
            assertEquals("1", psttracker.numberofattachments.get(3));

        }

    }

    @Test
    public void testOutlookPSTParserMetadataUserInfo() throws IOException, SAXException, TikaException {

        OutlookPSTParser parser = new OutlookPSTParser();
        parser.setRecoverDeleted(false);
        parser.setUseLibpffParser(false);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_sample.pst")) {
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("Sr. Guilherme Andreúce Monteiro", psttracker.username.get(0));
            assertEquals("guilhermeandreuce@gmail.com", psttracker.useremail.get(0));
            assertEquals("+55 (61) 986778855", psttracker.userphone.get(0));
            assertEquals("Condomínio da imaginação ruas dos bobos número 0", psttracker.useraddress.get(0));
            assertEquals("Polícia Federal", psttracker.userorganization.get(0));
            assertEquals("github.com/streeg", psttracker.userurls.get(0));

        }
    }

}
