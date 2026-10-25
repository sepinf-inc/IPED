package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.After;
import org.junit.Test;

import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.rag.RAGService.RAGSourceDoc;
import iped.properties.ExtraProperties;

public class RAGSourceItemTest {

    private final List<DirectoryReader> readers = new ArrayList<>();
    private final List<Directory> directories = new ArrayList<>();

    @After
    public void tearDown() throws IOException {
        for (DirectoryReader reader : readers) {
            reader.close();
        }
        for (Directory directory : directories) {
            directory.close();
        }
    }

    @Test
    public void testGlobalIdDistinguishesCasesWithSameItemId() throws IOException {
        List<IIPEDSource> sources = Arrays.asList(source(0, 7, "first-case"), source(1, 7, "second-case"));

        IItemId resolved = RAGAssistantPanel.resolveSourceItem(sources, doc("7", "second-case#2"));

        assertEquals(1, resolved.getSourceId());
        assertEquals(7, resolved.getId());
    }

    @Test
    public void testMissingGlobalIdDoesNotFallBackToAnotherItem() throws IOException {
        assertNull(RAGAssistantPanel.resolveSourceItem(Collections.singletonList(source(0, 7, "first-case")),
                doc("7", "missing-case#1")));
    }

    @Test
    public void testAmbiguousLegacySourceIsNotResolved() throws IOException {
        List<IIPEDSource> sources = Arrays.asList(source(0, 7, "first-case"), source(1, 7, "second-case"));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("7", null)));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("7", "legacy-fragment-id")));
    }

    @Test
    public void testUniqueLegacySourceIsResolved() throws IOException {
        IItemId resolved = RAGAssistantPanel.resolveSourceItem(Collections.singletonList(source(3, 7, "first-case")),
                doc("7", null));
        assertEquals(3, resolved.getSourceId());
        assertEquals(7, resolved.getId());
    }

    @Test
    public void testDuplicateGlobalIdIsNotResolvedArbitrarily() throws IOException {
        List<IIPEDSource> sources = Arrays.asList(source(0, 7, "same-item"), source(1, 7, "same-item"));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("7", "same-item#1")));
    }

    @Test
    public void testMissingNumericItemIsNotResolved() throws IOException {
        List<IIPEDSource> sources = Collections.singletonList(source(0, 7, "first-case"));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("8", null)));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("100", null)));
    }

    @Test
    public void testInvalidSourceIsNotResolved() throws IOException {
        List<IIPEDSource> sources = Collections.singletonList(source(0, 7, "first-case"));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, null));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc(null, null)));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("invalid", null)));
        assertNull(RAGAssistantPanel.resolveSourceItem(sources, doc("-1", null)));
    }

    private IIPEDSource source(int sourceId, int itemId, String globalId) throws IOException {
        Directory directory = new ByteBuffersDirectory();
        directories.add(directory);
        try (KeywordAnalyzer analyzer = new KeywordAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            Document document = new Document();
            document.add(new StringField(ExtraProperties.GLOBAL_ID, globalId, Field.Store.YES));
            writer.addDocument(document);
        }
        DirectoryReader reader = DirectoryReader.open(directory);
        readers.add(reader);
        IndexSearcher searcher = new IndexSearcher(reader);
        return (IIPEDSource) Proxy.newProxyInstance(IIPEDSource.class.getClassLoader(),
                new Class<?>[] { IIPEDSource.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSourceId":
                            return sourceId;
                        case "getLastId":
                            return itemId + 10;
                        case "getLuceneId":
                            return ((Integer) args[0]) == itemId ? 0 : -1;
                        case "getSearcher":
                            return searcher;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private RAGSourceDoc doc(String itemId, String fragmentId) {
        RAGSourceDoc doc = new RAGSourceDoc();
        doc.itemId = itemId;
        doc.contenttrackID = fragmentId;
        return doc;
    }
}
