package iped.viewers.api;

import org.apache.lucene.search.Query;

/**
 * A filter that exposes a lucene query to be used to filter the result set
 * 
 * @author patrick.pdb
 */
public interface IQueryFilter extends IFilter {
    Query getQuery();
}
