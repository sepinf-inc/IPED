package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import iped.engine.config.RAGConfig;

public class RAGAssistantVisibilityTest {

    @Test
    public void testAssistantNeedsRagAndLlmQueries() {
        assertTrue(RAGAssistantPanel.isAssistantEnabled(config(true, true)));
        assertFalse(RAGAssistantPanel.isAssistantEnabled(config(true, false)));
        assertFalse(RAGAssistantPanel.isAssistantEnabled(config(false, true)));
        assertFalse(RAGAssistantPanel.isAssistantEnabled(null));
    }

    @Test
    public void testOnlyNonLocalProvidersAreExternal() {
        assertTrue(RAGAssistantPanel.externalServices(providers("local", "local")).isEmpty());
        assertEquals(java.util.Arrays.asList("LLM: gemini (m)"),
                RAGAssistantPanel.externalServices(providers("gemini", "local")));
        assertEquals(java.util.Arrays.asList("LLM: claude (m)", "Embeddings: remote (e)"),
                RAGAssistantPanel.externalServices(providers("claude", "remote")));
        assertTrue(RAGAssistantPanel.externalServices(null).isEmpty());
    }

    private static RAGConfig providers(String llm, String embedding) {
        return new RAGConfig() {
            @Override
            public String getLlmProvider() {
                return llm;
            }

            @Override
            public String getLlmModel() {
                return "m";
            }

            @Override
            public String getEmbeddingProvider() {
                return embedding;
            }

            @Override
            public String getEmbeddingModel() {
                return "e";
            }
        };
    }

    private static RAGConfig config(boolean rag, boolean llmQueries) {
        return new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return rag;
            }

            @Override
            public boolean isEnableLLMQueries() {
                return llmQueries;
            }
        };
    }
}
