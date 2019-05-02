package dpf.sp.gpinf.indexer.search;

import java.util.Iterator;

import iped3.IPEDSource;
import iped3.ItemId;
import iped3.search.LuceneSearchResult;
import iped3.search.MultiSearchResult;

public class MultiSearchResultImpl implements MultiSearchResult {
	
	private ItemId[] ids;
	private float[] scores;
	
	public MultiSearchResultImpl(){
		this.ids = new ItemIdImpl[0];
		this.scores = new float[0];
	}
	
	public MultiSearchResultImpl(ItemId[] ids, float[] scores){
		this.ids = ids;
		this.scores = scores;
	}
	
	public final int getLength(){
		return ids.length;
	}
	
	public final ItemId getItem(int i){
		return ids[i];
	}
	
	public final float getScore(int i){
		return scores[i];
	}
	
	public Iterable<ItemId> getIterator(){
		return new ItemIdIterator();
	}
	
	public class ItemIdIterator implements Iterable<ItemId>, Iterator<ItemId>{
		
		private int pos = 0;

		@Override
		public final Iterator<ItemId> iterator() {
			return this;
		}

		@Override
		public final boolean hasNext() {
			return pos < ids.length;
		}

		@Override
		public final ItemId next() {
			return ids[pos++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Remove not allowed"); //$NON-NLS-1$
		}
	}	
	
	public static MultiSearchResultImpl get(IPEDMultiSource iSource, LuceneSearchResult luceneResult){
		
		//System.out.println("multi Result");
		
		MultiSearchResultImpl result = new MultiSearchResultImpl();
		result.scores = luceneResult.getScores();
		result.ids = new ItemIdImpl[luceneResult.getLength()];
		
		if(luceneResult.getLength() <= IPEDSearcherImpl.MAX_SIZE_TO_SCORE){
			int[] docs = luceneResult.getLuceneIds();
			for(int i = 0; i < docs.length; i++){
				result.ids[i] = iSource.getItemId(docs[i]);
			}
			
		//Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não usa scores)
		}else{
			IPEDSource atomicSource = null;
			int baseDoc = 0;
			int sourceId = 0;
			int maxdoc = 0;
			int[] docs = luceneResult.getLuceneIds();
			for(int i = 0; i < docs.length; i++){
				if(atomicSource == null || docs[i] >= baseDoc + maxdoc){
					atomicSource = iSource.getAtomicSource(docs[i]);
					sourceId = atomicSource.getSourceId();
					baseDoc = iSource.getBaseLuceneId(atomicSource);
					maxdoc = atomicSource.getReader().maxDoc();
				}
				result.ids[i] = new ItemIdImpl(sourceId, atomicSource.getId(docs[i] - baseDoc));
			}
		}
		
		return result;
	}
	
	public static LuceneSearchResult get(MultiSearchResultImpl ipedResult, IPEDMultiSource iSource){
		LuceneSearchResult lResult = new LuceneSearchResult(ipedResult.getLength());
		float[] scores = lResult.getScores();
		int[] docs = lResult.getLuceneIds(); 
		
		int i = 0;
		if(ipedResult.getLength() <= IPEDSearcherImpl.MAX_SIZE_TO_SCORE){
			for(ItemId item : ipedResult.ids){ 
				scores[i] = ipedResult.getScore(i);
				docs[i] = iSource.getLuceneId(item);
				i++;
			}
		
		//Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não usa scores)
		}else{
			IPEDSource atomicSource = null;
			int baseDoc = 0;
			int sourceId = 0;
			for(ItemId item : ipedResult.ids){
				if(atomicSource == null || item.getSourceId() != sourceId){
					sourceId = item.getSourceId();
					atomicSource = iSource.getAtomicSourceBySourceId(sourceId);
					baseDoc = iSource.getBaseLuceneId(atomicSource);
				}
				docs[i] = atomicSource.getLuceneId(item.getId()) + baseDoc;
				scores[i] = ipedResult.getScore(i);
				i++;
			}
		}
		
		return lResult;
	}
	
	@Override
	public MultiSearchResultImpl clone(){
		MultiSearchResultImpl result = new MultiSearchResultImpl();
		result.ids = this.ids.clone();
		result.scores = this.scores.clone();
		return result;
	}
}
