package iped.parsers.external;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.RepoToolDownloader;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

// WIP: parsing only superfetch file for now
public class ExternalParserTest implements ExternalParsersConfigReaderMetKeys {
    private static String toolsPath = "/src/test/tmp_tools/";
    private static String absoluteToolsPath = System.getProperty("user.dir") + toolsPath;
    private static String osName = System.getProperty("os.name").toLowerCase();

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libagdb/20181111.1/libagdb-20181111.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteToolsPath);
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
        if (osName.startsWith("windows")) {
            FileUtils.deleteDirectory(new File(absoluteToolsPath));
        }
    }
    
    @Test
    public void testSuperFetch() throws IOException, TikaException, SAXException {
        // not building parser from an xml configuration file yet
        ExternalParser parser = new ExternalParser();
        parser.setParserName("SuperFetchParser");
        parser.setToolPath(toolsPath + "libagdb");
        parser.setCommand("agdbinfo ${INPUT}");

        Set<MediaType> types = new HashSet<MediaType>();
        types.add(MediaType.parse("application/x-superfetch"));
        parser.setSupportedTypes(types);
        parser.setCharset("ISO-8859-1");

        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_superfetchAgGlFgAppHistory.db";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {

            assumeTrue(osName.startsWith("windows"));
            parser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains(fileName));
            assertTrue(hts.contains("Creation time"));
            assertTrue(hts.contains("Mar 25, 2015 11:08:36.956950100 UTC"));

        }

    }
}
