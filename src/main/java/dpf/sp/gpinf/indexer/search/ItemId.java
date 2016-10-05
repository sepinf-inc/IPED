package dpf.sp.gpinf.indexer.search;

public class ItemId implements Comparable<ItemId>{
	
	private int sourceId, id;
	
	public ItemId(int sourceId, int id){
		this.sourceId = sourceId;
		this.id = id;
	}

	@Override
	public int compareTo(ItemId o) {
		if(sourceId == o.sourceId)
			return id - o.id;
		else
			return sourceId - o.sourceId;
	}
	
	@Override
	public boolean equals(Object o){
		return compareTo((ItemId)o) == 0;
	}
	
	public int getSourceId(){
		return this.sourceId;
	}
	
	public int getId(){
		return this.id;
	}

}
