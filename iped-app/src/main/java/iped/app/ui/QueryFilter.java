package iped.app.ui;

import org.apache.lucene.search.Query;

import iped.viewers.api.IQueryFilter;

public class QueryFilter implements IQueryFilter {
    Query query;
    
    public QueryFilter(Query query) {
        this.query = query;
    }

    @Override
    public String getFilterExpression() {
        return query.toString();
    }
    
    public String toString() {
        return query.toString();
    }

}
