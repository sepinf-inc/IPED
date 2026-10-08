package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.configuration.IConfigurationDirectory;
import iped.utils.UTF8Properties;

public class OCRConfigPrecedenceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private OCRConfig layers(String global, String profile) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path globalConfig = OCRTestFixtures.write(root.resolve("IPEDConfig.txt"), "enableOCR=" + global + "\n");
        Path options = OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "OCRLanguage=eng\n");
        Path profileConfig = OCRTestFixtures.write(root.resolve("profiles/test/IPEDConfig.txt"),
                profile == null ? "# inherit OCR\n" : "enableOCR=" + profile + "\n");
        OCRConfig config = new OCRConfig();
        config.processConfigs(Arrays.asList(globalConfig, options, profileConfig));
        return config;
    }

    @Test
    public void globalTrueProfileAbsentInheritsTrue() throws Exception {
        assertEquals(Boolean.TRUE, layers("true", null).isOCREnabled());
    }

    @Test
    public void explicitProfileFalseOverridesGlobalTrue() throws Exception {
        assertEquals(Boolean.FALSE, layers("true", "false").isOCREnabled());
    }

    @Test
    public void globalFalseProfileFalseStaysFalse() throws Exception {
        assertEquals(Boolean.FALSE, layers("false", "false").isOCREnabled());
    }

    @Test
    public void explicitProfileTrueCanEnableOverGlobalFalse() throws Exception {
        assertEquals(Boolean.TRUE, layers("false", "true").isOCREnabled());
    }

    @Test
    public void uninitializedNullThenAbsentDefaultsFalseWithoutDeclaringKey() {
        OCRConfig config = new OCRConfig();
        assertNull(config.isOCREnabled()); // Loader uses null as its not-loaded sentinel.
        UTF8Properties absent = OCRTestFixtures.properties(null);
        assertFalse(absent.containsKey("enableOCR"));
        config.processProperties(absent);
        assertEquals(Boolean.FALSE, config.isOCREnabled());
        assertFalse(absent.containsKey("enableOCR"));
        UTF8Properties explicit = OCRTestFixtures.properties("false");
        assertTrue(explicit.containsKey("enableOCR"));
    }

    @Test
    public void absentAndBlankValuesDoNotResetResolvedValue() {
        OCRConfig config = OCRTestFixtures.configured("true");
        config.processProperties(OCRTestFixtures.properties(null));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        config.processProperties(OCRTestFixtures.properties("  "));
        assertEquals(Boolean.TRUE, config.isOCREnabled());
    }

    @Test
    public void explicitFalseRemainsFalseAcrossLaterAbsentOptions() {
        OCRConfig config = OCRTestFixtures.configured("true");
        config.processProperties(OCRTestFixtures.properties(" false "));
        config.processProperties(OCRTestFixtures.properties(null));
        assertEquals(Boolean.FALSE, config.isOCREnabled());
    }

    @Test
    public void lastExplicitValueWinsInBothOrdersOnSameObject() throws Exception {
        OCRConfig config = new OCRConfig();
        for (String value : new String[] { "true", "false", "true", "false", " FALSE ", " TRUE " }) {
            config.processProperties(OCRTestFixtures.properties(value));
            assertEquals(Boolean.valueOf(value.trim()), config.isOCREnabled());
        }
        assertFalse(Modifier.isStatic(OCRConfig.class.getDeclaredField("enableOCR").getModifiers()));
    }

    @Test
    public void mergedPropertiesAndEffectiveStateAgreeForFalseOverride() throws Exception {
        OCRConfig config = layers("true", "false");
        assertEquals("false", config.getConfiguration().getProperty("enableOCR"));
        assertEquals(Boolean.FALSE, config.isOCREnabled());
        assertEquals("eng", config.getOcrLanguage());
    }

    @Test
    public void independentProfileLoadsInSameJvmDoNotShareOCRFlag() throws Exception {
        for (String[] order : new String[][] { { "true", "false" }, { "false", "true" } }) {
            for (String profile : order) {
                assertEquals(Boolean.valueOf(profile), layers("true", profile).isOCREnabled());
            }
        }
    }

    @Test
    public void sameObjectCanReloadCompleteLayerStacksWithoutStickyTrue() throws Exception {
        OCRConfig config = new OCRConfig();
        Path root = temporary.newFolder().toPath();
        Path global = root.resolve("IPEDConfig.txt");
        Path profile = root.resolve("profiles/test/IPEDConfig.txt");
        for (String[] row : new String[][] {
                { "true", "false", "false" }, { "true", null, "true" },
                { "false", "true", "true" }, { "false", null, "false" } }) {
            OCRTestFixtures.write(global, "enableOCR=" + row[0] + "\n");
            OCRTestFixtures.write(profile, row[1] == null ? "# inherit\n" : "enableOCR=" + row[1] + "\n");
            config.processConfigs(Arrays.asList(global, profile));
            assertEquals(Boolean.valueOf(row[2]), config.isOCREnabled());
        }
    }

    @Test
    public void realDirectoryAndManagerRespectOrderAndForcedReload() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path global = OCRTestFixtures.write(root.resolve("IPEDConfig.txt"), "enableOCR=true\n");
        Path options = OCRTestFixtures.write(root.resolve("conf/OCRConfig.txt"), "OCRLanguage=eng\n");
        Path profile = OCRTestFixtures.write(root.resolve("profiles/test/IPEDConfig.txt"), "# inherit\n");
        ConfigurationDirectory directory = new ConfigurationDirectory(global);
        directory.addPath(options.getParent());
        directory.addPath(profile);
        OCRConfig config = new OCRConfig();
        List<Path> resources = directory.lookUpResource(config);
        assertEquals(Arrays.asList(global, options, profile), resources);

        // A separate manager isolates this test from the process-wide singleton.
        Constructor<ConfigurationManager> constructor = ConfigurationManager.class
                .getDeclaredConstructor(IConfigurationDirectory.class);
        constructor.setAccessible(true);
        ConfigurationManager manager = constructor.newInstance(directory);
        manager.addObject(config);
        manager.loadConfig(config);
        assertEquals(Boolean.TRUE, config.isOCREnabled());
        OCRTestFixtures.write(profile, "enableOCR=false\n");
        manager.loadConfig(config);
        assertEquals(Boolean.TRUE, config.isOCREnabled()); // Cached until force reload.
        manager.loadConfigs(true);
        assertEquals(Boolean.FALSE, config.isOCREnabled());
    }
}
