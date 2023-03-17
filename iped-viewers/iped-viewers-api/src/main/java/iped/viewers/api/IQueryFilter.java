package iped.viewers.api;

import org.apache.lucene.search.Query;

public interface IQueryFilter extends IFilter {
    Query getQuery();
}
