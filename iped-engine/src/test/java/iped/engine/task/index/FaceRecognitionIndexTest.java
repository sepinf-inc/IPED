package iped.engine.task.index;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;

import iped.engine.search.FaceRecognitionModels;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.search.LoadIndexFields;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.Item;
import iped.data.IItem;
import iped.engine.task.index.IndexItem.KnnVector;
import iped.properties.ExtraProperties;

public class FaceRecognitionIndexTest {

    @Test
    public void modernFaceCanBeAppendedToOriginalLegacyLuceneSchema() throws Exception {
        try (ByteBuffersDirectory directory = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
                float[] oldVector = new float[128];
                oldVector[0] = 1;
                byte[] bytes = IndexItem.convFloatArrayToByteArray(oldVector);
                Document original = new Document();
                original.add(new StoredField("name", "legacy"));
                // Original IPED used the same field name for all three representations.
                original.add(new SortedSetDocValuesField(ExtraProperties.FACE_ENCODINGS, new BytesRef(bytes)));
                original.add(new StoredField(ExtraProperties.FACE_ENCODINGS, bytes));
                original.add(new KnnVectorField(ExtraProperties.FACE_ENCODINGS, oldVector));
                writer.addDocument(original);
            }
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
                Document modern = new Document();
                modern.add(new StoredField("name", "modern"));
                IndexItem.addFaceVector(modern, vector(512), "auraface");
                modern.add(new StoredField(ExtraProperties.FACE_MODEL, "auraface"));
                modern.add(new SortedDocValuesField(ExtraProperties.FACE_MODEL, new BytesRef("auraface")));
                writer.addDocument(modern);
                Document importedLegacy = new Document();
                importedLegacy.add(new StoredField("name", "imported-legacy"));
                IndexItem.addFaceVector(importedLegacy, vector(128), null);
                IndexItem.addFaceVector(importedLegacy, vector(128), null);
                writer.addDocument(importedLegacy);
                writer.forceMerge(1);
                try (DirectoryReader reader = DirectoryReader.open(writer)) {
                    assertEquals(3, reader.numDocs());
                    Document oldStored = storedDocument(reader, "legacy");
                    Document modernStored = storedDocument(reader, "modern");
                    assertEquals(128 * 4, oldStored.getBinaryValue(ExtraProperties.FACE_ENCODINGS).length);
                    assertNull(modernStored.getBinaryValue(ExtraProperties.FACE_ENCODINGS));
                    assertEquals(512 * 4, modernStored
                            .getBinaryValue(FaceRecognitionModels.storageField("auraface", 512)).length);
                    assertEquals(128, reader.leaves().get(0).reader().getFieldInfos()
                            .fieldInfo(ExtraProperties.FACE_ENCODINGS).getVectorDimension());
                    assertEquals(512, reader.leaves().get(0).reader().getFieldInfos()
                            .fieldInfo(FaceRecognitionModels.vectorField("auraface", 512)).getVectorDimension());
                    assertEquals(128, reader.leaves().get(0).reader().getFieldInfos()
                            .fieldInfo("face_encodings1").getVectorDimension());
                    IPEDMultiSource source = sourceForStoredItemTests();
                    IItem oldItem = IndexItem.getItem(oldStored, source, false);
                    IItem modernItem = IndexItem.getItem(modernStored, source, false);
                    assertNotNull(oldItem);
                    assertNotNull(modernItem);
                    assertEquals(128 * 4, storedFeatures(oldItem).get(0).length);
                    assertEquals(512 * 4, storedFeatures(modernItem).get(0).length);
                    assertEquals("auraface", modernItem.getExtraAttribute(ExtraProperties.FACE_MODEL));
                    SimilarFacesSearch.validateReference(oldItem);
                    SimilarFacesSearch.validateReference(modernItem);
                    // IPEDReader's separate case import path uses this same physical-to-logical restore.
                    Item importedItem = new Item();
                    IndexItem.restoreFaceEncodings(modernStored, importedItem);
                    assertArrayEquals(storedFeatures(modernItem).get(0), storedFeatures(importedItem).get(0));
                    assertEquals("auraface", importedItem.getExtraAttribute(ExtraProperties.FACE_MODEL));
                    Item importedLegacyItem = new Item();
                    IndexItem.restoreFaceEncodings(storedDocument(reader, "imported-legacy"), importedLegacyItem);
                    assertEquals(2, storedFeatures(importedLegacyItem).size());
                }
            }
        }
    }

    @Test
    public void modernOnlyCaseRestoresLogicalFieldsAndEmbeddingOrder() throws Exception {
        try (ByteBuffersDirectory directory = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            Document modern = new Document();
            modern.add(new StoredField("name", "modern-only"));
            KnnVector first = vector(512);
            double[] other = new double[512];
            other[1] = 1;
            IndexItem.addFaceVector(modern, first, "auraface");
            IndexItem.addFaceVector(modern, new KnnVector(other), "auraface");
            modern.add(new StoredField(ExtraProperties.FACE_MODEL, "auraface"));
            modern.add(new SortedDocValuesField(ExtraProperties.FACE_MODEL, new BytesRef("auraface")));
            writer.addDocument(modern);
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                IPEDMultiSource source = sourceForStoredItemTests(reader);
                IItem item = IndexItem.getItem(reader.document(0), source, false);
                List<byte[]> features = storedFeatures(item);
                assertEquals(2, features.size());
                assertArrayEquals(IndexItem.convFloatArrayToByteArray(IndexItem.convDoubleToFloatArray(first.getArray())), features.get(0));
                assertArrayEquals(IndexItem.convFloatArrayToByteArray(IndexItem.convDoubleToFloatArray(other)), features.get(1));
                List<String> fields = Arrays.asList(LoadIndexFields.getFields(Collections.singletonList(source)));
                assertTrue(fields.contains(ExtraProperties.FACE_ENCODINGS));
                assertFalse(fields.contains(FaceRecognitionModels.storageField("auraface", 512)));
                assertNull(reader.leaves().get(0).reader().getSortedSetDocValues(ExtraProperties.FACE_ENCODINGS));
                assertNotNull(reader.leaves().get(0).reader()
                        .getSortedSetDocValues(FaceRecognitionModels.storageField("auraface", 512)));
                SimilarFacesSearch.validateCaseCompatibility(reader.leaves().get(0).reader(), item);
            }
        }
    }

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
                assertEquals(512 * 4, reader.document(4)
                        .getBinaryValue(FaceRecognitionModels.storageField(null, 512)).length);
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

    @Test
    public void existingElasticIndexReceivesOnlyFaceMappingUpdate() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = mappingServer(200, "{\"acknowledged\":true}", request);
        try (RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")))) {
            ElasticSearchIndexTask.updateFaceMappings(client, "existing-case");
            JsonNode properties = new ObjectMapper().readTree(request.get()).path("properties");
            assertEquals(1, properties.size());
            JsonNode faces = properties.path("faces");
            assertEquals("nested", faces.path("type").asText());
            JsonNode fields = faces.path("properties");
            assertEquals(128, fields.path("face_encoding").path("dimension").asInt());
            assertEquals("l2", fields.path("face_encoding").path("method").path("space_type").asText());
            for (String model : FaceRecognitionModels.MODERN_MODELS) {
                JsonNode field = fields.path("face_encoding_" + model);
                assertEquals("knn_vector", field.path("type").asText());
                assertEquals(512, field.path("dimension").asInt());
                assertEquals("cosinesimil", field.path("method").path("space_type").asText());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void incompatibleExistingElasticMappingRequiresReindexing() throws Exception {
        HttpServer server = mappingServer(400,
                "{\"error\":{\"type\":\"illegal_argument_exception\",\"reason\":\"cannot change vector dimensions\"},\"status\":400}",
                new AtomicReference<>());
        try (RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")))) {
            IOException error = assertThrows(IOException.class,
                    () -> ElasticSearchIndexTask.updateFaceMappings(client, "existing-case"));
            assertTrue(error.getMessage().contains("Reindex into a new index"));
            assertNotNull(error.getCause());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void unacknowledgedElasticMappingAbortsAppend() throws Exception {
        HttpServer server = mappingServer(200, "{\"acknowledged\":false}", new AtomicReference<>());
        try (RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")))) {
            IOException error = assertThrows(IOException.class,
                    () -> ElasticSearchIndexTask.updateFaceMappings(client, "existing-case"));
            assertTrue(error.getMessage().contains("append/continue processing was aborted"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer mappingServer(int status, String response, AtomicReference<String> request) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/existing-case/_mapping", exchange -> {
            if (!"PUT".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static KnnVector vector(int dimensions) {
        double[] values = new double[dimensions];
        values[0] = 1;
        return new KnnVector(values);
    }

    @SuppressWarnings("unchecked")
    private static List<byte[]> storedFeatures(IItem item) {
        return (List<byte[]>) item.getExtraAttribute(ExtraProperties.FACE_ENCODINGS);
    }

    private static IPEDMultiSource sourceForStoredItemTests() {
        return sourceForStoredItemTests(null);
    }

    private static IPEDMultiSource sourceForStoredItemTests(IndexReader reader) {
        return new IPEDMultiSource(Collections.emptyList()) {
            @Override
            public void init() {
                // Stored-field decoding only needs the module path, not a loaded case.
            }

            @Override
            public File getModuleDir() {
                return new File(".");
            }

            @Override
            public IndexReader getReader() {
                return reader;
            }
        };
    }

    private static Document storedDocument(DirectoryReader reader, String name) throws Exception {
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document doc = reader.document(i);
            if (name.equals(doc.get("name"))) {
                return doc;
            }
        }
        throw new AssertionError("Missing document " + name);
    }
}
