package iped.engine.lucene;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.BitSet;
import java.util.Collection;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.util.FixedBitSet;

import iped.engine.search.LuceneSearchResult;

/**
 * Fast collector that do not compute scores, for cases with dozens of millions
 * of items
 * 
 * @author Nassif
 *
 */
public class NoScoringCollector implements CollectorManager<Collector, Void> {

    public final BitSet bits;

    private int totalHits = 0;

    private volatile boolean canceled = false;

    public NoScoringCollector(int maxDoc) {
        this.bits = new BitSet(maxDoc);
    }

    public void cancel() {
        canceled = true;
    }

    /**
     * Collectors may run concurrently on different slices, each leaf collects into
     * its own bitset and merges it into {@link #bits} when finished.
     */
    @Override
    public Collector newCollector() {
        return new Collector() {
            @Override
            public LeafCollector getLeafCollector(LeafReaderContext context) {
                return new NoScoringLeafCollector(context);
            }

            @Override
            public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
            }
        };
    }

    @Override
    public Void reduce(Collection<Collector> collectors) {
        return null;
    }

    private final class NoScoringLeafCollector implements LeafCollector {

        private final int docBase;
        private final FixedBitSet leafBits;
        private int leafHits = 0;

        NoScoringLeafCollector(LeafReaderContext context) {
            this.docBase = context.docBase;
            this.leafBits = new FixedBitSet(context.reader().maxDoc());
        }

        @Override
        public void setScorer(Scorable scorer) {
        }

        @Override
        public void collect(int doc) throws IOException {
            if (canceled)
                throw new InterruptedIOException("Search canceled!"); //$NON-NLS-1$
            leafBits.set(doc);
            leafHits++;
        }

        @Override
        public void finish() {
            merge(docBase, leafBits, leafHits);
        }
    }

    private synchronized void merge(int docBase, FixedBitSet leafBits, int leafHits) {
        if (leafHits == 0) {
            return;
        }
        int length = leafBits.length();
        for (int doc = leafBits.nextSetBit(0); doc != DocIdSetIterator.NO_MORE_DOCS; doc = doc + 1 < length
                ? leafBits.nextSetBit(doc + 1)
                : DocIdSetIterator.NO_MORE_DOCS) {
            bits.set(docBase + doc);
        }
        totalHits += leafHits;
    }

    public synchronized int getTotalHits() {
        return this.totalHits;
    }

    public LuceneSearchResult getSearchResults1() {
        LuceneSearchResult results = new LuceneSearchResult(totalHits);
        int[] docs = results.getLuceneIds();
        int idx = 0;
        for (int i = 0; i < bits.length(); i++)
            if (bits.get(i))
                docs[idx++] = i;

        return results;
    }

    public LuceneSearchResult getSearchResults() {
        LuceneSearchResult results = new LuceneSearchResult(totalHits);
        int[] docs = results.getLuceneIds();
        int idx = 0;
        long[] array = bits.toLongArray();
        for (int i = 0; i < array.length; i++)
            for (int j = 0; j < Long.SIZE; j++)
                if ((array[i] & (1L << j)) != 0)
                    docs[idx++] = (i << 6) | j;

        return results;
    }

}
