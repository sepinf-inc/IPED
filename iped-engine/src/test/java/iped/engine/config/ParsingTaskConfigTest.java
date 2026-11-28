package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import iped.exception.IPEDException;
import iped.parsers.standard.RawStringParser;
import iped.utils.UTF8Properties;

public class ParsingTaskConfigTest {

    private static final File SHIPPED_CONFIG = new File("../iped-app/resources/config");

    private static ParsingTaskConfig withRawStrings(String value) {
        UTF8Properties properties = new UTF8Properties();
        // 'auto' would need the OCR configuration
        properties.setProperty(ParsingTaskConfig.NUM_EXTERNAL_PARSERS, "2");
        if (value != null) {
            properties.setProperty("rawStrings", value);
        }
        ParsingTaskConfig config = new ParsingTaskConfig();
        config.processProperties(properties);
        return config;
    }

    /** Loads the shipped files in order, as the configuration directory does with a profile. */
    private static ParsingTaskConfig shipped(File... files) throws IOException {
        UTF8Properties properties = new UTF8Properties();
        for (File file : files) {
            properties.load(file);
        }
        properties.setProperty(ParsingTaskConfig.NUM_EXTERNAL_PARSERS, "2");
        ParsingTaskConfig config = new ParsingTaskConfig();
        config.processProperties(properties);
        return config;
    }

    @Test
    public void testRawStringsDefaultIsLatin1() {
        assertEquals(RawStringParser.LATIN1, new ParsingTaskConfig().getRawStrings());
        assertEquals(RawStringParser.LATIN1, withRawStrings(null).getRawStrings());
        assertEquals(RawStringParser.LATIN1, withRawStrings("  ").getRawStrings());
    }

    @Test
    public void testRawStringsValues() {
        assertEquals(RawStringParser.LATIN1, withRawStrings("latin1").getRawStrings());
        assertEquals(RawStringParser.ALL, withRawStrings("all").getRawStrings());
        assertEquals(RawStringParser.ALL, withRawStrings(" ALL ").getRawStrings());
        assertEquals(RawStringParser.LATIN1, withRawStrings("Latin1").getRawStrings());
    }

    @Test(expected = IPEDException.class)
    public void testRawStringsInvalidValue() {
        withRawStrings("utf8");
    }

    @Test
    public void testRawStringsOfShippedProfiles() throws IOException {
        File base = new File(SHIPPED_CONFIG, "conf/" + ParsingTaskConfig.CONF_FILE);
        assertEquals(RawStringParser.LATIN1, shipped(base).getRawStrings());

        File[] profiles = new File(SHIPPED_CONFIG, "profiles").listFiles(File::isDirectory);
        assertTrue(profiles != null && profiles.length > 1);
        for (File profile : profiles) {
            File file = new File(profile, "conf/" + ParsingTaskConfig.CONF_FILE);
            ParsingTaskConfig config = file.exists() ? shipped(base, file) : shipped(base);
            String expected = profile.getName().equals("forensic") ? RawStringParser.ALL : RawStringParser.LATIN1;
            assertEquals(profile.getName(), expected, config.getRawStrings());
        }
    }

}
