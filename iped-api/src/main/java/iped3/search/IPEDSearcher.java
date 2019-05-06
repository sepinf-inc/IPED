/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import org.apache.lucene.search.Query;

/**
 *
 * @author WERNECK
 */
public interface IPEDSearcher {

  int MAX_SIZE_TO_SCORE = 1000000;

  void cancel();

  LuceneSearchResult filtrarFragmentos(LuceneSearchResult prevResult) throws Exception;

  Query getQuery();

  LuceneSearchResult luceneSearch() throws Exception;

  MultiSearchResult multiSearch() throws Exception;

  SearchResult search() throws Exception;

  LuceneSearchResult searchAll() throws Exception;

  void setQuery(Query query);

  void setTreeQuery(boolean treeQuery);
  
}
