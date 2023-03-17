package iped.app.ui.filters;

import org.apache.lucene.search.Query;

import iped.viewers.api.IQueryFilter;

public class QueryFilter implements IQueryFilter {
    Query query;
    
    public QueryFilter(Query query) {
        this.query = query;
    }

    @Override
    public Query getQuery() {
        return query;
    }
    
    public String toString() {
        return query.toString();
    }

}
