package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RAGConfigApiKeyTest {

    @Test
    public void testEnvironmentKeyTakesPrecedence() {
        assertEquals("env-key", RAGConfig.resolveApiKey(" env-key ", "config-key"));
    }

    @Test
    public void testConfigKeyIsUsedWithoutEnvironmentKey() {
        assertEquals("config-key", RAGConfig.resolveApiKey(null, "config-key"));
        assertEquals("config-key", RAGConfig.resolveApiKey("  ", "config-key"));
        assertNull(RAGConfig.resolveApiKey(null, null));
    }
}
