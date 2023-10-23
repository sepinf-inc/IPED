package iped.parsers.database;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.RepoToolDownloader;

public class EDBParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libesedb/20151213.1/libesedb-20151213.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(EDBParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/esedbexport/");
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(EDBParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
        }
    }

    
    @Test
    public void testEDBParser() throws IOException, SAXException, TikaException {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, EDBParser.EDB.toString());
        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        EDBParser parser = new EDBParser();

        assumeFalse(parser.getSupportedTypes(context).isEmpty());

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/test_EntClientDb.edb")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertEquals(EDBParser.EDB.toString(), metadata.get(Metadata.CONTENT_TYPE));
            assertTrue(hts.contains("Sample Video"));
            assertTrue(hts.contains("Title of police video"));
            assertTrue(hts.contains("C:\\Users\\Felipe Costa\\Videos\\samples\\video.mp4"));
            assertTrue(hts.contains("C:\\Users\\Felipe Costa\\Videos\\samples\\ocean_video.mkv"));
            assertTrue(hts.contains("C:\\Users\\Felipe Costa\\Videos\\samples2\\police.mp4"));
            assertTrue(hts.contains("SeriesId"));
            assertTrue(hts.contains("ReleaseDate"));
            assertTrue(hts.contains("idxSeriesIdDateAddedReleaseDate"));
        }
    }

}
