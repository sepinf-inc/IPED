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
import iped3.util.ExtraProperties;

public class SimilarImageSearch {
    private static final int matchRange = 8;
    private static final int matchMinimumPct = 80;

    public Query getQueryForSimilarImages(Query currentQuery, IItem item) {
        byte[] simIdx = item.getImageSimilarityFeatures(false);
        if (simIdx == null) {
            return null;
        }
        BooleanQuery similarImagesQuery = new BooleanQuery();
        for (int i = 0; i < simIdx.length; i++) {
            String field = ExtraProperties.SIMILARITY_META_PREFIX + i;
            int value = simIdx[i] & 0xFF;
            similarImagesQuery.add(NumericRangeQuery.newIntRange(field, value - matchRange, value + matchRange, true, true), Occur.SHOULD);
        }
        similarImagesQuery.setMinimumNumberShouldMatch(matchMinimumPct * simIdx.length / 100);

        if (currentQuery != null) {
            BooleanQuery q = new BooleanQuery();
            q.add(currentQuery, Occur.MUST);
            q.add(similarImagesQuery, Occur.MUST);
            similarImagesQuery = q;
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
            similarityFeaturesValues = atomicReader.getBinaryDocValues(BasicProps.SIMILARITY);
        }

        public float customScore(int id, float subQueryScore, float valSrcScore) throws IOException {
            BytesRef bytesRef = similarityFeaturesValues.get(id);
            if (bytesRef == null) {
                return 0;
            }
            byte[] refSim = refItem.getImageSimilarityFeatures(true);
            byte[] currSim = bytesRef.bytes;
            int distance = ImageSimilarity.distance(refSim, currSim);
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
            float score = 100 - distance / (refSim.length / 2f);
            if (score < 1) score = 1;
            return score;
        }
    }
}