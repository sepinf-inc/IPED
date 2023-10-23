package iped.engine.lucene;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.BitSet;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import iped.engine.search.LuceneSearchResult;

/**
 * Fast collector that do not compute scores, for cases with dozens of millions
 * of items
 * 
 * @author Nassif
 *
 */
public class NoScoringCollector extends SimpleCollector {

    private int docBase = 0;
    private int totalHits = 0;
    public BitSet bits;

    private volatile boolean canceled = false;

    public NoScoringCollector(int maxDoc) {
        this.bits = new BitSet(maxDoc);
    }

    public void cancel() {
        canceled = true;
    }

    @Override
    public void collect(int doc) throws IOException {
        if (canceled)
            throw new InterruptedIOException("Search canceled!"); //$NON-NLS-1$

        bits.set(doc + docBase);
        totalHits++;
    }

    @Override
    public void doSetNextReader(LeafReaderContext context) throws IOException {
        docBase = context.docBase;
    }

    public int getTotalHits() {
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
    
    @Override
    public ScoreMode scoreMode() {
        return ScoreMode.COMPLETE_NO_SCORES;
    }

}
