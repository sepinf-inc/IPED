package iped.viewers.api;

public interface IResultSetFilterer extends IFilterer {
    /**
     * Gets a filter that represents all the individual defined filters applied
     * Ex:filterers like bookmarks and category, OR between individual filters is
     * applied
     */
    IFilter getFilter();
}
