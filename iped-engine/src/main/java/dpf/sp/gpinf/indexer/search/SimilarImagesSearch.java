package dpf.sp.gpinf.indexer.search;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;

import iped3.IItem;
import iped3.util.BasicProps;

public class SimilarImagesSearch {
    /** 
     * This range is used in the query to filter images, based only in 4 features (RGB and gray 
     * channels median values of the whole image). Higher values will bring more images to be 
     * evaluated (i.e. distance to reference image will be measured inside scoring (customScore() 
     * method), so it can be slower. On the other hand, lower values may discard images that later
     * would be considered good (i.e. close to reference image). 
     */
    private static final int range = 64;

    public Query getQueryForSimilarImages(IItem item) {
        byte[] similarityFeatures = item.getImageSimilarityFeatures();
        if (similarityFeatures == null) {
            return null;
        }

        BooleanQuery similarImagesQuery = new BooleanQuery();
        for (int i = 0; i < 4; i++) {
            int refVal = similarityFeatures[i];
            similarImagesQuery.add(NumericRangeQuery.newIntRange(BasicProps.SIMILARITY_FEATURES + i, refVal - range, refVal + range, true, true), Occur.MUST);
        }

        return similarImagesQuery;
    }
}
