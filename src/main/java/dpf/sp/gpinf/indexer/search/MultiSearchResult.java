package dpf.sp.gpinf.indexer.search;

public class MultiSearchResult {
	
	ItemId[] ids;
	float[] scores;
	
	public MultiSearchResult(){
		this.ids = new ItemId[0];
		this.scores = new float[0];
	}
	
	public MultiSearchResult(ItemId[] ids, float[] scores){
		this.ids = ids;
		this.scores = scores;
	}
	
	public ItemId[] getIds(){
		return ids;
	}
	
	public int getLength(){
		return ids.length;
	}
	
	public float[] getScores(){
		return scores;
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
			for(ItemId item : ipedResult.getIds()){ 
				lResult.docs[i++] = iSource.getLuceneId(item);
			}
		
		//Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não usa scores)
		}else{
			IPEDSource atomicSource = null;
			int baseDoc = 0;
			int sourceId = 0;
			for(ItemId item : ipedResult.getIds()){
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
