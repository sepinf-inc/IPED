package iped.viewers.api;

import java.util.List;

/**
 * Interface to an object that provides filters (IFilter)
 * 
 * @author patrick.pdb
 *
 */
public interface IFilterer extends ClearFilterListener, ActionListenerControl {

    public static final int ENABLE_FILTER_EVENT = 0;
    public static final int DISABLE_FILTER_EVENT = 1;

    List<IFilter> getDefinedFilters();

    /**
     * Informs if there is at least one filter defined by the filterer
     * 
     * @return
     */
    public boolean hasFilters();

    /**
     * Informs if the defined filters inside the filterer are to be applied (used)
     * 
     * @return
     */
    public boolean hasFiltersApplied();

    default public String getFilterName() {
        try {
            return Messages.get(this.getClass().getName().replace("$", ".") + ".filtererName");
        } catch (Exception e) {
            return this.getClass().getName();
        }
    }


}
