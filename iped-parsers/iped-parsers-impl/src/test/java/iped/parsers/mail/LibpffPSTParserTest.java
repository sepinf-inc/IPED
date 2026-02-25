package iped.parsers.mail;


import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.content.TikaManager;
import iped.content.TikaManager.TikaConfigAlreadyInitializedException;
import iped.parsers.mail.OutlookPSTParserTest.EmbeddedPSTParser;
import iped.parsers.util.AbstractPkgTest;
import iped.parsers.util.RepoToolDownloader;

public class LibpffPSTParserTest extends AbstractPkgTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();

    protected EmbeddedPSTParser psttracker;
    protected ParseContext pstContext;

    private LibpffPSTParser parser;
    private Metadata metadata;
    private ContentHandler handler;
    private Boolean pffexportInstalled = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libpff/20131028/libpff-20131028.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/pffexport/");
        }

        try {
            TikaManager.initializeTikaConfig(false, false);
        } catch (TikaConfigAlreadyInitializedException ignore) {
        }

        psttracker = new EmbeddedPSTParser();
        pstContext = new ParseContext();
        pstContext.set(Parser.class, psttracker);

        parser = new LibpffPSTParser();
        metadata = new Metadata();
        handler = new DefaultHandler();

        try {
            assumeFalse(parser.getSupportedTypes(pstContext).isEmpty());
        } catch (AssumptionViolatedException e) {
            pffexportInstalled = false;
        }

    }

    @Override
    protected void tearDown() throws Exception {
        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(LibpffPSTParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
        }
    }

    @Test
    public void testLibpffPSTParserOST() throws IOException, SAXException, TikaException {
        parser.setExtractOnlyActive(true);
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_sample.ost");

        if (pffexportInstalled) {
            try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.ost")) {
                parser.parse(stream, handler, metadata, pstContext);

                assertTrue(psttracker.messagebody.toString().contains("This is a test email with two attachment files"));
                assertEquals(4, psttracker.numberofattachments.stream().mapToInt(Integer::parseInt).sum());

                String attachmentNames = psttracker.attachmentname.toString();
                String messageDates = psttracker.messagedate.toString();
                assertTrue(attachmentNames.contains("calibre.docx"));
                assertTrue(attachmentNames.contains("sample.jpg"));
                assertTrue(messageDates.contains("2018-08-02T06:15:12"));
                assertTrue(messageDates.contains("2018-04-24T04:31:17"));
                assertTrue(psttracker.messagesubject.toString().contains("Microsoft Outlook Test Message"));
            }
        }
    }

    @Test
    public void testLibpffPSTParserUserInfo() throws IOException, SAXException, TikaException {
        parser.setExtractOnlyActive(true);
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_sample.pst");

        if (pffexportInstalled) {
            try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.pst")) {
                parser.parse(stream, handler, metadata, pstContext);

                assertEquals("Sr. Guilherme Andreúce Monteiro", psttracker.username.get(0));
                assertEquals("guilhermeandreuce@gmail.com", psttracker.useremail.get(0));
                assertTrue(psttracker.userphone.get(0).matches("\\+55 \\(61\\) 986778855|\\+55 \\(61\\) 980099922"));
                assertEquals("Condomínio da imaginação ruas dos bobos número 0", psttracker.useraddress.get(0));
                assertEquals("Polícia Federal", psttracker.userorganization.get(0));
                // assertEquals("github.com/streeg", psttracker.userurls.get(0)); //
                // psttracker.userurls is empty
            }
        }
    }

    @Test
    public void testLibpffPSTParserInbox() throws IOException, SAXException, TikaException {
        parser.setExtractOnlyActive(true);

        if (pffexportInstalled) {
            try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.pst")) {
                parser.parse(stream, handler, metadata, pstContext);

                String messageSubjects = psttracker.messagesubject.toString();
                String messageDates = psttracker.messagedate.toString();
                String messageBodies = psttracker.messagebody.toString();

                // real message
                assertTrue(messageSubjects.contains("Re: [sepinf-inc/IPED] WIP Parsers tests (#481)"));
                assertTrue(messageSubjects.contains("Re: Solicita documentação para contrato de estágio na DITEC/PF."));
                assertTrue(
                        messageBodies.contains("Solved by using my abstrackpackage instead of hardcoding the parser. Sorry,"
                                + " my mistake. Thanks for the tip!! Good. I also confu(...)"));
                assertTrue(messageDates.contains("2021-04-26"));
                assertTrue(messageBodies.contains("Bom dia, Guilherme! A UnB assinou o TCE/PA? At.te,"
                        + " ELIZÃ‚NGELA RIBEIRO DE ANDRADE Fiscal do Contrato substituta NAD/SELO/DITEC/(...)"));
                assertTrue(messageDates.contains("2021-03-29"));

                // attachment
                assertTrue(messageSubjects.contains("This is a test message with attachment!!!"));
                assertTrue(
                        messageBodies.contains("Hi there, it’s me again. Take a look\nin this attachment. It is awesome."));
                assertTrue(messageDates.contains("2021-04-27"));
                assertTrue(psttracker.attachmentname.contains("lionel-animals-to-follow-on-instagram-1568319926.jpg"));
                assertEquals(2, psttracker.numberofattachments.stream().mapToInt(Integer::parseInt).sum());

            }
        }
    }

}
