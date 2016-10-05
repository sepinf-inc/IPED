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
	
	public static IPEDResult get(IPEDSource iSource, SearchResult sr){
		IPEDResult result = new IPEDResult();
		result.scores = sr.scores;
		result.ids = new ItemId[sr.getLength()];
		
		int i = 0;
		for(int luceneId : sr.docs){
			IPEDSource atomicSource = iSource.getAtomicCase(luceneId);
			int sourceId = atomicSource.getSourceId();
			int id = atomicSource.getId(luceneId - iSource.getBaseLuceneId(atomicSource));
			result.ids[i++] = new ItemId(sourceId, id);
		}
		return result;
	}
}
