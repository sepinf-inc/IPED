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

    private void expectConflict(OCRConfig config, Path resource, Path layer) throws Exception {
        try {
            config.processConfig(resource);
            fail("Conflicting explicit declarations must abort configuration loading");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("enableOCR"));
            assertTrue(expected.getMessage().contains(layer.resolve("IPEDConfig.txt").toString()));
            assertTrue(expected.getMessage().contains(layer.resolve("conf/OCRConfig.txt").toString()));
            assertTrue(expected.getMessage().contains("true"));
            assertTrue(expected.getMessage().contains("false"));
            assertTrue(expected.getMessage().contains("Remove enableOCR from OCRConfig.txt"));
        }
    }

    @Test
    public void rejectsMainTrueLegacyFalseBeforeMerging() throws Exception {
        Path root = layer("true", "false");
        OCRConfig config = new OCRConfig();
        expectConflict(config, root.resolve("IPEDConfig.txt"), root);
        assertNull(config.isOCREnabled());
        assertTrue(config.getConfiguration().isEmpty());
    }

    @Test
    public void rejectsMainFalseLegacyTrue() throws Exception {
        Path root = layer("false", "true");
        expectConflict(new OCRConfig(), root.resolve("IPEDConfig.txt"), root);
    }

    @Test
    public void rejectsConflictWhenLegacyFileIsLoadedFirst() throws Exception {
        Path root = layer("true", "false");
        expectConflict(new OCRConfig(), root.resolve("conf/OCRConfig.txt"), root);
    }

    @Test
    public void rejectsReverseConflictWhenLegacyFileIsLoadedFirst() throws Exception {
        Path root = layer("false", "true");
        expectConflict(new OCRConfig(), root.resolve("conf/OCRConfig.txt"), root);
    }

    @Test
    public void acceptsMatchingTrueAndFalseInEitherOrder() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(value, value);
            for (boolean reverse : new boolean[] { false, true }) {
                OCRConfig config = new OCRConfig();
                Path main = root.resolve("IPEDConfig.txt");
                Path legacy = root.resolve("conf/OCRConfig.txt");
                config.processConfigs(reverse ? Arrays.asList(legacy, main) : Arrays.asList(main, legacy));
                assertEquals(Boolean.valueOf(value), config.isOCREnabled());
                assertEquals("eng", config.getOcrLanguage());
            }
        }
    }

    @Test
    public void acceptsWhitespaceAndCaseInsensitiveMatchingBooleans() throws Exception {
        Path root = layer(" TRUE ", "true");
        OCRConfig config = new OCRConfig();
        config.processConfig(root.resolve("IPEDConfig.txt"));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
    }

    @Test
    public void blankOrAbsentLegacyKeysDoNotConflict() throws Exception {
        for (String value : new String[] { null, "   " }) {
            Path root = layer("true", value);
            OCRConfig config = new OCRConfig();
            config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
            assertEquals(Boolean.TRUE, config.isOCREnabled());
            assertEquals("eng", config.getOcrLanguage());
        }
    }

    @Test
    public void legacyOnlyDeclarationRemainsSupported() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(null, value);
            OCRConfig config = new OCRConfig();
            config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
            assertEquals(Boolean.valueOf(value), config.isOCREnabled());
        }
    }

    @Test
    public void missingSiblingRemainsSupported() throws Exception {
        Path root = temporary.newFolder().toPath();
        OCRTestFixtures.write(root.resolve("IPEDConfig.txt"), "enableOCR=true\n");
        OCRConfig config = new OCRConfig();
        config.processConfig(root.resolve("IPEDConfig.txt"));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        Path legacy = OCRTestFixtures.write(temporary.newFolder().toPath().resolve("conf/OCRConfig.txt"), "enableOCR=false\n");
        config.processConfig(legacy);
        assertEquals(Boolean.FALSE, config.isOCREnabled());
    }

    @Test
    public void commentedLegacyDeclarationIsIgnored() throws Exception {
        Path root = layer("true", null);
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "# enableOCR=false\nOCRLanguage=por\n");
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        assertEquals("por", config.getOcrLanguage());
    }

    @Test
    public void matchingRootLegacyDoesNotBlockProfileOverrideInEitherDirection() throws Exception {
        for (String value : new String[] { "true", "false" }) {
            Path root = layer(value, value);
            String override = Boolean.toString(!Boolean.valueOf(value));
            Path profile = OCRTestFixtures.write(root.resolve("profiles/test/IPEDConfig.txt"), declaration(override));
            Path options = OCRTestFixtures.write(root.resolve("profiles/test/conf/OCRConfig.txt"), "OCRLanguage=por\n");
            OCRConfig config = new OCRConfig();
            config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt"), profile, options));
            assertEquals(Boolean.valueOf(override), config.isOCREnabled());
            assertEquals("por", config.getOcrLanguage());
        }
    }

    @Test
    public void matchingProfileLegacyCanDifferFromRoot() throws Exception {
        Path root = layer("true", "true");
        Path profile = OCRTestFixtures.write(root.resolve("profiles/test/IPEDConfig.txt"), declaration("false"));
        Path legacy = OCRTestFixtures.write(root.resolve("profiles/test/conf/OCRConfig.txt"), declaration("false"));
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt"), profile, legacy));
        assertEquals(Boolean.FALSE, config.isOCREnabled());
    }

    @Test
    public void rejectsConflictingProfileWithoutMergingItsFlag() throws Exception {
        Path root = layer("true", "true");
        Path profile = root.resolve("profiles/test");
        OCRTestFixtures.write(profile.resolve("IPEDConfig.txt"), declaration("false"));
        OCRTestFixtures.write(profile.resolve("conf/OCRConfig.txt"), declaration("true"));
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        expectConflict(config, profile.resolve("IPEDConfig.txt"), profile);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        assertEquals("true", config.getConfiguration().getProperty("enableOCR"));
    }

    @Test
    public void reloadDetectsEditedConflictAndAcceptsCorrection() throws Exception {
        Path root = layer("true", "true");
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("false"));
        expectConflict(config, root.resolve("IPEDConfig.txt"), root);
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "OCRLanguage=eng\n");
        config.processConfigs(Arrays.asList(root.resolve("IPEDConfig.txt"), root.resolve("conf/OCRConfig.txt")));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
    }

    @Test
    public void managerPropagatesConflictOnInitialLoadAndForcedReload() throws Exception {
        Path root = layer("true", "true");
        ConfigurationDirectory directory = new ConfigurationDirectory(root.resolve("IPEDConfig.txt"));
        directory.addPath(root.resolve("conf"));
        Constructor<ConfigurationManager> constructor = ConfigurationManager.class.getDeclaredConstructor(IConfigurationDirectory.class);
        constructor.setAccessible(true);
        ConfigurationManager manager = constructor.newInstance(directory);
        OCRConfig config = new OCRConfig();
        manager.addObject(config);
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("false"));
        try {
            manager.loadConfig(config);
            fail("Manager must propagate configuration conflict");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("enableOCR"));
        }
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("true"));
        manager.loadConfig(config);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), declaration("false"));
        try {
            manager.loadConfigs(true);
            fail("Forced reload must propagate configuration conflict");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("enableOCR"));
        }
    }
}
