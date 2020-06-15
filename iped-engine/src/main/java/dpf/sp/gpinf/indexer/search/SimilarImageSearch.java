package dpf.sp.gpinf.indexer.search;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

import iped3.IItem;
import iped3.IItemId;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class SimilarImageSearch {
    private static final int matchRange = 10;
    private static final int matchMinimumPct = 70;

    public Query getQueryForSimilarImages(IItemId itemId, IPEDMultiSource appCase) {
        IItem item = appCase.getItemByItemId(itemId);
        byte[] simIdx = item.getImageSimilarityFeatures(false);
        if (simIdx == null) {
            return null;
        }
        BooleanQuery query = new BooleanQuery();
        for (int i = 0; i < simIdx.length; i++) {
            String field = ExtraProperties.SIMILARITY_META_PREFIX + i;
            int value = simIdx[i] & 0xFF;
            query.add(NumericRangeQuery.newIntRange(field, value - matchRange, value + matchRange, true, true), Occur.SHOULD);
        }
        query.setMinimumNumberShouldMatch(matchMinimumPct * simIdx.length / 100);
        CustomScoreQuery customScoreQuery = new SimilarImageCustomScoreQuery(query, item);
        return customScoreQuery;
    }

    class SimilarImageCustomScoreQuery extends CustomScoreQuery {
        private final IItem refItem;

        public SimilarImageCustomScoreQuery(Query subQuery, IItem refItem) {
            super(subQuery);
            this.refItem = refItem;
        }

        public CustomScoreProvider getCustomScoreProvider(final AtomicReaderContext atomicContext) {
            return new SimilarImageScoreProvider(atomicContext, refItem);
        }
    }

    class SimilarImageScoreProvider extends CustomScoreProvider {
        private final AtomicReader atomicReader;
        private final IItem refItem;

        public SimilarImageScoreProvider(AtomicReaderContext context, IItem refItem) {
            super(context);
            atomicReader = context.reader();
            this.refItem = refItem;
        }

        public float customScore(int id, float subQueryScore, float valSrcScore) throws IOException {
            Document doc = atomicReader.document(id);
            BytesRef bytesRef = doc.getBinaryValue(BasicProps.SIMILARITY);
            if (bytesRef == null) {
                return 0;
            }
            byte[] refSim = refItem.getImageSimilarityFeatures(true);
            byte[] currSim = bytesRef.bytes;
            int distance = 0;
            for (int i = 0; i < refSim.length; i++) {
                int d = (refSim[i] & 0xFF) - (currSim[i] & 0xFF);
                distance += d * d;
            }
            if (distance == 0) {
                String refHash = refItem.getHash();
                if (refHash != null) {
                    String currHash = doc.get(BasicProps.HASH);
                    if (refHash.equals(currHash)) return 200;
                }
            }
            float score = 100 - distance / (float)refSim.length;
            if (score < 1) score = 1;
            return score;
        }
    }
}