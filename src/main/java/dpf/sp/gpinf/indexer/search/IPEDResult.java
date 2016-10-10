package dpf.sp.gpinf.indexer.search;

public class IPEDResult {
	
	ItemId[] ids;
	float[] scores;
	
	private IPEDResult(){
	}
	
	public IPEDResult(ItemId[] ids, float[] scores){
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
	
	public static IPEDResult get(IPEDMultiSource iSource, SearchResult luceneResult){
		IPEDResult result = new IPEDResult();
		result.scores = luceneResult.scores;
		result.ids = new ItemId[luceneResult.getLength()];
		
		int i = 0;
		for(int luceneId : luceneResult.docs){
			result.ids[i++] = iSource.getItemId(luceneId);
		}
		return result;
	}
	
	public static IPEDResult get(IPEDSource iSource, SearchResult luceneResult){
		IPEDResult result = new IPEDResult();
		result.scores = luceneResult.scores;
		result.ids = new ItemId[luceneResult.getLength()];
		
		int i = 0;
		for(int luceneId : luceneResult.docs){
			ItemId item = new ItemId(iSource.getSourceId(), iSource.getId(luceneId));
			result.ids[i++] = item;
		}
		return result;
	}
	
	public static SearchResult get(IPEDResult ipedResult, IPEDMultiSource iSource){
		SearchResult lResult = new SearchResult(0);
		lResult.length = ipedResult.getLength();
		lResult.scores = ipedResult.scores;
		lResult.docs = new int[lResult.length];
		
		int i = 0;
		for(ItemId item : ipedResult.getIds()){ 
			lResult.docs[i++] = iSource.getLuceneId(item);
		}
		return lResult;
	}
	
	public static SearchResult get(IPEDResult ipedResult, IPEDSource iSource){
		SearchResult lResult = new SearchResult(0);
		lResult.length = ipedResult.getLength();
		lResult.scores = ipedResult.scores;
		lResult.docs = new int[lResult.length];
		
		int i = 0;
		for(ItemId item : ipedResult.getIds()){ 
			lResult.docs[i++] = iSource.getLuceneId(item.getId());
		}
		return lResult;
	}
	
	@Override
	public IPEDResult clone(){
		IPEDResult result = new IPEDResult();
		result.ids = this.ids.clone();
		result.scores = this.scores.clone();
		return result;
	}
}
