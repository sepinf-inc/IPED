package dpf.sp.gpinf.indexer.search;

public class MultiSearchResult {
	
	ItemId[] ids;
	float[] scores;
	
	private MultiSearchResult(){
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
		MultiSearchResult result = new MultiSearchResult();
		result.scores = luceneResult.scores;
		result.ids = new ItemId[luceneResult.getLength()];
		
		int i = 0;
		for(int luceneId : luceneResult.docs){
			result.ids[i++] = iSource.getItemId(luceneId);
		}
		return result;
	}
	
	public static LuceneSearchResult get(MultiSearchResult ipedResult, IPEDMultiSource iSource){
		LuceneSearchResult lResult = new LuceneSearchResult(0);
		lResult.length = ipedResult.getLength();
		lResult.scores = ipedResult.scores;
		lResult.docs = new int[lResult.length];
		
		int i = 0;
		for(ItemId item : ipedResult.getIds()){ 
			lResult.docs[i++] = iSource.getLuceneId(item);
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
