package iped.viewers.api;

import org.apache.lucene.search.Query;

public interface IQueryFilterer {
	
	public Query getQuery();
	public boolean hasFiltersApplied();

}
