package dpf.sp.gpinf.indexer.search;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

import gpinf.similarity.ImageSimilarity;
import iped3.IItem;
import iped3.util.BasicProps;

public class SimilarImagesSearch {
    private static final int range = 320;//TODO!

    public Query getQueryForSimilarImages(Query currentQuery, IItem item) {
        byte[] similarityFeatures = item.getImageSimilarityFeatures();
        if (similarityFeatures == null) {
            return null;
        }

        BooleanQuery similarImagesQuery = new BooleanQuery();
        if (currentQuery != null) {
            similarImagesQuery.add(currentQuery, Occur.MUST);
        }

        for (int i = 0; i < 4; i++) {
            int refVal = similarityFeatures[i];
            similarImagesQuery.add(NumericRangeQuery.newIntRange(BasicProps.SIMILARITY_FEATURES + i, refVal - range, refVal + range, true, true), Occur.MUST);
        }

        CustomScoreQuery customScoreQuery = new SimilarImageCustomScoreQuery(similarImagesQuery, item);
        return customScoreQuery;
    }

    class SimilarImageCustomScoreQuery extends CustomScoreQuery {
        private final IItem refItem;

        public SimilarImageCustomScoreQuery(Query subQuery, IItem refItem) {
            super(subQuery);
            this.refItem = refItem;
        }

        public CustomScoreProvider getCustomScoreProvider(final AtomicReaderContext atomicContext) throws IOException {
            return new SimilarImageScoreProvider(atomicContext, refItem);
        }
    }

    class SimilarImageScoreProvider extends CustomScoreProvider {
        private final AtomicReader atomicReader;
        private final IItem refItem;
        private final BinaryDocValues similarityFeaturesValues;

        public SimilarImageScoreProvider(AtomicReaderContext context, IItem refItem) throws IOException {
            super(context);
            atomicReader = context.reader();
            this.refItem = refItem;
            similarityFeaturesValues = atomicReader.getBinaryDocValues(BasicProps.SIMILARITY_FEATURES);
        }

        public float customScore(int id, float subQueryScore, float valSrcScore) throws IOException {
            BytesRef bytesRef = similarityFeaturesValues.get(id);
            if (bytesRef == null) {
                return 0;
            }
            byte[] refSimilarityFeatures = refItem.getImageSimilarityFeatures();
            byte[] currSimilarityFeatures = bytesRef.bytes;
            int distance = ImageSimilarity.distance(refSimilarityFeatures, currSimilarityFeatures);
            if (distance == 0) {
                String refHash = refItem.getHash();
                if (refHash != null) {
                    Document doc = atomicReader.document(id);
                    String currHash = doc.get(BasicProps.HASH);
                    if (refHash.equals(currHash)) {
                        return 200;
                    }
                }
            }
            //TODO 0
            float score = Math.max(2, 100 - distance / (refSimilarityFeatures.length / 2f));
            return score;
        }
    }
}