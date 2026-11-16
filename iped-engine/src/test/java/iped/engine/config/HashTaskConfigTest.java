package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import iped.utils.UTF8Properties;

public class HashTaskConfigTest {

    @Test
    public void testDefaultAlgorithmsEmpty() {
        HashTaskConfig config = new HashTaskConfig();
        assertNotNull(config.getAlgorithms());
        assertTrue(config.getAlgorithms().isEmpty());
    }

    @Test
    public void testProcessPropertiesMultipleAlgorithms() {
        HashTaskConfig config = new HashTaskConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("hashes", "md5; sha-1; sha-256; sha-512; edonkey");
        config.processProperties(props);

        List<String> algos = config.getAlgorithms();
        assertEquals(5, algos.size());
        assertEquals("md5", algos.get(0));
        assertEquals("sha-1", algos.get(1));
        assertEquals("sha-256", algos.get(2));
        assertEquals("sha-512", algos.get(3));
        assertEquals("edonkey", algos.get(4));
    }

    @Test
    public void testProcessPropertiesWithWhitespace() {
        HashTaskConfig config = new HashTaskConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("hashes", "   md5  ;   sha-256   ");
        config.processProperties(props);

        List<String> algos = config.getAlgorithms();
        assertEquals(2, algos.size());
        assertEquals("md5", algos.get(0));
        assertEquals("sha-256", algos.get(1));
    }

    @Test
    public void testProcessPropertiesSingleAlgorithm() {
        HashTaskConfig config = new HashTaskConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("hashes", "sha-256");
        config.processProperties(props);

        List<String> algos = config.getAlgorithms();
        assertEquals(1, algos.size());
        assertEquals("sha-256", algos.get(0));
    }

    @Test
    public void testProcessPropertiesNull() {
        HashTaskConfig configNull = new HashTaskConfig();
        configNull.processProperties(new UTF8Properties());
        assertTrue(configNull.getAlgorithms().isEmpty());
    }

    @Test
    public void testShippedConfigFile() throws IOException {
        HashTaskConfig config = new HashTaskConfig();
        Path shippedConfig = Paths.get("../iped-app/resources/config/conf/HashTaskConfig.txt");
        config.processTaskConfig(shippedConfig);

        List<String> algos = config.getAlgorithms();
        assertEquals(3, algos.size());
        assertEquals("md5", algos.get(0));
        assertEquals("sha-1", algos.get(1));
        assertEquals("sha-256", algos.get(2));
    }

    @Test
    public void testMetadataProperties() {
        HashTaskConfig config = new HashTaskConfig();
        assertEquals(HashTaskConfig.ENABLE_PARAM, config.getTaskEnableProperty());
        assertEquals("enableHash", config.getTaskEnableProperty());
        assertEquals(HashTaskConfig.CONFIG_FILE, config.getTaskConfigFileName());
        assertEquals("HashTaskConfig.txt", config.getTaskConfigFileName());
    }
}
