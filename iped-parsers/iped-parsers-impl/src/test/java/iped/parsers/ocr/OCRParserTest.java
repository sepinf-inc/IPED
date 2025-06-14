package iped.parsers.ocr;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.cache.ICacheProvider;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.OCROutputFolder;
import iped.parsers.util.PDFToImage;
import iped.parsers.util.RepoToolDownloader;
import iped.utils.ExternalImageConverter;

public class OCRParserTest {
    private static String testRoot = System.getProperty("user.dir") + "/src/test";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static String OCR_OUTPUT_FOLDER_NAME = "ocr_output";
    private static Logger LOGGER = LoggerFactory.getLogger(OCRParser.class);

    @Rule
    public TestName testName = new TestName();

    private ICacheProvider cacheProvider = new ICacheProvider() {

        public ResourcePoolsBuilder getDefaultResourcePoolsBuilder() {
            return ResourcePoolsBuilder.heap(10);
        };

        @Override
        public <K, V> Cache<K, V> getOrCreateCache(String alias, Class<K> keyType, Class<V> valueType, ResourcePoolsBuilder resourcePoolsBuilder) {
            return CacheManagerBuilder.newCacheManagerBuilder() //
                    .build(true) //
                    .createCache(alias, //
                            CacheConfigurationBuilder.newCacheConfigurationBuilder(keyType, valueType, resourcePoolsBuilder));
        }

        @Override
        public File getCacheDir() {
            return new File(OCR_OUTPUT_FOLDER_NAME);
        }
    };

    @BeforeClass
    public static void setUpTool() throws IOException {
        if (osName.startsWith("windows")) {
            String repoPath = "tesseract/tesseract-zip/5.3.2-24-g3922_1/tesseract-zip-5.3.2-24-g3922_1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/");
            System.setProperty(OCRParser.TOOL_PATH_PROP, testRoot + "/tmp_tools/tesseract/");
        }

        // Checks if tesseract is present to then enable OCR parsing property
        try {
            String tesseractPath = System.getProperty(OCRParser.TOOL_PATH_PROP, "") + "tesseract";
            List<String> tessInfo = OCRParser.checkVersionInfo(tesseractPath, "-v");
            System.out.println("Detected tesseract version " + tessInfo.get(0));
            System.setProperty(OCRParser.ENABLE_PROP, "true");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Skipping tesseract tests...");
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void tearDownTool() throws IOException {
        if (osName.startsWith("windows")) {
            File tool_path = new File(System.clearProperty(OCRParser.TOOL_PATH_PROP));
            FileUtils.deleteDirectory(tool_path.getParentFile());
        }
        FileUtils.deleteDirectory(new File(OCR_OUTPUT_FOLDER_NAME));
    }


    @Test
    public void testOCRParserPNG() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "image/png");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "por");
        context.set(ICacheProvider.class, cacheProvider);

        String hts = "";
        OCRParser parser = new OCRParser();
        InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.png");
        assumeTrue(parser.isEnabled());

        parser.parse(stream, handler, metadata, context);
        hts = handler.toString();

        assertTrue(hts.contains("Oi, tudo bem?"));
        assertTrue(hts.contains("Tudo certo, o que estamos fazendo"));
        assertTrue(hts.contains("aqui?"));
        assertTrue(hts.contains("Isso é um print para testar o"));
        assertTrue(hts.contains("OCRParser"));
        assertTrue(hts.contains("2:51 PM"));
        assertTrue(hts.toLowerCase().contains("boa sorte galera"));

        parser.close();
    }

    private void assertPDFParsing() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "application/pdf");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "por");
        context.set(ICacheProvider.class, cacheProvider);

        String hts = "";
        OCRParser parser = new OCRParser();
        InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.pdf");
        assumeTrue(parser.isEnabled());

        parser.parse(stream, handler, metadata, context);
        hts = handler.toString();

        assertTrue(hts.contains("RISC-V UNICICLO"));
        assertTrue(hts.contains("Instruction [31-0]"));
        assertTrue(hts.contains("MemtoReg"));
        assertTrue(hts.contains("Lógico-Aritméticas com imediato:"));
        assertTrue(hts.contains("and s6, s5, s4"));
        assertTrue(hts.contains("00000048 005324b3"));
        assertTrue(hts.contains("as memórias de instruções e dados."));

        parser.close();
    }

    @Test
    public void testOCRParserPDF() throws IOException, SAXException, TikaException, SQLException {
        assertPDFParsing();
        // convert pdf using icepdf
        System.setProperty(PDFToImage.PDFLIB_PROP, "icepdf");
        FileUtils.deleteDirectory(new File(OCR_OUTPUT_FOLDER_NAME));
        assertPDFParsing();
        // convert pdf externally (fails because the classpath when testing lacks the necessary jars)
        // System.setProperty(PDFToImage.EXTERNAL_CONV_PROP, "true");
        // FileUtils.deleteDirectory(new File(OCR_OUTPUT_FOLDER_NAME));
        // assertPDFParsing();
    }

    
    @Test
    public void testOCRParserTIFF() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "image/tiff");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");
        context.set(ICacheProvider.class, cacheProvider);

        String hts = "";
        OCRParser parser = new OCRParser();
        InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.tiff");
        assumeTrue(parser.isEnabled());

        parser.parse(stream, handler, metadata, context);
        hts = handler.toString();

        assertTrue(hts.contains("Literature must rest always on a principle"));
        assertTrue(hts.contains("times and places are one; the stuff he deals with"));
        assertTrue(hts.contains("extract from THE ENGLISH RENAISSANCE"));
        assertTrue(hts.contains("The Quick Brown"));
        assertTrue(hts.contains("Fox Jumps Over"));
        assertTrue(hts.contains("The Lazy Dog."));
        assertTrue(hts.contains("abcdefghijklmnopq"));
        assertTrue(hts.contains("01234567890 01234567890"));

        parser.close();
    }

    @Test
    public void testOCRParserPSD() throws IOException, SAXException, TikaException, SQLException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "image/vnd.adobe.photoshop");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");
        context.set(ICacheProvider.class, cacheProvider);

        String hts = "";
        OCRParser parser = new OCRParser();
        InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.psd");
        assumeTrue(parser.isEnabled());

        setUpImageMagick();
        String magickDir = System.getProperty(ExternalImageConverter.winToolPathPrefixProp, "");
        assumeTrue(isImageMagickInstalled(magickDir));

        parser.parse(stream, handler, metadata, context);
        hts = handler.toString();

        assertTrue(hts.contains("Parsing non-standard file format"));
        assertTrue(hts.contains("SAMPLE TEXT"));
        assertTrue(hts.contains("Centered Text"));

        parser.close();
    }

    @Test
    public void testOCRParserSVG() throws IOException, SAXException, TikaException, SQLException, InterruptedException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, testName.getMethodName(), null, null, testName.getMethodName(), false);
        context.set(ItemInfo.class, itemInfo);
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, "image/svg+xml");
        context.set(OCROutputFolder.class, new OCROutputFolder(new File(OCR_OUTPUT_FOLDER_NAME)));
        System.setProperty(OCRParser.LANGUAGE_PROP, "eng");
        context.set(ICacheProvider.class, cacheProvider);

        String hts = "";
        OCRParser parser = new OCRParser();
        InputStream stream = this.getClass().getResourceAsStream("/test-files/test_OCR.svg");
        assumeTrue(parser.isEnabled());

        setUpImageMagick();
        String magickDir = System.getProperty(ExternalImageConverter.winToolPathPrefixProp, "");
        assumeTrue(isImageMagickInstalled(magickDir));

        parser.parse(stream, handler, metadata, context);
        hts = handler.toString();

        assertTrue(hts.contains("The Quick Brown"));
        assertTrue(hts.contains("Fox Jumps Over"));
        assertTrue(hts.contains("The Lazy Dog"));
        assertTrue(hts.contains("0123456789"));

        parser.close();
    }


    private void setUpImageMagick() throws IOException {
        if (System.getProperty(ExternalImageConverter.enabledProp, "").isEmpty()) {
            System.setProperty(ExternalImageConverter.enabledProp, "true");
            if (osName.startsWith("windows")) {
                String repoPath = "org/imagemagick/imagemagick-zip/7.1.1-19-q8-x64/imagemagick-zip-7.1.1-19-q8-x64.zip";
                RepoToolDownloader.unzipFromUrl(repoPath, testRoot + "/tmp_tools/tools");
                System.setProperty(ExternalImageConverter.winToolPathPrefixProp, testRoot + "/tmp_tools");
            }
        }
    }

    private boolean isImageMagickInstalled(String magickDir) {
        magickDir += osName.startsWith("windows") ? "/tools/imagemagick/magick" : "magick";

        try {
            Process process;
            int result = -1;
            try {
                process = Runtime.getRuntime().exec(magickDir + " -version");
                result = process.waitFor();
            } catch (IOException e) {
                if (!osName.startsWith("windows")) {
                    // try with older command
                    process = Runtime.getRuntime().exec("convert -version");
                    result = process.waitFor();
                }
            }
            if (result != 0) {
                throw new IOException("Returned error code " + result);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Skipping imagemagick tests... " + e.toString());
            return false;
        }
        return true;
    }
}
