package iped.app.ui;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import iped.data.IItemId;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;

public class SemanticSimilarityFilterTest {

    private static final IItemId REF = new ItemId(0, 1);

    private static MultiSearchResult source() {
        IItemId[] ids = { REF, new ItemId(0, 7), new ItemId(1, 7), new ItemId(0, 8) };
        return new MultiSearchResult(ids, new float[] { 1, 1, 1, 1 });
    }

    private static Map<IItemId, Float> similarity() {
        Map<IItemId, Float> similar = new LinkedHashMap<>();
        similar.put(REF, 95f);
        similar.put(new ItemId(1, 7), 82f);
        similar.put(new ItemId(0, 8), 64f);
        similar.put(new ItemId(2, 3), 90f); // not in the current result
        return similar;
    }

    @Test
    public void testKeepsOnlySimilarItemsOfTheirOwnCaseAboveThreshold() {
        IMultiSearchResult result = new SemanticSimilarityFilter(REF, null, similarity(), 70).filterResult(source());
        assertEquals(2, result.getLength());
        assertEquals(REF, result.getItem(0));
        assertEquals(SemanticSimilarityFilter.REFERENCE_SCORE, result.getScore(0), 0f);
        assertEquals(new ItemId(1, 7), result.getItem(1));
        assertEquals(82f, result.getScore(1), 0f);
    }

    @Test
    public void testTypedPercentageIsValidated() {
        assertEquals(Integer.valueOf(75), SemanticSimilarityFilterActions.parsePercent(" 75 "));
        assertEquals(Integer.valueOf(0), SemanticSimilarityFilterActions.parsePercent("0"));
        assertEquals(null, SemanticSimilarityFilterActions.parsePercent("101"));
        assertEquals(null, SemanticSimilarityFilterActions.parsePercent("-1"));
        assertEquals(null, SemanticSimilarityFilterActions.parsePercent("abc"));
    }

    @Test
    public void testSliderValueChangesTheThreshold() {
        SemanticSimilarityFilter filter = new SemanticSimilarityFilter(REF, null, similarity(), 70);
        filter.setQuantityValue(60);
        assertEquals(60, filter.getQuantityValue());
        assertEquals(3, filter.filterResult(source()).getLength());
        filter.setQuantityValue(90);
        assertEquals(1, filter.filterResult(source()).getLength());
    }
}
