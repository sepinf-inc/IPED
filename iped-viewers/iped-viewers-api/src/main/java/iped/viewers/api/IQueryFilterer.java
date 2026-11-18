package iped.viewers.api;

import org.apache.lucene.search.Query;

public interface IQueryFilterer extends IFilterer {
    Query getQuery();
}
