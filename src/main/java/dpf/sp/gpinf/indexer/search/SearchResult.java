package dpf.sp.gpinf.indexer.search;

public class SearchResult {
	
	int[] ids;
	float[] scores;
	
	private SearchResult(){
	}
	
	public SearchResult(int[] ids, float[] scores){
		this.ids = ids;
		this.scores = scores;
	}
	
	public int[] getIds(){
		return ids;
	}
	
	public int getLength(){
		return ids.length;
	}
	
	public float[] getScores(){
		return scores;
	}
	
	public static SearchResult get(IPEDSource iSource, LuceneSearchResult luceneResult){
		SearchResult result = new SearchResult();
		result.scores = luceneResult.scores;
		result.ids = new int[luceneResult.getLength()];
		
		int i = 0;
		for(int luceneId : luceneResult.docs){
			result.ids[i++] = iSource.getId(luceneId);
		}
		return result;
	}
	
	public static LuceneSearchResult get(SearchResult ipedResult, IPEDSource iSource){
		LuceneSearchResult lResult = new LuceneSearchResult(0);
		lResult.length = ipedResult.getLength();
		lResult.scores = ipedResult.scores;
		lResult.docs = new int[lResult.length];
		
		int i = 0;
		for(int id : ipedResult.getIds()){
			lResult.docs[i++] = id;
		}
		return lResult;
	}
	
	@Override
	public SearchResult clone(){
		SearchResult result = new SearchResult();
		result.ids = this.ids.clone();
		result.scores = this.scores.clone();
		return result;
	}
}
