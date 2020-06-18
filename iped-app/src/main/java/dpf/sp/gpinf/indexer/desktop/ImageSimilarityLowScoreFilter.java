package dpf.sp.gpinf.indexer.desktop;

import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import iped3.IItemId;
import iped3.search.IMultiSearchResult;

public class ImageSimilarityLowScoreFilter {
    public static MultiSearchResult filter(IMultiSearchResult result) {
        ArrayList<IItemId> filteredItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            float score = result.getScore(i++);
            if (score > 1) {
                filteredItems.add(item);
                scores.add(score);
            }
        }
        return new MultiSearchResult(filteredItems.toArray(new IItemId[0]), toPrimitive(scores));
    }

    private static float[] toPrimitive(List<Float> list) {
        if (list == null) {
            return null;
        }
        final float[] result = new float[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i).floatValue();
        }
        return result;
    }
}
