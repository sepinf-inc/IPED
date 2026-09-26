package iped.engine.rag;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.config.RAGConfig;
import iped.engine.rag.RAGService.RAGSourceDoc;

/**
 * Unit tests for {@link RAGService}.
 *
 * Network-dependent methods (OpenSearch calls) are not exercised here ??? they
 * require an integration-test environment. Tests focus on:
 * <ul>
 *   <li>SQLite embedding cache (write ??? read round-trip).</li>
 *   <li>Float ??? byte array conversion helpers (via cache round-trip).</li>
 *   <li>Reciprocal Rank Fusion (RRF) ranking logic in
 *       {@link RAGService#performHybridSearch(String, float[])} via a
 *       subclass that stubs the OpenSearch calls.</li>
 *   <li>Parent-ID deduplication in {@link RAGService#findSimilarDocumentIds}.</li>
 *   <li>Provider selection: correct provider type created per config.</li>
 * </ul>
 */
public class RAGServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private RAGConfig config;

    @Before
    public void setUp() {
        config = new RAGConfig();
        setField(config, "embeddingProvider", "local");
        setField(config, "embeddingModel", "bge-m3");
        setField(config, "embeddingEndpoint", "http://localhost:11434/api/embeddings");
        setField(config, "embeddingDimensions", 4);
        setField(config, "llmProvider", "local");
        setField(config, "llmModel", "qwen3");
        setField(config, "llmEndpoint", "http://localhost:11434/v1");
        setField(config, "llmApiKey", "");
        setField(config, "chunkSimilarityThreshold", 0.0f);
        setField(config, "maxRetrievedChunks", 10);
    }

    // ---------------------------------------------------------------- cache tests

    @Test
    public void testEmbeddingCacheRoundTrip() throws Exception {
        File caseDir = tempFolder.newFolder("case1");
        RAGService.initialize(caseDir, config);
        RAGService service = RAGService.getInstance();

        float[] original = {0.1f, 0.2f, 0.3f, 0.4f};
        service.cacheEmbedding("hello world", "bge-m3", original);

        float[] retrieved = service.getCachedEmbedding("hello world", "bge-m3");
        assertNotNull("Cached embedding must not be null", retrieved);
        assertEquals("Embedding length mismatch", original.length, retrieved.length);
        for (int i = 0; i < original.length; i++) {
            assertEquals("Value mismatch at " + i, original[i], retrieved[i], 1e-6f);
        }
    }

    @Test
    public void testCacheMissReturnsNull() throws Exception {
        File caseDir = tempFolder.newFolder("case2");
        RAGService.initialize(caseDir, config);
        RAGService service = RAGService.getInstance();

        assertNull("Cache miss should return null",
                service.getCachedEmbedding("not-in-cache", "bge-m3"));
    }

    @Test
    public void testCacheDifferentModelsAreIsolated() throws Exception {
        File caseDir = tempFolder.newFolder("case3");
        RAGService.initialize(caseDir, config);
        RAGService service = RAGService.getInstance();

        float[] emb1 = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] emb2 = {0.0f, 1.0f, 0.0f, 0.0f};
        service.cacheEmbedding("text", "model-A", emb1);
        service.cacheEmbedding("text", "model-B", emb2);

        float[] r1 = service.getCachedEmbedding("text", "model-A");
        float[] r2 = service.getCachedEmbedding("text", "model-B");
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(1.0f, r1[0], 1e-6f);
        assertEquals(1.0f, r2[1], 1e-6f);
    }

    @Test
    public void testCacheOverwriteSameKey() throws Exception {
        File caseDir = tempFolder.newFolder("case4");
        RAGService.initialize(caseDir, config);
        RAGService service = RAGService.getInstance();

        float[] first  = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] second = {0.0f, 0.0f, 0.0f, 1.0f};
        service.cacheEmbedding("same-text", "model-X", first);
        service.cacheEmbedding("same-text", "model-X", second);

        float[] result = service.getCachedEmbedding("same-text", "model-X");
        assertNotNull(result);
        assertEquals("Latest value should win", 1.0f, result[3], 1e-6f);
    }

    // ---------------------------------------------------------------- RRF tests
    //
    // We use a local subclass that stubs performVectorSearch / performLexicalSearch
    // so we can exercise the RRF fusion logic without an OpenSearch cluster.

    @Test
    public void testHybridSearchRRFCombinesRanks() throws Exception {
        File caseDir = tempFolder.newFolder("case-rrf");
        RAGService.initialize(caseDir, config);

        RAGSourceDoc a = doc("1", "content-a", "a-001", 0.9f);
        RAGSourceDoc b = doc("2", "content-b", "b-001", 0.8f);
        RAGSourceDoc c = doc("3", "content-c", "c-001", 0.7f);

        // doc-A appears in both vector and lexical lists ??? higher RRF score
        RAGService service = new RAGService(caseDir, config) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(a, b);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(a, c);
            }
        };

        List<RAGSourceDoc> result = service.performHybridSearch("question", new float[4]);
        assertFalse("Result must not be empty", result.isEmpty());
        assertEquals("Doc-A (in both lists) should be ranked first", "a-001",
                result.get(0).contenttrackID);
    }

    @Test
    public void testHybridSearchRespectMaxChunks() throws Exception {
        File caseDir = tempFolder.newFolder("case-max");
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "maxRetrievedChunks", 2);
        RAGService.initialize(caseDir, cfg);

        RAGSourceDoc a = doc("1", "ca", "t1", 0.9f);
        RAGSourceDoc b = doc("2", "cb", "t2", 0.8f);
        RAGSourceDoc c = doc("3", "cc", "t3", 0.7f);

        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(a, b, c);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(a, b, c);
            }
        };

        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);
        assertTrue("Result should not exceed maxRetrievedChunks=2", result.size() <= 2);
    }

    /**
     * Verifies that chunkSimilarityThreshold is applied on the normalized RRF score
     * AFTER fusion, not on raw vector scores.
     *
     * Setup:
     *   vector: [highScore(rank1), lowScore(rank2)]
     *   lexical: [highScore(rank1)]           ??? only highScore has lexical support
     *
     * Expected RRF normalized scores:
     *   highScore = 1.0  (top score in both lists ??? normalized to 1.0)
     *   lowScore  ??? 0.50 (only vector rank2 contribution, no lexical ??? ~50% of top)
     *
     * With threshold = 0.85: lowScore (???0.50) should be filtered out.
     * With threshold = 0.85: highScore (1.0) should be kept.
     */
    @Test
    public void testHybridSearchSimilarityThresholdFiltersVectorHits() throws Exception {
        File caseDir = tempFolder.newFolder("case-thresh");
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "chunkSimilarityThreshold", 0.85f);
        setField(cfg, "maxRetrievedChunks", 10);
        RAGService.initialize(caseDir, cfg);

        // highScore appears in both lists ??? RRF normalized score = 1.0
        // lowScore appears only in vector list at rank 2 ??? RRF normalized score ??? 0.50
        RAGSourceDoc highScore = doc("1", "text", "t1", 0.9f);
        RAGSourceDoc lowScore  = doc("2", "text", "t2", 0.5f);

        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(highScore, lowScore);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(highScore); // only highScore has lexical support
            }
        };

        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);

        // highScore must be present (normalized RRF = 1.0 >= threshold 0.85)
        boolean foundHigh = result.stream().anyMatch(d -> "t1".equals(d.contenttrackID));
        assertTrue("High-relevance fragment must be kept", foundHigh);

        // lowScore must be filtered (normalized RRF ??? 0.50 < threshold 0.85)
        boolean foundLow = result.stream().anyMatch(d -> "t2".equals(d.contenttrackID));
        assertFalse("Low combined-relevance fragment must be filtered by post-RRF threshold", foundLow);
    }

    /**
     * Verifies the key benefit of the new design: a fragment that is weak on the
     * vector side (raw score below old threshold) but strong on the lexical side
     * is NOT lost ??? the RRF fusion rescues it.
     *
     * Setup:
     *   vector: [docA(rank1), docB(rank2)]   ??? docB has raw vector score < old threshold
     *   lexical: [docB(rank1), docA(rank2)]  ??? docB is the best lexical hit
     *
     * With threshold = 0.0 (disabled), both docs pass.
     * The important assertion is that docB is present despite a weak vector score,
     * because the lexical signal compensated it via RRF.
     */
    @Test
    public void testHybridSearchLexicalRescuesWeakVectorHit() throws Exception {
        File caseDir = tempFolder.newFolder("case-rescue");
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "chunkSimilarityThreshold", 0.0f); // no post-filter
        setField(cfg, "maxRetrievedChunks", 10);
        RAGService.initialize(caseDir, cfg);

        RAGSourceDoc docA = doc("1", "textA", "tA", 0.9f);
        RAGSourceDoc docB = doc("2", "textB", "tB", 0.55f); // weak vector, strong lexical

        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(docA, docB);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(docB, docA); // docB is best lexical hit
            }
        };

        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);

        boolean foundB = result.stream().anyMatch(d -> "tB".equals(d.contenttrackID));
        assertTrue("docB must be kept: weak vector score rescued by strong lexical rank", foundB);
    }

    // ---------------------------------------------------------------- deduplication

    @Test
    public void testFindSimilarDocumentIdsDeduplicatesParents() throws Exception {
        File caseDir = tempFolder.newFolder("case-dedup");
        RAGService.initialize(caseDir, config);

        RAGSourceDoc frag1 = doc("42", "text1", "42-frag1", 0.9f);
        RAGSourceDoc frag2 = doc("42", "text2", "42-frag2", 0.8f); // same itemId
        RAGSourceDoc frag3 = doc("99", "text3", "99-frag1", 0.7f);

        RAGService service = new RAGService(caseDir, config) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(frag1, frag2, frag3);
            }
        };

        List<String> ids = service.findSimilarDocumentIds(new float[4], 10);
        assertTrue("Item 42 must be in results", ids.contains("42"));
        assertTrue("Item 99 must be in results", ids.contains("99"));
        assertEquals("Duplicate parent must be deduped ??? 2 unique parents", 2, ids.size());
    }

    // ---------------------------------------------------------------- provider wiring

    @Test
    public void testLocalEmbeddingProviderSelectedByConfig() throws Exception {
        File caseDir = tempFolder.newFolder("prov-local-emb");
        setField(config, "embeddingProvider", "local");
        RAGService.initialize(caseDir, config);
        assertTrue("Expected LocalEmbeddingProvider",
                RAGService.getInstance().getEmbeddingProvider() instanceof LocalEmbeddingProvider);
    }

    @Test
    public void testRemoteEmbeddingProviderSelectedByConfig() throws Exception {
        File caseDir = tempFolder.newFolder("prov-remote-emb");
        setField(config, "embeddingProvider", "remote");
        RAGService.initialize(caseDir, config);
        assertTrue("Expected RemoteEmbeddingProvider",
                RAGService.getInstance().getEmbeddingProvider() instanceof RemoteEmbeddingProvider);
    }

    @Test
    public void testLocalLLMProviderSelectedByConfig() throws Exception {
        File caseDir = tempFolder.newFolder("prov-local-llm");
        setField(config, "llmProvider", "local");
        RAGService.initialize(caseDir, config);
        assertTrue("Expected LocalLLMProvider",
                RAGService.getInstance().getLlmProvider() instanceof LocalLLMProvider);
    }

    @Test
    public void testGeminiLLMProviderSelectedByConfig() throws Exception {
        File caseDir = tempFolder.newFolder("prov-gemini");
        setField(config, "llmProvider", "gemini");
        RAGService.initialize(caseDir, config);
        assertTrue("Expected GeminiLLMProvider",
                RAGService.getInstance().getLlmProvider() instanceof GeminiLLMProvider);
    }

    @Test
    public void testClaudeLLMProviderSelectedByConfig() throws Exception {
        File caseDir = tempFolder.newFolder("prov-claude");
        setField(config, "llmProvider", "claude");
        RAGService.initialize(caseDir, config);
        assertTrue("Expected ClaudeLLMProvider",
                RAGService.getInstance().getLlmProvider() instanceof ClaudeLLMProvider);
    }

    @Test
    public void testShouldEmbedFilterModes() {
        RAGConfig cfg = new RAGConfig();
        
        // 1. Test default Blacklist mode
        setField(cfg, "embeddingFilterMode", "blacklist");
        setField(cfg, "embeddingCategoryBlacklist", "^(Programs and Libraries|Image Disks)$");
        setField(cfg, "embeddingMimeTypeBlacklist", "^(application/octet-stream)$");
        
        // Allowed: Documents, PDF
        assertTrue(cfg.shouldEmbed(Arrays.asList("Documents"), "application/pdf"));
        // Blacklisted category: Programs and Libraries
        assertFalse(cfg.shouldEmbed(Arrays.asList("Programs and Libraries"), "application/pdf"));
        // Blacklisted mime: octet-stream
        assertFalse(cfg.shouldEmbed(Arrays.asList("Documents"), "application/octet-stream"));
        
        // 2. Test Whitelist mode
        setField(cfg, "embeddingFilterMode", "whitelist");
        setField(cfg, "embeddingCategoryWhitelist", "^(Documents|Chats)$");
        setField(cfg, "embeddingMimeTypeWhitelist", "^(text/plain)$");
        
        // Whitelisted category: Documents
        assertTrue(cfg.shouldEmbed(Arrays.asList("Documents"), "application/pdf"));
        // Whitelisted category: Chats
        assertTrue(cfg.shouldEmbed(Arrays.asList("Chats"), "application/octet-stream"));
        // Whitelisted mime: text/plain
        assertTrue(cfg.shouldEmbed(Arrays.asList("NonWhitelistedCategory"), "text/plain"));
        // Neither whitelisted: NonWhitelistedCategory + application/pdf
        assertFalse(cfg.shouldEmbed(Arrays.asList("NonWhitelistedCategory"), "application/pdf"));
    }

    // ---------------------------------------------------------------- helpers

    private static RAGSourceDoc doc(String itemId, String content, String trackId, float score) {
        RAGSourceDoc d = new RAGSourceDoc();
        d.itemId = itemId;
        d.content = content;
        d.contenttrackID = trackId;
        d.score = score;
        return d;
    }

    /** Shallow-clone a RAGConfig by copying fields reflectively. */
    private static RAGConfig cloneConfig(RAGConfig src) {
        RAGConfig dst = new RAGConfig();
        for (java.lang.reflect.Field f : src.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            try { f.set(dst, f.get(src)); } catch (IllegalAccessException ignore) {}
        }
        return dst;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
