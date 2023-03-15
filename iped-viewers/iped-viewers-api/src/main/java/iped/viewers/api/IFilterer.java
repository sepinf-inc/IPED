package iped.viewers.api;

import java.util.List;

/**
 * Interface to an object that provides filters (IFilter)
 * @author patrick.pdb
 *
 */
public interface IFilterer extends ClearFilterListener{
    
    List<IFilter> getDefinedFilters();

    public boolean hasFilters();
    public boolean hasFiltersApplied();
    
}
