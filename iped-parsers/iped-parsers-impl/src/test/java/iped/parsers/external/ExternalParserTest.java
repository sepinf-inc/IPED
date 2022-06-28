package iped.parsers.external;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.ExternalParserConfigGenerator;
import iped.parsers.util.RepoToolDownloader;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

// WIP: parsing only superfetch file for now
public class ExternalParserTest implements ExternalParsersConfigReaderMetKeys {
    private static String tmpPath = "/src/test/tmp/";
    private static String userDir = System.getProperty("user.dir");
    private static String absoluteTmpPath = userDir + tmpPath;
    private static String XMLFilePath = absoluteTmpPath + "/ExternalParsers.xml";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static File XMLFile;
    private static List<ExternalParser> parsers;
    private static ExternalParser superfetchParser, prefetchParser, recyclebinParser;

    private static ExternalParserConfigGenerator createDefaultExternalParserConfig(String name, String toolPath, String checkCommand,
        String command, MediaType mimeType, int firstLinesToIgnore) throws TikaException, TransformerException {

        ExternalParserConfigGenerator parserConfigGenerator = new ExternalParserConfigGenerator();
        parserConfigGenerator.setParserName(name);
        parserConfigGenerator.setWinToolPath(toolPath);
        parserConfigGenerator.setCheckCommand(checkCommand);
        parserConfigGenerator.setErrorCodes(1);
        parserConfigGenerator.setCommand(command);
        HashSet<MediaType> mimeTypes = new HashSet<>();
        mimeTypes.add(mimeType);
        parserConfigGenerator.addMimeTypes(mimeTypes);
        parserConfigGenerator.setOutputCharset("ISO-8859-1");
        parserConfigGenerator.setFirstLinesToIgnore(firstLinesToIgnore);

        return parserConfigGenerator;
    }

    @BeforeClass
    public static void setUp() throws IOException, TikaException, TransformerException, ParserConfigurationException, SAXException {
        XMLFile = new File(XMLFilePath);

        // download all external parsers tools
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libagdb/20181111.1/libagdb-20181111.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
            repoPath = "libyal/sccainfo/20170205.1/sccainfo-20170205.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
            repoPath = "abelcheung/rifiuti2/0.6.1/rifiuti2-0.6.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
        }

        // add SuperFetch parser configuration to xml file
        ExternalParserConfigGenerator superfetchConfigGenerator = createDefaultExternalParserConfig("SuperFetchParser", tmpPath + "libagdb/", 
            "agdbinfo -V", "agdbinfo ${INPUT}", MediaType.application("x-superfetch"), 0);
        superfetchConfigGenerator.writeDocumentToFile(XMLFile);

        // append Prefetch parser configuration to the same file
        ExternalParserConfigGenerator prefetchConfigGenerator = createDefaultExternalParserConfig("PrefetchParser", tmpPath + "sccainfo/",
            "sccainfo -V", "sccainfo ${INPUT}", MediaType.application("x-prefetch"), 0);
        prefetchConfigGenerator.writeDocumentToFile(XMLFile);

        // append RecycleBin parser
        ExternalParserConfigGenerator recyclebinConfigGenerator = createDefaultExternalParserConfig("RecycleBinParser", tmpPath + "rifiuti/",
            "rifiuti-vista -v", "rifiuti-vista -8 ${INPUT}", MediaType.application("x-recyclebin"), 3);
        recyclebinConfigGenerator.writeDocumentToFile(XMLFile);

        parsers = ExternalParsersConfigReader.read(new FileInputStream(XMLFile));
        superfetchParser = parsers.get(0);
        prefetchParser = parsers.get(1);
        recyclebinParser = parsers.get(2);
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (osName.startsWith("windows")) {
            FileUtils.deleteDirectory(new File(absoluteTmpPath));
        }
        XMLFile.delete();
    }
    
    @Test
    public void testSuperFetch() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_superfetchAgGlFgAppHistory.db";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {

            String[] checkCommand = new String[] { absoluteTmpPath + "libagdb/agdbinfo", "-V" };
            assumeTrue(ExternalParser.check(checkCommand, 1));
            superfetchParser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains(fileName));
            assertTrue(hts.contains("\\DEVICE\\HARDDISKVOLUME2"));
            assertTrue(hts.contains("Mar 25, 2015 11:08:36"));
            assertTrue(hts.contains("0xca0c7a4"));
            assertTrue(hts.contains("3573"));
            assertTrue(hts.contains("EXCEL.EXE"));
        }
    }

    @Test
    public void testPreFetch() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_prefetch.pf";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {

            String[] checkCommand = new String[] { absoluteTmpPath + "sccainfo/sccainfo", "-V" };
            assumeTrue(ExternalParser.check(checkCommand, 1));
            prefetchParser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            System.out.println(hts);

            assertTrue(hts.contains("TEAMS.EXE"));
            assertTrue(hts.contains("30"));
            assertTrue(hts.contains("0x92d8a64c"));
            assertTrue(hts.contains("Jun 08, 2022 12:09:38.412736500 UTC"));
            assertTrue(hts.toLowerCase().contains("users\\felipe costa\\appdata\\local\\microsoft\\teams\\current\\teams.exe"));
        }
    }

}
