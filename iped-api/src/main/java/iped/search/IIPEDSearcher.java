/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped.search;

import org.apache.lucene.search.Query;

/**
 *
 * @author WERNECK
 */
public interface IIPEDSearcher {

    int MAX_SIZE_TO_SCORE = 1000000;

    void cancel();

    Query getQuery();

    IMultiSearchResult multiSearch() throws Exception;

    SearchResult search() throws Exception;

    void setQuery(Query query);

    void setTreeQuery(boolean treeQuery);

}
