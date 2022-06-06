package dpf.sp.gpinf.indexer.lucene;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.BitSet;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import dpf.sp.gpinf.indexer.search.IPEDSearcher.FinalDocIdConverter;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;

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
    private BitSet bits;

    private FinalDocIdConverter docIdConverter;

    private volatile boolean canceled = false;

    public NoScoringCollector(IPEDSource ipedCase) {
        this.docIdConverter = new FinalDocIdConverter(ipedCase);
        this.bits = new BitSet(ipedCase.getReader().maxDoc());
    }

    public void cancel() {
        canceled = true;
    }

    @Override
    public void collect(int doc) throws IOException {
        if (canceled)
            throw new InterruptedIOException("Search canceled!"); //$NON-NLS-1$

        doc += docBase;
        // see #925 why this is needed
        doc = docIdConverter.convertToFinalDocId(doc);
        if (!bits.get(doc)) {
            bits.set(doc);
            totalHits++;
        }
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
