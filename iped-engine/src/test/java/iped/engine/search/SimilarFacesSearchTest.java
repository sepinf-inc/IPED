package iped.engine.search;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.After;
import org.junit.Test;

import iped.engine.data.Item;
import iped.engine.lucene.SlowCompositeReaderWrapper;
import iped.engine.task.index.IndexItem;
import iped.properties.ExtraProperties;

public class SimilarFacesSearchTest {

    @After
    public void resetSettings() {
        SimilarFacesSearch.resetMinScore();
        SimilarFacesSearch.setSelectedIdxs(null);
    }

    @Test
    public void legacyVectorsRetainEuclideanDistanceAndSquaredApi() {
        float[] a = new float[128];
        float[] b = new float[128];
        a[0] = 1.3f;
        b[0] = 0.8f;
        assertEquals(0.5f, SimilarFacesSearch.distance(a, b), 0.00001f);
        assertEquals(0.25f, SimilarFacesSearch.distance(a, b, Float.MAX_VALUE), 0.00001f);
        assertEquals(50, SimilarFacesSearch.getMinScore());
    }

    @Test
    public void modernVectorsUseCosineAndClampRoundingErrors() {
        float[] a = new float[512];
        float[] b = new float[512];
        a[0] = 1;
        b[1] = 1;
        assertEquals(0, SimilarFacesSearch.distance(a, a), 0);
        assertEquals(1, SimilarFacesSearch.distance(a, b), 0);
        a[0] = 1.0000001f;
        assertEquals(0, SimilarFacesSearch.distance(a, a), 0);
        assertEquals(Float.MAX_VALUE, SimilarFacesSearch.distance(a, new float[128]), 0);
        a[0] = Float.NaN;
        assertEquals(Float.MAX_VALUE, SimilarFacesSearch.distance(a, b), 0);
    }

    @Test
    public void docValueDecodingRespectsBytesRefOffsetAndLength() {
        byte[] encoded = IndexItem.convFloatArrayToByteArray(new float[] {99, 1, 2, 3, 88});
        assertArrayEquals(new float[] {1, 2, 3},
                SimilarFacesSearch.convToFloatVec(new BytesRef(encoded, 4, 12)), 0);
    }

    @Test
    public void legacyMatchesKeepWorkingAndModernModelsCannotCrossMatch() {
        float[] legacy = new float[128];
        legacy[0] = 1.3f;
        Item old = item(null, legacy);
        SimilarFacesSearch.validateReference(old);
        assertEquals(Collections.singletonList("(1, 4, 3, 2)"), SimilarFacesSearch.getMatchLocations(old, old));

        float[] modern = new float[512];
        modern[0] = 1;
        Item aura = item("auraface", modern);
        Item buffalo = item("buffalo_l", modern);
        SimilarFacesSearch.validateReference(aura);
        assertEquals(1, SimilarFacesSearch.getMatchLocations(aura, aura).size());
        assertTrue(SimilarFacesSearch.getMatchLocations(aura, buffalo).isEmpty());
        assertTrue(SimilarFacesSearch.getMatchLocations(aura, old).isEmpty());
        assertTrue(SimilarFacesSearch.getMatchLocations(aura, item(null, modern)).isEmpty());
    }

    @Test
    public void unidentifiedModernReferenceIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SimilarFacesSearch.validateReference(item(null, new float[512])));
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isEmpty());
    }

    @Test
    public void modelIdentityRequiresMatchingDimensionAndExplicitModernTag() {
        assertEquals("dlib", FaceRecognitionModels.resolve(null, 128));
        assertNull(FaceRecognitionModels.resolve(null, 512));
        assertNull(FaceRecognitionModels.resolve("auraface", 128));
        assertNull(FaceRecognitionModels.resolve("unknown", 512));
        assertNotEquals(FaceRecognitionModels.vectorField("auraface", 512),
                FaceRecognitionModels.vectorField("buffalo_l", 512));
    }

    @Test
    public void modelDefaultsRemainConservativeUnlessUserOverridesThem() {
        SimilarFacesSearch.resetMinScore();
        Item legacy = item(null, new float[128]);
        Item aura = item("auraface", new float[512]);
        assertEquals(50, SimilarFacesSearch.getMinScore(legacy));
        assertEquals(60, SimilarFacesSearch.getMinScore(aura));
        // Merely opening/accepting the modern default does not override the legacy default.
        assertEquals(50, SimilarFacesSearch.getMinScore(legacy));
        SimilarFacesSearch.setMinScore(75);
        assertEquals(75, SimilarFacesSearch.getMinScore(legacy));
        assertEquals(75, SimilarFacesSearch.getMinScore(aura));
        SimilarFacesSearch.resetMinScore();
        assertEquals(60, SimilarFacesSearch.getMinScore(aura));
        assertEquals(50, SimilarFacesSearch.getMinScore(legacy));
    }

    @Test
    public void externalQueryRequiresSameModelInCaseAndAllowsMatchingMixedCase() throws Exception {
        Item aura = item("auraface", new float[512]);
        Item legacy = item(null, new float[128]);
        try (ByteBuffersDirectory directory = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            Document oldDocument = new Document();
            oldDocument.add(new SortedSetDocValuesField(ExtraProperties.FACE_ENCODINGS,
                    new BytesRef(IndexItem.convFloatArrayToByteArray(new float[128]))));
            writer.addDocument(oldDocument);
            try (LeafReader reader = SlowCompositeReaderWrapper.wrap(DirectoryReader.open(writer))) {
                SimilarFacesSearch.validateCaseCompatibility(reader, legacy);
                assertThrows(IllegalArgumentException.class,
                        () -> SimilarFacesSearch.validateCaseCompatibility(reader, aura));
            }
            Document buffaloDocument = new Document();
            buffaloDocument.add(new SortedDocValuesField(ExtraProperties.FACE_MODEL, new BytesRef("buffalo_l")));
            writer.addDocument(buffaloDocument);
            try (LeafReader reader = SlowCompositeReaderWrapper.wrap(DirectoryReader.open(writer))) {
                assertThrows(IllegalArgumentException.class,
                        () -> SimilarFacesSearch.validateCaseCompatibility(reader, aura));
            }
            Document auraDocument = new Document();
            auraDocument.add(new SortedDocValuesField(ExtraProperties.FACE_MODEL, new BytesRef("auraface")));
            writer.addDocument(auraDocument);
            try (LeafReader reader = SlowCompositeReaderWrapper.wrap(DirectoryReader.open(writer))) {
                SimilarFacesSearch.validateCaseCompatibility(reader, aura);
                SimilarFacesSearch.validateCaseCompatibility(reader, legacy);
            }
        }
    }

    private static Item item(String model, float[] vector) {
        Item item = new Item();
        item.setExtraAttribute(ExtraProperties.FACE_ENCODINGS,
                Arrays.asList(IndexItem.convFloatArrayToByteArray(vector)));
        item.setExtraAttribute(ExtraProperties.FACE_LOCATIONS, Collections.singletonList("(1, 4, 3, 2)"));
        if (model != null) {
            item.setExtraAttribute(ExtraProperties.FACE_MODEL, model);
        }
        return item;
    }
}
