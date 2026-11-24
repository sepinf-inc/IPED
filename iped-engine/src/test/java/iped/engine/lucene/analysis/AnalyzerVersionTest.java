package iped.engine.lucene.analysis;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class AnalyzerVersionTest {

    private static void commit(Directory dir, Integer version) throws IOException {
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
            if (version != null) {
                writer.setLiveCommitData(AnalyzerVersion.withVersion(writer.getLiveCommitData(), version).entrySet());
            }
            Document doc = new Document();
            doc.add(new StringField("id", "1", Field.Store.NO));
            writer.addDocument(doc);
            writer.commit();
        }
    }

    @Test
    public void testIndexWithoutVersionIsLegacy() throws IOException {
        try (Directory dir = new ByteBuffersDirectory()) {
            commit(dir, null);
            assertEquals(AnalyzerVersion.LEGACY, AnalyzerVersion.readFromIndex(dir, AnalyzerVersion.LATEST));
        }
    }

    @Test
    public void testVersionIsKeptAcrossCommits() throws IOException {
        try (Directory dir = new ByteBuffersDirectory()) {
            commit(dir, AnalyzerVersion.UNICODE);
            // a later session (e.g. --append) that does not set it again keeps it
            commit(dir, null);
            assertEquals(AnalyzerVersion.UNICODE, AnalyzerVersion.readFromIndex(dir, AnalyzerVersion.LEGACY));
        }
    }

    @Test
    public void testEmptyDirectoryUsesDefault() throws IOException {
        try (Directory dir = new ByteBuffersDirectory()) {
            assertEquals(AnalyzerVersion.LATEST, AnalyzerVersion.readFromIndex(dir, AnalyzerVersion.LATEST));
        }
    }

    @Test
    public void testCommitDataParsing() {
        assertEquals(AnalyzerVersion.LEGACY, AnalyzerVersion.fromCommitData(Collections.emptyMap()));
        assertEquals(AnalyzerVersion.LEGACY,
                AnalyzerVersion.fromCommitData(Collections.singletonMap(AnalyzerVersion.COMMIT_DATA_KEY, "x")));
        // a version from a newer IPED falls back to the latest known
        assertEquals(AnalyzerVersion.LATEST,
                AnalyzerVersion.fromCommitData(Collections.singletonMap(AnalyzerVersion.COMMIT_DATA_KEY, "99")));
        Map<String, String> other = new HashMap<>();
        other.put("other", "value");
        Map<String, String> data = AnalyzerVersion.withVersion(other.entrySet(), AnalyzerVersion.UNICODE);
        assertEquals("value", data.get("other"));
        assertEquals(AnalyzerVersion.UNICODE, AnalyzerVersion.fromCommitData(data));
    }
}
