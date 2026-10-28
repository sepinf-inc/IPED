package iped.engine.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatisticsSecretMaskTest {

    @Test
    public void testConfigurationSecretsAreMasked() {
        assertEquals("{llmModel=qwen3:8b, llmApiKey=***, password=***, username=analyst}",
                Statistics.maskSecrets("{llmModel=qwen3:8b, llmApiKey=sk-synthetic-123, password=s3cret!, username=analyst}"));
    }

    @Test
    public void testKeysEndingInSecretTermsAreMasked() {
        assertEquals("{authToken=***, clientSecret=***}",
                Statistics.maskSecrets("{authToken=abc123, clientSecret=xyz}"));
    }

    @Test
    public void testEmptySecretStaysEmpty() {
        assertEquals("{llmApiKey=, llmModel=x}", Statistics.maskSecrets("{llmApiKey=, llmModel=x}"));
    }

    @Test
    public void testCommandLineSecretsAreMasked() {
        assertEquals("iped.jar -d img.E01 -p *** -Xelastic=user:analyst;password:***;indexName:case",
                Statistics.maskSecrets(
                        "iped.jar -d img.E01 -p Synthetic#1 -Xelastic=user:analyst;password:Synth3tic;indexName:case"));
        assertEquals("iped.jar -password *** -o out", Statistics.maskSecrets("iped.jar -password abc -o out"));
    }

    @Test
    public void testTextWithoutSecretsIsUnchanged() {
        String text = "{enableRAG=true, embeddingModel=bge-m3, maxTokens=2560}";
        assertEquals(text, Statistics.maskSecrets(text));
    }
}
