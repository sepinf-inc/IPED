package iped.viewers.api;

import org.apache.lucene.search.Query;

public interface IQueryFilterer extends IFilterer{
	public boolean hasFiltersApplied();
    
    Query getQuery();
}
