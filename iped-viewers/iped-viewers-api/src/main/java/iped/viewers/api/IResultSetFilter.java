package iped.viewers.api;

import java.io.IOException;

import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;

public interface IResultSetFilter extends IFilter {
    IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException;
}
