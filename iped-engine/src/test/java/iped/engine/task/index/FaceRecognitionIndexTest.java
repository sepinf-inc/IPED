package iped.engine.task.index;

import static org.junit.Assert.*;

import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.Test;

import iped.engine.search.FaceRecognitionModels;
import iped.engine.task.index.IndexItem.KnnVector;
import iped.properties.ExtraProperties;

public class FaceRecognitionIndexTest {

    @Test
    public void legacyAndModernVectorsCoexistInOneLuceneIndex() throws Exception {
        try (ByteBuffersDirectory directory = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            Document legacy = new Document();
            IndexItem.addFaceVector(legacy, vector(128), null);
            writer.addDocument(legacy);
            for (String model : FaceRecognitionModels.MODERN_MODELS) {
                Document modern = new Document();
                IndexItem.addFaceVector(modern, vector(512), model);
                IndexItem.addFaceVector(modern, vector(512), model);
                assertNotNull(modern.getField(FaceRecognitionModels.vectorField(model, 512) + "_1"));
                writer.addDocument(modern);
            }
            // Untagged 512-d case imports retain bytes, but cannot impersonate a known model.
            Document unknown = new Document();
            IndexItem.addFaceVector(unknown, vector(512), null);
            writer.addDocument(unknown);
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                assertEquals(5, reader.numDocs());
                assertEquals(128 * 4, reader.document(0).getBinaryValue(ExtraProperties.FACE_ENCODINGS).length);
                assertEquals(512 * 4, reader.document(4).getBinaryValue(ExtraProperties.FACE_ENCODINGS).length);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void elasticMappingsAndExportKeepLegacyAndModernModelsSeparate() {
        Map<String, Object> mappings = ElasticSearchIndexTask.faceMappings();
        Map<String, Object> legacy = (Map<String, Object>) mappings.get("face_encoding");
        assertEquals(128, legacy.get("dimension"));
        assertEquals("l2", ((Map<String, Object>) legacy.get("method")).get("space_type"));
        assertEquals("dlib", ElasticSearchIndexTask.faceEncoding(new float[128], null).get(ExtraProperties.FACE_MODEL));
        for (String model : FaceRecognitionModels.MODERN_MODELS) {
            String field = "face_encoding_" + model;
            Map<String, Object> modern = (Map<String, Object>) mappings.get(field);
            assertEquals(512, modern.get("dimension"));
            assertEquals("cosinesimil", ((Map<String, Object>) modern.get("method")).get("space_type"));
            Map<String, Object> face = ElasticSearchIndexTask.faceEncoding(new float[512], model);
            assertTrue(face.containsKey(field));
            assertFalse(face.containsKey("face_encoding"));
            assertEquals(model, face.get(ExtraProperties.FACE_MODEL));
        }
        assertTrue(ElasticSearchIndexTask.faceEncoding(new float[512], null).containsKey("face_encoding_unknown"));
    }

    private static KnnVector vector(int dimensions) {
        double[] values = new double[dimensions];
        values[0] = 1;
        return new KnnVector(values);
    }
}
