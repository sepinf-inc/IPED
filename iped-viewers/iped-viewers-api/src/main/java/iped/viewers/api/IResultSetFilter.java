package iped.viewers.api;

import java.io.IOException;

import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

/**
 * Represents a filter that exposes a method that gets the input resultSet and
 * returns a filtered resultSet
 * 
 * @author patrick.pdb
 */
public interface IResultSetFilter extends IFilter {
    IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException;
}
