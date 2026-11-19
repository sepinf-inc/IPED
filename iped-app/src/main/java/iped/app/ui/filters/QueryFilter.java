package iped.app.ui.filters;

import java.util.Objects;

import org.apache.lucene.search.Query;

import iped.viewers.api.IQueryFilter;

public class QueryFilter implements IQueryFilter {
    Query query;
    String title = null;

    public QueryFilter(Query query) {
        this.query = query;
    }

    public QueryFilter(String title, Query query2) {
        this(query2);
        this.title = title;
    }

    @Override
    public Query getQuery() {
        return query;
    }

    public String toString() {
        return title != null ? title : query.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, title, this.getClass());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        QueryFilter other = (QueryFilter) obj;
        return Objects.equals(query, other.query);
    }

}
