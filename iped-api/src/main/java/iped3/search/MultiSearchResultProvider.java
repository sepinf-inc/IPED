package iped3.search;

import javax.swing.JTable;
import javax.swing.SortOrder;

import iped3.IPEDSource;

public interface MultiSearchResultProvider {
    MultiSearchResult getResults();

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
    IPEDSource getIPEDSource();

    /*
     * creates an independent search from the current search result
     */
    IPEDSearcher createNewSearch(String query);
}
