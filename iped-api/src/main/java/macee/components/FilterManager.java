package macee.components;

import macee.collection.CaseItemCollection;
import macee.filter.Filter;

public interface FilterManager {

    void applyFilter(String filterName);

    void removeFilter(String filterName);

    void resetFilters();

    CaseItemCollection getFilteredItems(Filter filter);

    CaseItemCollection getFilteredItems(String filterName);
}
