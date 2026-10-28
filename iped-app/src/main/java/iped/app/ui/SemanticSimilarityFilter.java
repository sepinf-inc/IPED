package iped.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IItemRef;
import iped.viewers.api.IQuantifiableFilter;
import iped.viewers.api.IResultSetFilter;

/**
 * Keeps only the items found by the semantic-similarity RAG search whose
 * similarity reaches the threshold, adjustable in the filter slider like the
 * other similarity filters. Items are identified by source and ID, so equal
 * numeric IDs of other cases in a multicase are not matched. The score becomes
 * the similarity (0-100) and the reference item is shown as "REF".
 */
public class SemanticSimilarityFilter implements IResultSetFilter, IItemRef, IQuantifiableFilter {

    /** Score rendered as "REF" by ProgressCellRenderer, as in the similar images/faces filters. */
    static final float REFERENCE_SCORE = 1000f;

    private final IItemId refId;
    private final IItem ref;
    private final Map<IItemId, Float> similarity;
    private int minPercent;

    /**
     * @param similarity cosine similarity of each candidate, as a 0-100 percentage
     * @param minPercent initial threshold, 0-100
     */
    public SemanticSimilarityFilter(IItemId refId, IItem ref, Map<IItemId, Float> similarity, int minPercent) {
        this.refId = refId;
        this.ref = ref;
        this.similarity = similarity;
        this.minPercent = minPercent;
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) {
        List<IItemId> ids = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        for (int i = 0; i < src.getLength(); i++) {
            IItemId id = src.getItem(i);
            Float score = id.equals(refId) ? Float.valueOf(REFERENCE_SCORE) : similarity.get(id);
            if (score != null && score >= minPercent) {
                ids.add(id);
                scores.add(score);
            }
        }
        float[] scoreArray = new float[scores.size()];
        for (int i = 0; i < scoreArray.length; i++) {
            scoreArray[i] = scores.get(i);
        }
        return new MultiSearchResult(ids.toArray(new IItemId[0]), scoreArray);
    }

    @Override
    public IItem getItemRef() {
        return ref;
    }

    @Override
    public IItemId getItemRefId() {
        return refId;
    }

    @Override
    public int getQuantityValue() {
        return minPercent;
    }

    @Override
    public void setQuantityValue(int value) {
        minPercent = value;
    }

    @Override
    public String toString() {
        return Messages.get("FilterValue.SemanticSimilarity") + " (" + minPercent + "%)"
                + (ref != null ? ": " + ref.getName() : "");
    }
}
