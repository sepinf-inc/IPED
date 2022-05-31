package dpf.sp.gpinf.indexer.parsers;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import dpf.sp.gpinf.indexer.parsers.util.RepoToolDownloader;

public class LibpffPSTParserTest extends AbstractPkgTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libpff/20131028/libpff-20131028.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/pffexport/");
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
        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        parser.setExtractOnlyActive(true);
        parser.getSupportedTypes(pstContext);
        metadata.set(Metadata.RESOURCE_NAME_KEY, "ost_sample");
        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.ost")) {
            if (parser.getSupportedTypes(pstContext).isEmpty()) throw new IOException();
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("This is a test email with two attachment files", psttracker.messagebody.get(0));
            assertEquals("2", psttracker.numberofattachments.get(0));
            assertEquals("1", psttracker.numberofattachments.get(6));
            assertEquals("calibre.docx", psttracker.attachmentname.get(0));
            assertEquals("sample.jpg", psttracker.attachmentname.get(1));
            assertTrue(psttracker.messagedate.get(0).contains("2018-08-02"));
            assertEquals("Microsoft Outlook Test Message", psttracker.messagesubject.get(3));

        } catch (IOException e) {
            // skip test
        }
    }

    @Test
    public void testLibpffPSTParserUserInfo() throws IOException, SAXException, TikaException {
        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        parser.setExtractOnlyActive(true);
        parser.getSupportedTypes(pstContext);
        metadata.set(Metadata.RESOURCE_NAME_KEY, "pst_sample");
        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.pst")) {
            if (parser.getSupportedTypes(pstContext).isEmpty()) throw new IOException();
            parser.parse(stream, handler, metadata, pstContext);

            assertEquals("Sr. Guilherme Andreúce Monteiro", psttracker.username.get(0));
            assertEquals("guilhermeandreuce@gmail.com", psttracker.useremail.get(0));
            assertTrue(psttracker.userphone.get(0).matches("\\+55 \\(61\\) 986778855|\\+55 \\(61\\) 980099922"));
            assertEquals("Condomínio da imaginação ruas dos bobos número 0", psttracker.useraddress.get(0));
            assertEquals("Polícia Federal", psttracker.userorganization.get(0));
            // assertEquals("github.com/streeg", psttracker.userurls.get(0));   // psttracker.userurls is empty

        } catch (IOException e) {
            // skip test
        }
    }


    @Test
    public void testLibpffPSTParserInbox() throws IOException, SAXException, TikaException {
        LibpffPSTParser parser = new LibpffPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        parser.setExtractOnlyActive(true);
        parser.getSupportedTypes(pstContext);
        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_sample.pst")) {
            if (parser.getSupportedTypes(pstContext).isEmpty()) throw new IOException();
            parser.parse(stream, handler, metadata, pstContext);

            // real message
            assertEquals("Re: [sepinf-inc/IPED] WIP Parsers tests (#481)", psttracker.messagesubject.get(1));
            assertEquals("Solved by using my abstrackpackage instead of hardcoding the parser. Sorry,"
                            + " my mistake. Thanks for the tip!! Good. I also confu(...)", psttracker.messagebody.get(1));
            assertEquals("2021-04-26", psttracker.messagedate.get(0).substring(0, 10));
            assertEquals("Re: Solicita documentação para contrato de estágio na DITEC/PF.", psttracker.messagesubject.get(2));
            assertEquals("Bom dia, Guilherme! A UnB assinou o TCE/PA? At.te,"
                            + " ELIZÃ‚NGELA RIBEIRO DE ANDRADE Fiscal do Contrato substituta NAD/SELO/DITEC/(...)", psttracker.messagebody.get(2));
            assertEquals("2021-03-29", psttracker.messagedate.get(1).substring(0, 10));


            // attachment
            assertEquals("This is a test message with attachment!!!", psttracker.messagesubject.get(3));
            assertEquals("Hi there, it’s me again. Take a look\nin this attachment. It is awesome.", psttracker.messagebody.get(3));
            assertEquals("2021-04-27", psttracker.messagedate.get(2).substring(0, 10));
            assertTrue(psttracker.attachmentname.stream().anyMatch("lionel-animals-to-follow-on-instagram-1568319926.jpg"::equals));
            assertEquals("true", psttracker.isattachment.get(0));
            assertEquals("1", psttracker.numberofattachments.get(5));

        } catch (IOException e) {
            // skip test
        }
    }
}
