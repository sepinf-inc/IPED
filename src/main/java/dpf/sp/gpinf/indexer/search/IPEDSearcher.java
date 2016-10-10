/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.io.InterruptedIOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.IndexItem;

public class IPEDSearcher {

	private static Logger LOGGER = LoggerFactory.getLogger(IPEDSearcher.class);
		
	IPEDSource ipedCase;
	Query query;
	String queryText;
	boolean treeQuery;
	NoScoringCollector collector;
	
	private volatile boolean canceled;

	public IPEDSearcher(IPEDSource ipedCase, Query query) {
		this.ipedCase = ipedCase;
		this.query = query;
	}
	
	public IPEDSearcher(IPEDSource ipedCase, String query) {
		this.ipedCase = ipedCase;
		this.queryText = query;
	}
	
	public void setTreeQuery(boolean treeQuery){
		this.treeQuery = treeQuery;
	}
	
	public void setQuery(Query query){
		this.query = query;
	}
	
	public Query getQuery(){
		return query;
	}

	public void cancel(){
		canceled = true;
		if(collector != null)
			collector.cancel();
	}

	public SearchResult pesquisar() throws Exception {
		return filtrarVersoes(filtrarFragmentos(pesquisarTodos()));
	}

	public SearchResult pesquisarTodos() throws Exception {
		
		if(query == null)
			query = new QueryBuilder(ipedCase).getQuery(queryText);
		
		if(!treeQuery)
			query = getNonTreeQuery();
		
		
		collector = new NoScoringCollector(ipedCase.reader.maxDoc());
		try{
			ipedCase.searcher.search(query, collector);
			
		}catch(InterruptedIOException e){
			//e.printStackTrace();
		}
		//não calcula scores (lento) quando resultado é mto grande
		if(collector.getTotalHits() > 1000000 || canceled)
			return collector.getSearchResults();
		
		//obtém resultados calculando score
		SearchResult searchResult = new SearchResult(0);
		int maxResults = 100000;
		ScoreDoc[] scoreDocs = null;
		do {
			ScoreDoc lastScoreDoc = null;
			if (scoreDocs != null)
				lastScoreDoc = scoreDocs[scoreDocs.length - 1];
			
			scoreDocs = ipedCase.searcher.searchAfter(lastScoreDoc, query, maxResults).scoreDocs;
			
			searchResult = searchResult.addResults(scoreDocs);
			
		} while (scoreDocs.length > 0 && !canceled);
		
		return searchResult;
	}
	
	private Query getNonTreeQuery(){
		BooleanQuery result = new BooleanQuery();
		result.add(query, Occur.MUST);
		result.add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST_NOT);
		return result;
	}

	public SearchResult filtrarFragmentos(SearchResult prevResult) throws Exception {
		if(ipedCase instanceof IPEDMultiSource)
			return filtrarFragmentosMulti(prevResult);
		
		HashSet<Integer> duplicates = new HashSet<Integer>();
		for (int i = 0; i < prevResult.length; i++) {
			int id = ipedCase.getId(prevResult.docs[i]);
			if (ipedCase.isSplited(id)) {
				if (!duplicates.contains(id)) {
					duplicates.add(id);
				} else {
					prevResult.docs[i] = -1;
				}
			}
		}

		prevResult.clearResults();
		return prevResult;

	}
	
	private SearchResult filtrarFragmentosMulti(SearchResult prevResult) throws Exception {
		HashSet<Integer> duplicates = new HashSet<Integer>();
		for (int i = 0; i < prevResult.length; i++) {
			IPEDSource atomicSource = ((IPEDMultiSource)ipedCase).getAtomicSource(prevResult.docs[i]);
			int id = ((IPEDMultiSource)ipedCase).getItemId(prevResult.docs[i]).getId();
			if (atomicSource.isSplited(id)) {
				if (!duplicates.contains(id)) {
					duplicates.add(id);
				} else {
					prevResult.docs[i] = -1;
				}
			}
		}
		prevResult.clearResults();
		return prevResult;
		
	}

	private SearchResult filtrarVersoes(SearchResult prevResult) throws Exception {
		if (ipedCase.viewToRawMap.getMappings() == 0)
			return prevResult;

		TreeMap<Integer, Integer> addedMap = new TreeMap<Integer, Integer>();
		for (int i = 0; i < prevResult.length; i++) {
		    int id = ipedCase.getId(prevResult.docs[i]);
			Integer original = ipedCase.viewToRawMap.getRaw(id);
			if (original == null) {
				if (ipedCase.viewToRawMap.isRaw(id)) {
					if (!addedMap.containsKey(id)) {
						addedMap.put(id, i);
					} else {
						addedMap.remove(id);
						prevResult.docs[i] = -1;
					}
				}
			} else {
				Integer pos = addedMap.get(original);
				if (pos != null) {
					prevResult.docs[pos] = prevResult.docs[i];
					prevResult.scores[pos] = prevResult.scores[i];
					prevResult.docs[i] = -1;
					addedMap.remove(original);
				} else
					addedMap.put(original, null);

			}
		}

		prevResult.clearResults();
		return prevResult;

	}

}
