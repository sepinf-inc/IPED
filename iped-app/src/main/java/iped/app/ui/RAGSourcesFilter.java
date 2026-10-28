package iped.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iped.data.IItemId;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IResultSetFilter;

/**
 * Keeps only the sources of an AI Assistant answer. Items are identified by
 * source and ID, so equal numeric IDs of other cases in a multicase are not
 * matched. The score becomes the relevance of the source in the answer (0-100).
 */
public class RAGSourcesFilter implements IResultSetFilter {

    private final Map<IItemId, Float> sources;

    /**
     * @param sources relevance of each source, as a 0-100 percentage
     */
    public RAGSourcesFilter(Map<IItemId, Float> sources) {
        this.sources = sources;
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) {
        List<IItemId> ids = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        for (int i = 0; i < src.getLength(); i++) {
            IItemId id = src.getItem(i);
            Float score = sources.get(id);
            if (score != null) {
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
    public String toString() {
        return Messages.get("FilterValue.RAGSources") + " (" + sources.size() + ")";
    }
}
