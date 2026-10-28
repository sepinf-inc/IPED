package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import iped.engine.config.RAGConfig;
import iped.engine.task.index.IndexTask;

/** A case indexed with one embedding model must not be searched with another. */
public class RAGEmbeddingModelCheckTest {

    @Test
    public void testSameModelIsAccepted() throws Exception {
        try (Directory dir = index("bge-m3", "bge-m3"); DirectoryReader reader = DirectoryReader.open(dir)) {
            assertNull(RAGService.checkIndexedEmbeddingModel(reader, "bge-m3"));
            assertNull(RAGService.checkIndexedEmbeddingModel(reader, "BGE-M3:latest"));
        }
    }

    @Test
    public void testOtherModelIsRejected() throws Exception {
        try (Directory dir = index("bge-m3"); DirectoryReader reader = DirectoryReader.open(dir)) {
            String reason = RAGService.checkIndexedEmbeddingModel(reader, "nomic-embed-text");
            assertTrue(reason, reason.contains("bge-m3") && reason.contains("nomic-embed-text"));
        }
    }

    @Test
    public void testIndexMixingModelsIsRejected() throws Exception {
        try (Directory dir = index("bge-m3", "nomic-embed-text"); DirectoryReader reader = DirectoryReader.open(dir)) {
            String reason = RAGService.checkIndexedEmbeddingModel(reader, "bge-m3");
            assertTrue(reason, reason.contains("bge-m3, nomic-embed-text"));
        }
    }

    @Test
    public void testIndexWithoutRecordedModelIsNotChecked() throws Exception {
        try (Directory dir = index(); DirectoryReader reader = DirectoryReader.open(dir)) {
            assertNull(RAGService.checkIndexedEmbeddingModel(reader, "bge-m3"));
        }
    }

    @Test
    public void testMismatchDisablesTheAssistantForTheCase() throws Exception {
        RAGConfig config = new RAGConfig() {
            @Override
            public String getEmbeddingEndpoint() {
                return null; // no model service: the check reads only the index
            }

            @Override
            public String getEmbeddingModel() {
                return "nomic-embed-text";
            }

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
        config.setEmbeddingDimensions(2);
        RAGService service = new RAGService(null, config);
        try (Directory dir = index("bge-m3"); DirectoryReader reader = DirectoryReader.open(dir)) {
            service.setLuceneSearcher(new IndexSearcher(reader));
            assertFalse(service.isAvailable());
            assertTrue(service.getUnavailableReason().contains("not comparable"));
            assertNull(service.getEmbedding("pergunta sintética"));
            assertFalse(service.retryIfUnavailable());

            service.setLuceneSearcher(null);
            assertEquals(null, service.getUnavailableReason());
        } finally {
            service.close();
        }
    }

    private static Directory index(String... models) throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (String model : models) {
                Document fragment = new Document();
                fragment.add(new StringField(IndexTask.HAS_EMBEDDING_FRAG, "1", Field.Store.YES));
                fragment.add(new StringField(IndexTask.EMBEDDING_MODEL, model, Field.Store.NO));
                writer.addDocument(fragment);
            }
            writer.addDocument(new Document());
        }
        return dir;
    }
}
