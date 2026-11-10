package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.configuration.IConfigurationDirectory;

/** Reject the deprecated key, independently of its value or other resources. */
public class OCRConfigConflictTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path layer(String main, String legacy) throws Exception {
        Path root = temporary.newFolder().toPath();
        OCRTestFixtures.write(root.resolve("IPEDConfig.txt"), declaration(main));
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration(legacy) + "OCRLanguage=eng\n");
        return root;
    }

    private String declaration(String value) {
        return value == null ? "# no enableOCR declaration\n" : "enableOCR=" + value + "\n";
    }

    private void expectRejected(OCRConfig config, Path resource) throws Exception {
        try {
            config.processConfig(resource);
            fail("enableOCR in OCRConfig.txt must abort configuration loading");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(resource.toString()));
            assertTrue(expected.getMessage().contains("Remove enableOCR from OCRConfig.txt"));
            assertTrue(expected.getMessage().contains("IPEDConfig.txt"));
        }
    }

    @Test
    public void rejectsLegacyTrueBeforeMerging() throws Exception {
        Path root = layer(null, "true");
        OCRConfig config = new OCRConfig();
        expectRejected(config, root.resolve("conf/OCRConfig.txt"));
        assertNull(config.isOCREnabled());
        assertTrue(config.getConfiguration().isEmpty());
    }

    @Test
    public void rejectsLegacyFalse() throws Exception {
        Path root = layer(null, "false");
        expectRejected(new OCRConfig(), root.resolve("conf/OCRConfig.txt"));
    }

    @Test
    public void rejectsMatchingValuesInEitherLoadingOrder() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(value, value);
            for (boolean legacyFirst : new boolean[] { false, true }) {
                OCRConfig config = new OCRConfig();
                if (!legacyFirst) {
                    config.processConfig(root.resolve("IPEDConfig.txt"));
                }
                expectRejected(config, root.resolve("conf/OCRConfig.txt"));
            }
        }
    }

    @Test
    public void rejectsDivergentValuesInEitherLoadingOrder() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(value, Boolean.toString(!Boolean.valueOf(value)));
            for (boolean legacyFirst : new boolean[] { false, true }) {
                OCRConfig config = new OCRConfig();
                try {
                    config.processConfigs(legacyFirst
                            ? Arrays.asList(root.resolve("conf/OCRConfig.txt"), root.resolve("IPEDConfig.txt"))
                            : Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
                    fail("Legacy declaration must abort either loading order");
                } catch (IOException expected) {
                    assertTrue(expected.getMessage().contains("Remove enableOCR from OCRConfig.txt"));
                }
            }
        }
    }

    @Test
    public void rejectsLegacyOnlyFileWithoutMainSibling() throws Exception {
        Path legacy = OCRTestFixtures.write(temporary.newFolder().toPath().resolve("conf/OCRConfig.txt"),
                "enableOCR=true\n");
        expectRejected(new OCRConfig(), legacy);
    }

    @Test
    public void rejectsEmptyAndWhitespaceValues() throws Exception {
        for (String value : new String[] { "", "   " }) {
            Path root = layer("true", value);
            expectRejected(new OCRConfig(), root.resolve("conf/OCRConfig.txt"));
        }
    }

    @Test
    public void rejectsAnyValueWithoutBooleanComparison() throws Exception {
        for (String value : new String[] { " TRUE ", "invalid", "0" }) {
            Path root = layer("true", value);
            expectRejected(new OCRConfig(), root.resolve("conf/OCRConfig.txt"));
        }
    }

    @Test
    public void commentedLegacyDeclarationIsIgnored() throws Exception {
        Path root = layer("true", null);
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"),
                "# enableOCR=false\nOCRLanguage=por\n");
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        assertEquals("por", config.getOcrLanguage());
    }

    @Test
    public void absentLegacyKeyPreservesOtherOCROptions() throws Exception {
        Path root = layer("false", null);
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        assertEquals(Boolean.FALSE, config.isOCREnabled());
        assertEquals("eng", config.getOcrLanguage());
    }

    @Test
    public void mainConfigDoesNotRequireOrReadSibling() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path main = OCRTestFixtures.write(root.resolve("IPEDConfig.txt"), "enableOCR=true\n");
        OCRConfig config = new OCRConfig();
        config.processConfig(main);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "enableOCR=false\n");
        config.processConfig(main); // Only the resource being loaded is validated.
        assertEquals(Boolean.TRUE, config.isOCREnabled());
    }

    @Test
    public void profileOverridesRemainSupportedInEitherDirection() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(value, null);
            String override = Boolean.toString(!Boolean.valueOf(value));
            Path profile = OCRTestFixtures.write(root.resolve("profiles/test/IPEDConfig.txt"), declaration(override));
            Path options = OCRTestFixtures.write(root.resolve("profiles/test/conf/OCRConfig.txt"), "OCRLanguage=por\n");
            OCRConfig config = new OCRConfig();
            config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt"),
                    profile, options));
            assertEquals(Boolean.valueOf(override), config.isOCREnabled());
            assertEquals("por", config.getOcrLanguage());
        }
    }

    @Test
    public void rejectsProfileLegacyKeyBeforeMergingThatResource() throws Exception {
        Path root = layer("true", null);
        Path legacy = OCRTestFixtures.write(root.resolve("profiles/test/conf/OCRConfig.txt"),
                "enableOCR=true\nOCRLanguage=por\n");
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        expectRejected(config, legacy);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        assertEquals("eng", config.getOcrLanguage());
    }

    @Test
    public void reloadRejectsAddedLegacyKeyAndAcceptsRemoval() throws Exception {
        Path root = layer("true", null);
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("true"));
        expectRejected(config, root.resolve("conf/OCRConfig.txt"));
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "OCRLanguage=por\n");
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        assertEquals("por", config.getOcrLanguage());
    }

    @Test
    public void managerPropagatesRejectionOnInitialLoadAndForcedReload() throws Exception {
        Path root = layer("true", "true");
        ConfigurationDirectory directory = new ConfigurationDirectory(root.resolve("IPEDConfig.txt"));
        directory.addPath(root.resolve("conf"));
        Constructor<ConfigurationManager> constructor = ConfigurationManager.class
                .getDeclaredConstructor(IConfigurationDirectory.class);
        constructor.setAccessible(true);
        ConfigurationManager manager = constructor.newInstance(directory);
        OCRConfig config = new OCRConfig();
        manager.addObject(config);
        try {
            manager.loadConfig(config);
            fail("Manager must propagate legacy-key rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Remove enableOCR from OCRConfig.txt"));
        }
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "OCRLanguage=eng\n");
        manager.loadConfig(config);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("true"));
        try {
            manager.loadConfigs(true);
            fail("Forced reload must propagate legacy-key rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Remove enableOCR from OCRConfig.txt"));
        }
    }

    @Test
    public void rejectsParsedKeyWithSurroundingWhitespace() throws Exception {
        Path legacy = OCRTestFixtures.write(temporary.newFolder().toPath().resolve("conf/OCRConfig.txt"),
                "  enableOCR  = true\n");
        expectRejected(new OCRConfig(), legacy);
    }
}
