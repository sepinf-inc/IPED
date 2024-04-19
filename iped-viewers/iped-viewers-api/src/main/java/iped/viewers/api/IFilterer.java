package iped.viewers.api;

import java.util.List;

/**
 * Interface to an object that provides filters (IFilter)
 * 
 * @author patrick.pdb
 *
 */
public interface IFilterer extends ClearFilterListener {

    List<IFilter> getDefinedFilters();

    public boolean hasFilters();

    public boolean hasFiltersApplied();

    default public String getFilterName() {
        try {
            return Messages.get(this.getClass().getName().replace("$", ".") + ".filtererName");
        } catch (Exception e) {
            return this.getClass().getName();
        }
    }
}
