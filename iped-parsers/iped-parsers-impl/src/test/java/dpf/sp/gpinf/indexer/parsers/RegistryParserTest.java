package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class RegistryParserTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @BeforeClass
    public static void beforeAll() {
        RegistryParser parser = new RegistryParser();
        ParseContext context = new ParseContext();
        assumeFalse(parser.getSupportedTypes(context).isEmpty());
    }

    @Test
    public void testRegistryParserSOFTWARE() throws IOException, SAXException, TikaException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(28000000);
        ParseContext context = new ParseContext();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "software");
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_software")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("Microsoft.VisualStudio.Tools.Office.Contract.v9.0"));
            assertTrue(hts.contains("Version=9.0.0.0"));
            assertTrue(hts.contains("Culture=neutral"));
            assertTrue(hts.contains("PublicKeyToken=b03f5f7f11d50a3a"));
        }

    }

    @Test
    public void testRegistryParserSAM() throws IOException, SAXException, TikaException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        metadata.set(Metadata.RESOURCE_NAME_KEY, "sam");
        try (InputStream stream = getStream("test-files/test_sam")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("ROOT"));
            assertTrue(hts.contains("Members"));
            assertTrue(hts.contains("guilh"));
            assertTrue(hts.contains("ServerDomainUpdates"));
        }

    }

    @Test
    public void testRegistryParserSECURITY() throws IOException, SAXException, TikaException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "security");
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_security")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("DefaultPassword"));
            assertTrue(hts.contains("S-1-5-80-3139157870-2983391045-3678747466-658725712-1809340420t"));
            assertTrue(hts.contains("MINWINPC"));
            assertTrue(hts.contains("WORKGROUP"));
        }

    }

    @Test
    public void testRegistryParserSYSTEM() throws IOException, SAXException, TikaException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(6000000);
        ParseContext context = new ParseContext();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "system");
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_system")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            assertTrue(hts.contains("C:\\Program Files\\VMware\\VMware Tools\\VMwareService.exe"));
            assertTrue(hts.contains("C:\\Windows\\system32\\svchost.exe"));
            assertTrue(hts.contains("C:\\Windows\\system32\\lsass.exe"));
            assertTrue(hts.contains("C:\\ProgramData\\VMware\\RawdskCompatibility"));
        }
    }

}
