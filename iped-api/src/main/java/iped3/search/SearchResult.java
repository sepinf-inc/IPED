package iped3.search;

import java.util.ArrayList;
import java.util.List;

import iped3.IPEDSource;

public class SearchResult {
	
	int[] ids;
	float[] scores;
	
	private SearchResult(){
	}
	
	public SearchResult(int[] ids, float[] scores){
		this.ids = ids;
		this.scores = scores;
	}
	
	public int getId(int i){
		return ids[i];
	}
	
	public float getScore(int i){
		return scores[i];
	}
	
	public int getLength(){
		return ids.length;
	}
	
	public List<Integer> getIds(){
	    List<Integer> ids = new ArrayList<>();
	    for(int i : this.ids)
	        ids.add(i);
	    return ids;
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
		for(int id : ipedResult.ids){
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
