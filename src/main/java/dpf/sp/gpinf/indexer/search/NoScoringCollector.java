package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.BitSet;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

/**
 * Fast collector that do not compute scores, for cases with dozens of millions of items
 * 
 * @author Nassif
 *
 */
public class NoScoringCollector extends Collector{
	
	private int docBase = 0;
	private int totalHits = 0;
	private BitSet bits;
	
	private volatile boolean canceled = false;
	
	public NoScoringCollector(int capacity){
		bits = new BitSet(capacity);
	}
	
	public void cancel(){
		canceled = true;
	}

	@Override
	public void setScorer(Scorer scorer) throws IOException {
	}

	@Override
	public void collect(int doc) throws IOException {
		if(canceled)
			throw new InterruptedIOException("Search canceled!");
		
		totalHits++;
		bits.set(doc + docBase);
	}

	@Override
	public void setNextReader(AtomicReaderContext context) throws IOException {
		docBase = context.docBase;
	}

	@Override
	public boolean acceptsDocsOutOfOrder() {
		return true;
	}
	
	public int getTotalHits(){
		return this.totalHits;
	}
	
	public LuceneSearchResult getSearchResults1(){
		LuceneSearchResult results = new LuceneSearchResult(totalHits);
		int idx = 0;
		for(int i = 0; i < bits.length(); i++)
			if(bits.get(i))
				results.docs[idx++] = i;
		
		return results;
	}
	
	public LuceneSearchResult getSearchResults(){
		LuceneSearchResult results = new LuceneSearchResult(totalHits);
		int idx = 0;
		long[] array = bits.toLongArray();
		for(int i = 0; i < array.length; i++)
			for(int j = 0; j < Long.SIZE; j++)
			if((array[i] & (1L << j)) != 0)
				results.docs[idx++] = (i << 6) | j;
		
		return results;
	}

}
