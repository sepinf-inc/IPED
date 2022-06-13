package dpf.sp.gpinf.indexer.search;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import iped3.IItem;

public class SimilarImagesSearch {
    /**
     * This range is used in the query to filter images, based only in 4 features
     * (RGB and gray channels median values of the whole image). Higher values will
     * bring more images to be evaluated (i.e. distance to reference image will be
     * measured inside scoring (customScore() method), so it can be slower. On the
     * other hand, lower values may discard images that later would be considered
     * good (i.e. close to reference image).
     */
    private static final int range = 64;

    public Query getQueryForSimilarImages(IItem item) {
        byte[] similarityFeatures = (byte[]) item.getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES);
        if (similarityFeatures == null) {
            return null;
        }

        int[] lower = new int[4];
        int[] upper = new int[4];
        for (int i = 0; i < 4; i++) {
            int refVal = similarityFeatures[i];
            lower[i] = refVal - range;
            upper[i] = refVal + range;
        }
        BooleanQuery.Builder similarImagesQuery = new BooleanQuery.Builder();
        similarImagesQuery.add(IntPoint.newRangeQuery(ImageSimilarityTask.IMAGE_FEATURES, lower, upper),
                Occur.MUST);

        return similarImagesQuery.build();
    }
}
