package iped.engine.task.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.document.Document;
import org.junit.Test;

import iped.engine.data.Item;
import iped.properties.ExtraProperties;

public class EmbeddingTaskTest {

    private static final String TEXT = "Synthetic paragraph with enough meaningful words to be embedded by the RAG pipeline.";

    @Test
    public void testFragmentsWithoutVectorAreCounted() {
        List<String> fragments = Arrays.asList(TEXT, TEXT, TEXT);
        List<float[]> vectors = Arrays.asList(new float[] { 1 }, null, new float[0]);
        assertEquals(2, EmbeddingTask.countMissingEmbeddings(fragments, vectors));
    }

    @Test
    public void testAllFragmentsMissingWhenServiceReturnedNothing() {
        assertEquals(2, EmbeddingTask.countMissingEmbeddings(Arrays.asList(TEXT, TEXT), null));
        assertEquals(2, EmbeddingTask.countMissingEmbeddings(Arrays.asList(TEXT, TEXT), Collections.emptyList()));
    }

    @Test
    public void testFragmentsWithoutUsefulContentAreNotFailures() {
        List<String> fragments = Arrays.asList("", "   ", TEXT);
        assertEquals(0, EmbeddingTask.countMissingEmbeddings(fragments, Arrays.asList(null, null, new float[] { 1 })));
    }

    @Test
    public void testEmbeddedFragmentsBecomeChildDocumentsWithoutRegularContent() {
        Item item = new Item();
        item.setId(42);
        item.setExtraAttribute(ExtraProperties.GLOBAL_ID, "abc");
        EmbeddingTask.Fragments fragments = new EmbeddingTask.Fragments("bge-m3");
        fragments.add("Original <b>text</b> one", "Original text one");
        fragments.add("Text two", "Text two");
        fragments.add("Text three", "Text three");
        fragments.setVector(0, new float[] { 1, 0 });
        fragments.setVector(2, new float[] { 0, 1 });

        List<Document> docs = IndexTask.ragDocuments(item, fragments);

        // the fragment without a vector is not retrievable, so it gets no document
        assertEquals(2, docs.size());
        Document first = docs.get(0);
        assertEquals("abc#1", first.get(IndexTask.FRAG_TRACK_ID));
        assertEquals("42", first.get(IndexTask.FRAG_PARENT_NUMERIC_ID));
        assertEquals("Original text one", first.get(IndexTask.CONTENT_STORED));
        assertEquals("1", first.get(IndexTask.HAS_EMBEDDING_FRAG));
        assertTrue(first.getField(IndexTask.RAG_CONTENT) != null);
        assertNull(first.getField(IndexItem.CONTENT));
        assertEquals("abc#3", docs.get(1).get(IndexTask.FRAG_TRACK_ID));
    }

    @Test
    public void testItemsWithoutFragmentsOrGlobalIdGetNoDocuments() {
        Item item = new Item();
        item.setId(1);
        assertTrue(IndexTask.ragDocuments(item, null).isEmpty());
        EmbeddingTask.Fragments fragments = new EmbeddingTask.Fragments("bge-m3");
        fragments.add(TEXT, TEXT);
        fragments.setVector(0, new float[] { 1 });
        assertTrue(IndexTask.ragDocuments(item, fragments).isEmpty());
    }
}
