package iped.engine.rag;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.config.RAGConfig;
import iped.engine.data.ItemId;
import iped.engine.rag.RAGService.RAGSourceDoc;
import iped.engine.task.index.IndexTask;
import iped.properties.ExtraProperties;

public class RAGSearchScopeTest {

    private final List<Directory> directories = new ArrayList<>();
    private final List<DirectoryReader> readers = new ArrayList<>();
    private final List<IIPEDSource> sources = new ArrayList<>();
    private MultiReader combinedReader;
    private StandardAnalyzer analyzer;
    private RAGConfig config;
    private RAGService service;
    private IndexSearcher searcher;

    @Before
    public void setUp() throws Exception {
        analyzer = new StandardAnalyzer();
        config = new RAGConfig() {
            @Override
            public String getEmbeddingEndpoint() {
                return null; // Scope tests use in-memory indexes, not a model service.
            }

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
        config.setEmbeddingDimensions(2);
        setConfig("chunkSimilarityThreshold", 0f);
        setConfig("maxRetrievedChunks", 10);
        service = new RAGService(null, config);
        service.setLuceneAnalyzer(analyzer);
    }

    @After
    public void tearDown() throws IOException {
        if (combinedReader != null) {
            combinedReader.close();
        }
        for (DirectoryReader reader : readers) {
            reader.close();
        }
        for (Directory directory : directories) {
            directory.close();
        }
        analyzer.close();
    }

    @Test
    public void testHybridSearchDoesNotLeakItemsWithSameNumericIdFromOtherCases() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 });
        addCase(1, "second", new int[] { 7 }, new float[] { 1, 0 });
        openSearch();
        RAGSearchScope scope = scope(new ItemId(1, 7));

        List<RAGSourceDoc> hits = service.performHybridSearchInScope("needle", new float[] { 1, 0 }, scope);

        assertEquals(1, hits.size());
        assertEquals("second-7#1", hits.get(0).contenttrackID);
        assertEquals(2, service.performHybridSearch("needle", new float[] { 1, 0 }).size());
    }

    @Test
    public void testSameIdCanBeSelectedInBothCases() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 });
        addCase(1, "second", new int[] { 7 }, new float[] { 1, 0 });
        openSearch();
        assertEquals(2, service.performHybridSearchInScope("needle", new float[] { 1, 0 },
                scope(new ItemId(0, 7), new ItemId(1, 7))).size());
    }

    @Test
    public void testSameTextInTwoCasesTakesOneSlotAndKeepsBothSources() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 }, "needle shared text");
        addCase(1, "second", new int[] { 7 }, new float[] { 1, 0 }, "needle shared text");
        openSearch();
        List<RAGSourceDoc> hits = service.performHybridSearch("needle", new float[] { 1, 0 });
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).identicalCopies.size());
        assertNotEquals(hits.get(0).contenttrackID, hits.get(0).identicalCopies.get(0).contenttrackID);
    }

    @Test
    public void testSourceBoundaryIsPreservedEvenForIdenticalGlobalIds() throws Exception {
        addCase(0, "same", new int[] { 7 }, new float[] { 1, 0 });
        addCase(1, "same", new int[] { 7 }, new float[] { 1, 0 });
        openSearch();
        org.apache.lucene.search.TopDocs hits = searcher.search(scope(new ItemId(1, 7)).createLuceneFilter(), 10);
        assertEquals(1, hits.scoreDocs.length);
        assertTrue(hits.scoreDocs[0].doc >= readers.get(0).maxDoc());
    }

    @Test
    public void testVectorFilterIsAppliedBeforeSelectingNearestCandidates() throws Exception {
        int[] excludedIds = new int[40];
        for (int i = 0; i < excludedIds.length; i++) {
            excludedIds[i] = i;
        }
        addCase(0, "excluded", excludedIds, new float[] { 1, 0 });
        addCase(1, "allowed", new int[] { 7 }, new float[] { 0, 1 });
        openSearch();
        setConfig("maxRetrievedChunks", 1);

        List<RAGSourceDoc> hits = service.performHybridSearchInScope("unmatched", new float[] { 1, 0 },
                scope(new ItemId(1, 7)));

        assertEquals(1, hits.size());
        assertEquals("allowed-7#1", hits.get(0).contenttrackID);
    }

    @Test
    public void testEmptyScopesNeverBecomeUnrestrictedSearches() throws Exception {
        // No searcher is installed: empty scopes must return before accessing any backend.
        RAGSearchScope empty = new RAGSearchScope(Collections.emptyList(), Collections.emptyList());
        assertTrue(service.performHybridSearchInScope("needle", new float[] { 1, 0 }, empty).isEmpty());
        assertTrue(service.performHybridSearch("needle", new float[] { 1, 0 }, Collections.emptyList()).isEmpty());
        assertTrue(service.performVectorSearch(new float[] { 1, 0 }, 5, Collections.emptyList()).isEmpty());
        assertTrue(service.performLexicalSearch("needle", 5, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testSelectionSnapshotDoesNotChangeWhenOriginalCollectionChanges() throws Exception {
        addCase(0, "first", new int[] { 0 }, new float[] { 1, 0 });
        List<IItemId> selected = new ArrayList<>(Collections.singletonList(new ItemId(0, 0)));
        RAGSearchScope scope = new RAGSearchScope(sources, selected);
        selected.clear();
        openSearch();
        assertFalse(scope.isEmpty());
        assertEquals(1, searcher.count(scope.createLuceneFilter()));
    }

    @Test
    public void testCaseSearcherKeepsItsOwnSimilarity() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 });
        combinedReader = new MultiReader(readers.toArray(new IndexReader[0]), false);
        searcher = new IndexSearcher(combinedReader);
        org.apache.lucene.search.similarities.Similarity caseSimilarity =
                new org.apache.lucene.search.similarities.ClassicSimilarity();
        searcher.setSimilarity(caseSimilarity);
        service.setLuceneSearcher(searcher);
        assertSame(caseSimilarity, searcher.getSimilarity());
        assertEquals(1, service.performHybridSearchInScope("needle", new float[] { 1, 0 }, scope(new ItemId(0, 7))).size());
    }

    @Test
    public void testAverageEmbeddingReadsOnlyTheSelectedCase() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 });
        addCase(1, "second", new int[] { 7 }, new float[] { 0, 1 });
        openSearch();
        assertArrayEquals(new float[] { 0, 1 }, service.getAverageEmbedding(scope(new ItemId(1, 7))), 1e-6f);
        assertArrayEquals(new float[] { 0.5f, 0.5f },
                service.getAverageEmbedding(scope(new ItemId(0, 7), new ItemId(1, 7))), 1e-6f);
    }

    @Test
    public void testAverageEmbeddingOfItemWithoutVectorsIsNull() throws Exception {
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 });
        openSearch();
        assertNull(service.getAverageEmbedding(scope(new ItemId(0, 0))));
    }

    @Test
    public void testComparisonQuestionFindsEachComparedDocument() throws Exception {
        // 10 terms without stop words: each document has only its own share of them
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 }, "portaria ministerial federal sistema texto");
        addCase(1, "second", new int[] { 7 }, new float[] { 1, 0 }, "decreto presidencial nacional texto");
        openSearch();
        List<RAGSourceDoc> hits = service.performLexicalSearch("Compare a abrangência do sistema de inteligência da "
                + "portaria ministerial federal com a do decreto presidencial nacional", 10);
        assertEquals(2, hits.size());
    }

    @Test
    public void testKindOfItemAskedForDoesNotMatchChatLabels() throws Exception {
        // a chat rendered in Portuguese repeats its labels; the subject is in another language
        addCase(0, "first", new int[] { 7 }, new float[] { 1, 0 },
                "Mensagem apagada recuperada. Mensagem apagada recuperada. Mensagem apagada recuperada.");
        addCase(1, "second", new int[] { 7 }, new float[] { 1, 0 }, "drugs and guns are the only way to get the money");
        openSearch();
        assertTrue(service.performLexicalSearch("há mensagem envolvendo transação ilegal?", 10).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownSourceIsRejected() {
        scope(new ItemId(99, 7));
    }

    private RAGSearchScope scope(IItemId... items) {
        return new RAGSearchScope(sources, Arrays.asList(items));
    }

    private void setConfig(String name, Object value) throws Exception {
        java.lang.reflect.Field field = RAGConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(config, value);
    }

    private void openSearch() throws IOException {
        combinedReader = new MultiReader(readers.toArray(new IndexReader[0]), false);
        searcher = new IndexSearcher(combinedReader);
        service.setLuceneSearcher(searcher);
    }

    private void addCase(int sourceId, String prefix, int[] ids, float[] vector) throws IOException {
        addCase(sourceId, prefix, ids, vector, "needle synthetic text " + prefix);
    }

    private void addCase(int sourceId, String prefix, int[] ids, float[] vector, String text) throws IOException {
        Directory directory = new ByteBuffersDirectory();
        directories.add(directory);
        Map<Integer, Integer> parentDocs = new HashMap<>();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            int docId = 0;
            for (int id : ids) {
                Document fragment = new Document();
                fragment.add(new StringField(IndexTask.FRAG_PARENT_NUMERIC_ID, Integer.toString(id), Field.Store.YES));
                fragment.add(new StringField(IndexTask.FRAG_TRACK_ID, prefix + "-" + id + "#1", Field.Store.YES));
                fragment.add(new StringField(IndexTask.HAS_EMBEDDING_FRAG, "1", Field.Store.YES));
                fragment.add(new TextField(IndexTask.RAG_CONTENT, text, Field.Store.NO));
                fragment.add(new StoredField(IndexTask.CONTENT_STORED, text));
                fragment.add(new KnnVectorField(IndexTask.CONTENT_EMBEDDING, vector));
                writer.addDocument(fragment);
                Document parent = new Document();
                parent.add(new StoredField(ExtraProperties.GLOBAL_ID, prefix + "-" + id));
                writer.addDocument(parent);
                parentDocs.put(id, docId + 1);
                docId += 2;
            }
        }
        DirectoryReader reader = DirectoryReader.open(directory);
        readers.add(reader);
        IndexSearcher atomicSearcher = new IndexSearcher(reader);
        sources.add((IIPEDSource) Proxy.newProxyInstance(IIPEDSource.class.getClassLoader(),
                new Class<?>[] { IIPEDSource.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSourceId": return sourceId;
                        case "getReader": return reader;
                        case "getSearcher": return atomicSearcher;
                        case "getLastId": return parentDocs.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                        case "getLuceneId": return parentDocs.getOrDefault((Integer) args[0], -1);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                }));
    }
}
