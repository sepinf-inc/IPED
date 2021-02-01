package iped3.search;

import javax.swing.JTable;
import javax.swing.SortOrder;

import iped3.IIPEDSource;

public interface IMultiSearchResultProvider {
    IMultiSearchResult getResults();

    /*
     * return the source of the current multisearch result
     */
    JTable getResultsTable();

    /*
     * return the name of the column used to sort the result
     */
    String getSortColumn();

    /*
     * return the order of sorting used, ASCENDING OR DESCENDING
     */
    SortOrder getSortOrder();

    /*
     * return the source of the current multisearch result
     */
    IIPEDSource getIPEDSource();

    /*
     * creates an independent search from the current search result
     */
    IIPEDSearcher createNewSearch(String query);
}
