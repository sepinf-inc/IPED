package iped.engine.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.engine.config.OCRConfig;
import iped.engine.config.OCRTestFixtures;
import iped.parsers.ocr.OCRParser;

public class OCRExecutionFlagTest {

    private final Map<String, String> saved = new LinkedHashMap<>();

    @Before
    public void saveProperties() {
        for (String name : new String[] { "tesseract.enabled", "tesseract.path", "ocr.language",
                "ocr.skipKnownFiles", "ocr.minFileSize", "ocr.maxFileSize", "ocr.pageSegMode",
                "pdfToImg.resolution", "pdfToImg.pdfLib", "pdfToImg.maxMem", "pdfparser.maxCharsToOcr",
                "ocr.processNonStandard", "ocr.maxConvImageSize" }) {
            saved.put(name, System.getProperty(name));
        }
    }

    @After
    public void restoreProperties() {
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private void setup(OCRConfig config) throws Exception {
        Method method = ParsingTask.class.getDeclaredMethod("setupOCROptions", OCRConfig.class);
        method.setAccessible(true);
        try {
            method.invoke(null, config);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw new AssertionError(e.getCause());
        }
    }

    @Test
    public void falseReplacesPreviouslyEnabledSystemProperty() throws Exception {
        System.setProperty(OCRParser.ENABLE_PROP, "true");
        setup(OCRTestFixtures.configured("false"));
        assertEquals("false", System.getProperty(OCRParser.ENABLE_PROP));
    }

    @Test
    public void successiveSetupInBothOrdersDoesNotRetainTrue() throws Exception {
        for (String value : new String[] { "false", "true", "false", "true", "false" }) {
            setup(OCRTestFixtures.configured(value));
            assertEquals(value, System.getProperty(OCRParser.ENABLE_PROP));
        }
    }

    @Test
    public void falseProfileFeedsDisabledParserWithoutReadingInputOrRunningTool() throws Exception {
        OCRConfig config = OCRTestFixtures.configured("true");
        config.processProperties(OCRTestFixtures.properties("false"));
        assertEquals(Boolean.FALSE, config.isOCREnabled());
        System.setProperty(OCRParser.ENABLE_PROP, "true"); // Deliberate stale state.
        setup(config);
        assertEquals("false", System.getProperty(OCRParser.ENABLE_PROP));

        // Enabled construction would try this nonexistent command and fail.
        // The assertion above prevents enabled construction in the negative control.
        System.setProperty(OCRParser.TOOL_PATH_PROP, "/synthetic-nonexistent-ocr-tool");
        OCRParser parser = new OCRParser();
        assertFalse(parser.isEnabled());
        InputStream forbidden = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Disabled OCR must not read input");
            }
        };
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(forbidden, handler, metadata, new ParseContext());
        assertEquals("", handler.toString());
        assertEquals(0, metadata.names().length);
    }

    @Test
    public void enabledSetupStillPublishesOptions() throws Exception {
        setup(OCRTestFixtures.configured("true"));
        assertEquals("true", System.getProperty(OCRParser.ENABLE_PROP));
        assertEquals("eng", System.getProperty(OCRParser.LANGUAGE_PROP));
        assertEquals("10000", System.getProperty(OCRParser.MIN_SIZE_PROP));
        assertEquals("150", System.getProperty("pdfToImg.resolution"));
        // No enabled OCRParser is constructed and no command is launched.
    }
}
