package iped.engine.rag;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        config = new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return true; // enableRAG comes from IPEDConfig.txt, not loaded in unit tests
            }
        };
        setField(config, "embeddingProvider", "local");
        setField(config, "embeddingModel", "bge-m3");
        setField(config, "embeddingEndpoint", null); // No metadata discovery in isolated unit tests.
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
    public void testHybridSearchSkipsFragmentsWithTextAlreadySelected() throws Exception {
        File caseDir = tempFolder.newFolder("case-dedup");
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "maxRetrievedChunks", 2);
        setField(cfg, "chunkSimilarityThreshold", 0f);
        RAGService.initialize(caseDir, cfg);

        // the same chat kept in two database backups, then another item
        RAGSourceDoc copy1 = doc("1", "same chat text", "backup1#1", 0.9f);
        RAGSourceDoc copy2 = doc("2", "same chat text ", "backup2#1", 0.9f);
        RAGSourceDoc other = doc("3", "other text", "other#1", 0.5f);

        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(copy1, copy2, other);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(copy1, copy2, other);
            }
        };

        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);
        assertEquals(2, result.size());
        assertEquals("backup1#1", result.get(0).contenttrackID);
        assertEquals("other#1", result.get(1).contenttrackID);
    }

    /** Synthetic chat: the main database names the contact, a backup keeps only the number. */
    private static String chat(String sender) {
        StringBuilder sb = new StringBuilder("5550001111 WhatsApp Chat - 5550001111\n");
        String[] lines = { "Did you get the package from the port yesterday", "Not yet they said customs is slow",
                "Call me when the truck arrives at the warehouse", "Sure I will send the photos of the boxes",
                "The buyer wants everything before the end of the month" };
        for (int i = 0; i < 4; i++) {
            for (String line : lines) {
                sb.append(i % 2 == 0 ? sender : "[Owner]").append('\n').append(line).append(" ").append(i)
                        .append("\n2016-02-1").append(i).append(" 01:26:07\n");
            }
        }
        return sb.toString();
    }

    @Test
    public void testChatKeptInABackupTakesOneSlot() throws Exception {
        File caseDir = tempFolder.newFolder("case-near-copies");
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "maxRetrievedChunks", 2);
        setField(cfg, "chunkSimilarityThreshold", 0f);
        RAGService.initialize(caseDir, cfg);

        RAGSourceDoc main = doc("1", chat("Ford (5550001111)"), "main#1", 1f);
        RAGSourceDoc backup = doc("2", chat("5550001111"), "backup#1", 0.9f);
        RAGSourceDoc other = doc("3", "Weekly report of the sales team with the numbers of the region", "other#1", 0.5f);
        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(main, backup, other);
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(main, backup, other);
            }
        };
        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);
        assertEquals(2, result.size());
        assertEquals("main#1", result.get(0).contenttrackID);
        assertEquals("backup#1", result.get(0).identicalCopies.get(0).contenttrackID);
        assertEquals("other#1", result.get(1).contenttrackID);
    }

    @Test
    public void testDifferentFragmentsAreNotCopies() {
        java.util.Set<String> a = RAGService.wordShingles(chat("Ford (5550001111)"));
        java.util.Set<String> b = RAGService.wordShingles(chat("5550001111"));
        java.util.Set<String> c = RAGService.wordShingles(
                "Meeting notes about the budget for the new office and the schedule of the move next year");
        assertTrue(RAGService.overlap(a, b) >= RAGService.NEAR_COPY_MIN_OVERLAP);
        assertTrue(RAGService.overlap(a, c) < RAGService.NEAR_COPY_MIN_OVERLAP);
        assertEquals(0, RAGService.overlap(a, java.util.Collections.emptySet()), 0);
    }

    @Test
    public void testCopiesDoNotPushOtherFragmentsDownTheRanking() throws Exception {
        // the chat of the main database and of two backups, then another fragment
        float withCopies = scoreAfter(Arrays.asList("main#1", "backup1#1", "backup2#1"));
        float withOneCopy = scoreAfter(Arrays.asList("main#1"));
        assertEquals(withOneCopy, withCopies, 0.0001f);
    }

    /** Normalized score of the keyword hit that comes right after copies of the same text. */
    private float scoreAfter(List<String> copyTrackIds) throws Exception {
        File caseDir = tempFolder.newFolder();
        RAGConfig cfg = cloneConfig(config);
        setField(cfg, "maxRetrievedChunks", 5);
        setField(cfg, "chunkSimilarityThreshold", 0f);
        RAGService.initialize(caseDir, cfg);
        List<RAGSourceDoc> lexical = new ArrayList<>();
        for (String track : copyTrackIds) {
            lexical.add(doc(track, "same chat text", track, 1f));
        }
        lexical.add(doc("other", "other text", "other#1", 0.5f));
        RAGService service = new RAGService(caseDir, cfg) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Collections.emptyList();
            }
            @Override
            public List<RAGSourceDoc> performLexicalSearch(String q, int k, java.util.Collection<String> allowedItemIds) {
                return lexical;
            }
        };
        List<RAGSourceDoc> result = service.performHybridSearch("q", new float[4]);
        assertEquals(2, result.size());
        assertEquals(copyTrackIds.size() - 1, result.get(0).identicalCopies.size());
        assertEquals("other#1", result.get(1).contenttrackID);
        return result.get(1).score;
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

    @Test
    public void testFindSimilarDocumentsKeepsEqualIdsOfDifferentCases() throws Exception {
        File caseDir = tempFolder.newFolder("case-similar");
        RAGSourceDoc first = doc("7", "a", "case-a-7#1", 0.9f);
        RAGSourceDoc firstAgain = doc("7", "b", "case-a-7#2", 0.8f);
        RAGSourceDoc other = doc("7", "c", "case-b-7#1", 0.7f);
        RAGService service = new RAGService(caseDir, config) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(first, firstAgain, other);
            }
        };
        List<RAGSourceDoc> similar = service.findSimilarDocuments(new float[4], 10, -1f);
        assertEquals(2, similar.size());
        assertEquals("case-a-7#1", similar.get(0).contenttrackID);
        assertEquals("case-b-7#1", similar.get(1).contenttrackID);
        // Lucene scores (1 + cos) / 2: 0.7 is cosine 0.4
        assertEquals(0.4f, similar.get(1).score, 1e-6f);
    }

    @Test
    public void testFindSimilarDocumentsAppliesCosineThresholdAndLimit() throws Exception {
        File caseDir = tempFolder.newFolder("case-similar-threshold");
        RAGService service = new RAGService(caseDir, config) {
            @Override
            public List<RAGSourceDoc> performVectorSearch(float[] v, int k, java.util.Collection<String> allowedItemIds) {
                return Arrays.asList(doc("1", "a", "g1#1", 1.0f), doc("2", "b", "g2#1", 0.9f),
                        doc("3", "c", "g3#1", 0.8f));
            }
        };
        // cosines 1.0, 0.8 and 0.6
        assertEquals(2, service.findSimilarDocuments(new float[4], 10, 0.75f).size());
        assertEquals(1, service.findSimilarDocuments(new float[4], 1, 0.75f).size());
    }

    @Test
    public void testLexicalQueryDropsStopWordsAndKeepsTerms() {
        assertEquals("pessoas trabalham CODE", RAGService.removeStopWords("Quem são as pessoas que trabalham na CODE"));
        assertEquals("Arthur Andersen asked leave Enron building",
                RAGService.removeStopWords("When was Arthur Andersen asked to leave the Enron building"));
        assertEquals("Nota Técnica nº 6 2024", RAGService.removeStopWords("a Nota Técnica nº 6 2024"));
        assertEquals("", RAGService.removeStopWords("qual é o de"));
        // the kind of item asked for is not a search term
        assertEquals("envolvendo transação ilegal", RAGService.removeStopWords("há mensagem envolvendo transação ilegal"));
        assertEquals("contract Enron", RAGService.removeStopWords("Is there any e-mail about the contract with Enron"));
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
        assertTrue(cfg.shouldEmbed(Arrays.asList("Chats"), "text/html"));
        // Blacklisted mime is an absolute veto, even for a whitelisted category or mime
        assertFalse(cfg.shouldEmbed(Arrays.asList("Chats"), "application/octet-stream"));
        setField(cfg, "embeddingMimeTypeBlacklist", "^(text/plain)$");
        setField(cfg, "embeddingMimeTypeBlacklistPattern", null);
        assertFalse(cfg.shouldEmbed(Arrays.asList("NonWhitelistedCategory"), "text/plain"));
        setField(cfg, "embeddingMimeTypeBlacklist", "^(application/octet-stream)$");
        setField(cfg, "embeddingMimeTypeBlacklistPattern", null);
        // Whitelisted mime: text/plain
        assertTrue(cfg.shouldEmbed(Arrays.asList("NonWhitelistedCategory"), "text/plain"));
        // Neither whitelisted: NonWhitelistedCategory + application/pdf
        assertFalse(cfg.shouldEmbed(Arrays.asList("NonWhitelistedCategory"), "application/pdf"));
    }

    @Test
    public void testUfedChatContainerAndItsPreviewAreEmbedded() {
        RAGConfig cfg = new RAGConfig();
        setField(cfg, "embeddingFilterMode", "whitelist");
        setField(cfg, "embeddingCategoryWhitelist", "^(Chats)$");

        // The container has no message text, but names the app and the participants of the chat:
        // it answers questions such as "which apps did A use to talk to B".
        assertTrue(cfg.shouldEmbed(Arrays.asList("Chats"), "application/x-ufed-chat"));
        assertTrue(cfg.shouldEmbed(Arrays.asList("Chats"), "application/x-ufed-chat-preview-whatsapp"));
        assertTrue(cfg.shouldEmbed(Arrays.asList("Chats"), "application/x-ufed-chat-preview"));
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
        RAGConfig dst = new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return src.isEnabled();
            }
        };
        for (java.lang.reflect.Field f : RAGConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            try { f.set(dst, f.get(src)); } catch (IllegalAccessException ignore) {}
        }
        return dst;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type.getSuperclass() != null && java.util.Arrays.stream(type.getDeclaredFields())
                    .noneMatch(field -> field.getName().equals(fieldName))) {
                type = type.getSuperclass(); // the config may be an anonymous subclass
            }
            java.lang.reflect.Field f = type.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
