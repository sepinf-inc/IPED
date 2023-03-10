package iped.viewers.api;

import java.util.BitSet;
import java.util.Map;

import iped.search.IMultiSearchResult;

public interface IResultSetFilterer extends IFilterer {
    Map<Integer, BitSet> getFilteredBitSets(IMultiSearchResult input);

    /**
     * Gets a filter that represents all the individual defined filters applied
     * Ex:filterers like bookmarks and category, OR between individual filters is applied 
     */
    IFilter getFilter();
}
