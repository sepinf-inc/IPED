package iped.app.ui;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;

public class RAGSourcesFilterTest {

    @Test
    public void testKeepsOnlyTheSourcesOfTheirOwnCaseWithTheirRelevance() {
        IItemId[] ids = { new ItemId(0, 1), new ItemId(0, 7), new ItemId(1, 7), new ItemId(0, 8) };
        MultiSearchResult table = new MultiSearchResult(ids, new float[] { 1, 1, 1, 1 });

        Map<IItemId, Float> sources = new LinkedHashMap<>();
        sources.put(new ItemId(1, 7), 100f);
        sources.put(new ItemId(0, 8), 96.8f);
        sources.put(new ItemId(2, 3), 90f); // not in the current result

        IMultiSearchResult result = new RAGSourcesFilter(sources).filterResult(table);
        assertEquals(2, result.getLength());
        assertEquals(new ItemId(1, 7), result.getItem(0));
        assertEquals(100f, result.getScore(0), 0f);
        assertEquals(new ItemId(0, 8), result.getItem(1));
        assertEquals(96.8f, result.getScore(1), 0f);
    }
}
