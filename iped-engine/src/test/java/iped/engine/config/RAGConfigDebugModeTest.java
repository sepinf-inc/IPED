package iped.engine.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import iped.utils.UTF8Properties;

public class RAGConfigDebugModeTest {

    @Test
    public void testDebugModeIsOffByDefault() {
        RAGConfig config = new RAGConfig();
        config.processProperties(new UTF8Properties());
        assertFalse(config.isDebugMode());
    }

    @Test
    public void testDebugModeCanBeEnabled() {
        UTF8Properties props = new UTF8Properties();
        props.setProperty("debugMode", " true ");
        RAGConfig config = new RAGConfig();
        config.processProperties(props);
        assertTrue(config.isDebugMode());
    }
}
