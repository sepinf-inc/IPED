package dpf.sp.gpinf.indexer.search;

import java.util.Iterator;

public class MultiSearchResult {
	
	private ItemId[] ids;
	private float[] scores;
	
	public MultiSearchResult(){
		this.ids = new ItemId[0];
		this.scores = new float[0];
	}
	
	public MultiSearchResult(ItemId[] ids, float[] scores){
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
	
	public ItemIdIterator getIterator(){
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
			throw new UnsupportedOperationException("Remove not allowed");
		}
	}	
	
	public static MultiSearchResult get(IPEDMultiSource iSource, LuceneSearchResult luceneResult){
		
		//System.out.println("multi Result");
		
		MultiSearchResult result = new MultiSearchResult();
		result.scores = luceneResult.scores;
		result.ids = new ItemId[luceneResult.getLength()];
		
		if(luceneResult.length <= IPEDSearcher.MAX_SIZE_TO_SCORE){
			for(int i = 0; i < luceneResult.docs.length; i++){
				result.ids[i] = iSource.getItemId(luceneResult.docs[i]);
			}
			
		//Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não usa scores)
		}else{
			IPEDSource atomicSource = null;
			int baseDoc = 0;
			int sourceId = 0;
			int maxdoc = 0;
			for(int i = 0; i < luceneResult.docs.length; i++){
				if(atomicSource == null || luceneResult.docs[i] >= baseDoc + maxdoc){
					atomicSource = iSource.getAtomicSource(luceneResult.docs[i]);
					sourceId = atomicSource.getSourceId();
					baseDoc = iSource.getBaseLuceneId(atomicSource);
					maxdoc = atomicSource.reader.maxDoc();
				}
				result.ids[i] = new ItemId(sourceId, atomicSource.getId(luceneResult.docs[i] - baseDoc));
			}
		}
		
		return result;
	}
	
	public static LuceneSearchResult get(MultiSearchResult ipedResult, IPEDMultiSource iSource){
		
		//System.out.println("lucene Result");
		
		LuceneSearchResult lResult = new LuceneSearchResult(0);
		lResult.length = ipedResult.getLength();
		lResult.scores = ipedResult.scores;
		lResult.docs = new int[lResult.length];
		
		int i = 0;
		if(ipedResult.getLength() <= IPEDSearcher.MAX_SIZE_TO_SCORE){
			for(ItemId item : ipedResult.ids){ 
				lResult.docs[i++] = iSource.getLuceneId(item);
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
				lResult.docs[i++] = atomicSource.getLuceneId(item.getId()) + baseDoc;
			}
		}
		
		return lResult;
	}
	
	@Override
	public MultiSearchResult clone(){
		MultiSearchResult result = new MultiSearchResult();
		result.ids = this.ids.clone();
		result.scores = this.scores.clone();
		return result;
	}
}
