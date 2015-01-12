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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.analyzing.AnalyzingQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.NumericConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.util.CancelableWorker;

public class PesquisarIndice extends CancelableWorker<SearchResult, Object> {

	volatile static int numFilters = 0;
	Query query;
	ProgressDialog progressDialog;

	public PesquisarIndice(Query query) {
		this.query = query;
	}

	private Set<String> getQueryStrings(Query query) {
		HashSet<String> result = new HashSet<String>();
		if (query != null)
			if (query instanceof BooleanQuery) {
				for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
					if (clause.getQuery() instanceof PhraseQuery && ((PhraseQuery) clause.getQuery()).getSlop() == 0) {
						String queryStr = clause.getQuery().toString();
						// System.out.println("phrase: " + queryStr);
						String field = "conteudo" + ":\"";
						if (queryStr.startsWith(field)) {
							String term = queryStr.substring(queryStr.indexOf(field) + field.length(), queryStr.lastIndexOf("\""));
							result.add(term.toLowerCase());
							// System.out.println(term);
						}

					} else {
						// System.out.println(clause.getQuery().toString());
						result.addAll(getQueryStrings(clause.getQuery()));
					}

				}
				// System.out.println("boolean query");
			} else {
				TreeSet<Term> termSet = new TreeSet<Term>();
				query.extractTerms(termSet);
				for (Term term : termSet)
					if (term.field().equalsIgnoreCase("conteudo")) {
						result.add(term.text().toLowerCase());
						// System.out.println(term.text());
					}
			}

		return result;

	}

	private Set<String> getQueryStrings() {
		Query query = null;
		if (App.get().query != null)
			try {
				Object termo = App.get().termo.getSelectedItem();
				String queryStr = termo != null ? termo.toString() : "";
				Analyzer analyzer = new WhitespaceAnalyzer(Versao.current);
				query = getQuery(queryStr, analyzer).rewrite(App.get().reader);

			} catch (Exception e) {
				e.printStackTrace();
			}

		Set<String> result = getQueryStrings(query);

		if (App.get().query != null)
			try {
				query = App.get().query.rewrite(App.get().reader);
				result.addAll(getQueryStrings(query));

			} catch (IOException e) {
				e.printStackTrace();
			}

		// for(String term : result)
		// System.out.println(term);

		return result;
	}

	public static Query getQueryWithFilter(String texto) throws ParseException, QueryNodeException {
		
		numFilters = 0;
		if (!texto.trim().isEmpty())
			numFilters++;
			
		String filtro = App.get().filtro.getSelectedItem().toString();
		
		if (!filtro.equals(App.FILTRO_TODOS) && !filtro.equals(App.FILTRO_SELECTED) && 
			App.get().filtro.getSelectedIndex() >= App.get().filtro.getItemCount() - App.get().categorias.size()) {
			
			filtro = "categoria:\"" + filtro.substring(Marcadores.CATEGORIES_PREFIX.length()).replace("\"", "\\\"") + "\"";
			if (texto.trim().isEmpty())
				texto = filtro;
			else
				texto = filtro + " && (" + texto + ")";
		}
		
		if (App.get().filtrarDuplicados.isSelected())
			if (texto.trim().isEmpty())
				texto = "primary:true";
			else
				texto = "primary:true" + " && (" + texto + ")";
		
		
		Query result = getQuery(texto, App.get().analyzer);
		
		if(!App.get().isReport){
			Query treeQuery = App.get().treeListener.treeQuery;
			if(App.get().recursiveTreeList.isSelected())
				treeQuery = App.get().treeListener.recursiveTreeQuery;
			
			if(treeQuery != null){
				if (texto.trim().isEmpty())
					result = treeQuery;
				else{
					BooleanQuery boolQuery = new BooleanQuery();
					boolQuery.add(result, Occur.MUST);
					boolQuery.add(treeQuery, Occur.MUST);
					result = boolQuery;
				}
				numFilters++;
			}
		}
		
		return result;
	}

	public static Query getQuery(String texto) throws ParseException, QueryNodeException {
		return getQuery(texto, App.get().analyzer);
	}

	public static Query getQuery(String texto, Analyzer analyzer) throws ParseException, QueryNodeException {

		if (texto.trim().isEmpty() || texto.equals(App.SEARCH_TOOL_TIP)) {
			return new MatchAllDocsQuery();

		} else {
			String[] fields = { "nome", "conteudo" };

			BooleanQuery result = new BooleanQuery();
			for (int i = 0; i < fields.length; i++) {
				AnalyzingQueryParser parser = new AnalyzingQueryParser(Versao.current, fields[i], analyzer);
				// ComplexPhraseQueryParser parser = new
				// ComplexPhraseQueryParser(Versao.current, fields[i], analyzer);
				parser.setAllowLeadingWildcard(false);
				parser.setFuzzyPrefixLength(2);
				parser.setFuzzyMinSim(0.7f);
				parser.setDateResolution(DateTools.Resolution.SECOND);
				parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
				result.add(parser.parse(texto), Occur.SHOULD);
			}
			
			
			return result;
			
			  //remove acentos e converte para caixa baixa como
			  /*char[] input = texto.toLowerCase().toCharArray();
			  char[] output = new char[input.length*4];
			  ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length); texto = (new String(output)).trim();
			  texto = texto.replaceAll(" and ", " AND ").replaceAll(" or ", " OR ");
			  texto = texto.replaceAll(" not ", " NOT ").replaceAll(" to ", " TO ");
			  */
			  
			  /*StandardQueryParser parser = new StandardQueryParser(analyzer);
			  parser.setMultiFields(fields);
			  parser.setAllowLeadingWildcard(true);
			  parser.setFuzzyPrefixLength(2);
			  parser.setFuzzyMinSim(0.7f);
			  parser.setDateResolution(DateTools.Resolution.SECOND);
			  parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
			  
			  NumericConfig config = new NumericConfig(4, NumberFormat.getInstance(), FieldType.NumericType.LONG);
			  HashMap<String,NumericConfig> numericConfigMap = new HashMap<String,NumericConfig>();
			  numericConfigMap.put("tamanho", config);
			  parser.setNumericConfigMap(numericConfigMap);
			  
			  return parser.parse(texto, null);
			 */

		}

	}
	
	
	  public SearchResult filtrarMarcador(SearchResult result, String labelName) throws Exception{
	  
		  	Marcadores marcadores = App.get().marcadores;
		  	int labelId = marcadores.getLabelId(labelName.substring(Marcadores.BOOKMARKS_PREFIX.length()));
			int removed = 0;
			App app = App.get();
			for (int i = 0; i < result.length; i++)
				if (!marcadores.hasLabel(app.ids[result.docs[i]], labelId)) {
					result.docs[i] = -1;
					removed++;
				}

			return clearResults(result, removed);
	  }
	  
	  public SearchResult filtrarMarcadores(SearchResult result){
		  	Marcadores marcadores = App.get().marcadores;
			int removed = 0;
			App app = App.get();
			for (int i = 0; i < result.length; i++)
				if (!marcadores.hasLabel(app.ids[result.docs[i]])) {
					result.docs[i] = -1;
					removed++;
				}

			return clearResults(result, removed);
	  }
	  

	  public SearchResult filtrarSelecionados(SearchResult result) throws Exception {

			Marcadores marcadores = App.get().marcadores;
			int removed = 0;
			for (int i = 0; i < result.length; i++)
				if (!marcadores.selected[result.docs[i]]) {
					result.docs[i] = -1;
					removed++;
				}

			return clearResults(result, removed);
	}
	 

	@Override
	public SearchResult doInBackground() {
		
		SearchResult result = null;
		try {
			final PesquisarIndice pesquisa = this;
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					progressDialog = new ProgressDialog(App.get(), pesquisa, true);
				}
			});

			result = pesquisar();

			String filtro = App.get().filtro.getSelectedItem().toString();
			
			if(App.get().filtro.getSelectedIndex() != 0 && !filtro.equals(Marcadores.CATEGORIES_DIV))
				numFilters++;
			
			if (filtro.equals(App.FILTRO_SELECTED))
				result = filtrarSelecionados(result);
			
			else if(filtro.equals(Marcadores.BOOKMARKS_DIV))
				result = filtrarMarcadores(result);
			
			else if(App.get().filtro.getSelectedIndex() >= Marcadores.BOOKMARKS_DIV_INDEX + 1 && 
					App.get().filtro.getSelectedIndex() < App.get().marcadores.labelNames.size() + Marcadores.BOOKMARKS_DIV_INDEX + 1)
				result = filtrarMarcador(result, App.get().filtro.getSelectedItem().toString());
			 
			countVolume(result);

			App.get().highlightTerms = getQueryStrings();

		} catch (Throwable e) {
			e.printStackTrace();
			return new SearchResult(0);
		}

		return result;
	}

	@Override
	public void done() {

		progressDialog.close();
		if(numFilters > 1)
			App.get().multiFilterAlert.setVisible(true);
		else
			App.get().multiFilterAlert.setVisible(false);
		
		if (!this.isCancelled())
			try {
				App.get().results = this.get();
				App.get().resultsModel.fireTableDataChanged();
				App.get().galleryModel.fireTableStructureChanged();
				App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(App.get().results.length);
				App.get().resultsTable.getTableHeader().repaint();
			} catch (Exception e) {
				e.printStackTrace();
			}

	}

	private static volatile Thread lastCounter;
	private long volume = 0;

	public void countVolume(final SearchResult result) {

		if (lastCounter != null)
			lastCounter.interrupt();

		Thread counter = new Thread() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().resultsTable.getColumnModel().getColumn(App.get().resultsTable.convertColumnIndexToView(6)).setHeaderValue("Tamanho (...)");
						App.get().resultsTable.getTableHeader().repaint();
					}
				});
				volume = 0;
				IndexReader reader = App.get().reader;
				for (int doc : result.docs) {
					try {
						String len = reader.document(doc).get("tamanho");
						if (!len.isEmpty())
							volume += Long.valueOf(len);

					} catch (IOException e) {
						// e.printStackTrace();
					}

					if (Thread.currentThread().isInterrupted())
						return;
				}
				volume = volume / (1024 * 1024);

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().resultsTable.getColumnModel().getColumn(App.get().resultsTable.convertColumnIndexToView(6)).setHeaderValue(
								"Tamanho (" + NumberFormat.getNumberInstance().format(volume) + "MB)");
						App.get().resultsTable.getTableHeader().repaint();
					}
				});

			}
		};

		counter.start();
		lastCounter = counter;

	}

	@Override
	public boolean doCancel(boolean mayInterruptIfRunning) {

		try {
			System.out.println("Pesquisa cancelada!");
			App.get().reader.close();
			String index = App.get().codePath + "/../index";
			Directory directory = FSDirectory.open(new File(index));
			App.get().reader = IndexReader.open(directory);
			App.get().searcher = new IndexSearcher(App.get().reader);
			App.get().searcher.setSimilarity(new IndexerSimilarity());

		} catch (IOException e) {
			e.printStackTrace();
		}

		return cancel(mayInterruptIfRunning);
	}

	public SearchResult pesquisar() throws Exception {
		// System.out.println(filtrarVersoes1(filtrarFragmentos1(pesquisarTodos1())).length);
		return filtrarVersoes(filtrarFragmentos(pesquisarTodos()));
	}



	public ScoreDoc[] pesquisarTodos1() throws IOException {
		return App.get().searcher.search(query, Integer.MAX_VALUE).scoreDocs;
	}

	public SearchResult pesquisarTodos() throws IOException {

		int maxResults = 1000000;
		SearchResult searchResult = new SearchResult(0);

		ScoreDoc[] scoreDocs = null;
		do {
			ScoreDoc lastScoreDoc = null;
			if (scoreDocs != null)
				lastScoreDoc = scoreDocs[scoreDocs.length - 1];

			scoreDocs = App.get().searcher.searchAfter(lastScoreDoc, query, maxResults).scoreDocs;
			searchResult = searchResult.addResults(scoreDocs);

		} while (scoreDocs.length == maxResults);

		return searchResult;
	}

	public class ScoreDocComparator implements Comparator<ScoreDoc> {
		int mul = 1;

		public ScoreDocComparator(int mul) {
			this.mul = mul;
		}

		@Override
		public int compare(ScoreDoc o1, ScoreDoc o2) {
			int result;
			if (o1.score == o2.score) {
				if (o1.doc == o2.doc)
					result = 0;
				else if (o1.doc > o2.doc)
					result = 1;
				else
					result = -1;
			} else if (o1.score > o2.score)
				result = 1;
			else
				result = -1;
			return result * mul;
		}

	}

	public ScoreDoc[] filtrarFragmentos1(ScoreDoc[] scoreDocs) throws Exception {
		HashMap<Integer, ScoreDoc> duplicates = new HashMap<Integer, ScoreDoc>();
		TreeSet<ScoreDoc> uniqueResults = new TreeSet<ScoreDoc>(new ScoreDocComparator(1));
		for (int i = 0; i < scoreDocs.length; i++) {
			Integer id = App.get().splitedDocs.get(scoreDocs[i].doc);
			if (id != null) {
				ScoreDoc prevDoc = duplicates.get(id);
				if (prevDoc == null) {
					uniqueResults.add(scoreDocs[i]);
					duplicates.put(id, scoreDocs[i]);
				} else {
					// retorna primeiro fragmento
					if (prevDoc.doc > scoreDocs[i].doc) {
						uniqueResults.remove(prevDoc);
						uniqueResults.add(scoreDocs[i]);
						duplicates.put(id, scoreDocs[i]);
					}
				}
			} else
				uniqueResults.add(scoreDocs[i]);
		}
		return uniqueResults.descendingSet().toArray(new ScoreDoc[0]);

	}

	/*
	 * public ScoreDoc[] filtrarFragmentos2(ScoreDoc[] scoreDocs)throws
	 * Exception{ HashMap<Integer,ScoreDoc> duplicates = new
	 * HashMap<Integer,ScoreDoc>(); int frags = 0; for (int i = 0; i <
	 * scoreDocs.length; i++) { Integer id =
	 * App.get().splitedDocs.get(scoreDocs[i].doc); if(id != null){ ScoreDoc
	 * prevDoc = duplicates.get(id); if(prevDoc == null){ duplicates.put(id,
	 * scoreDocs[i]); }else{ scoreDocs[i] = null; frags++; } } }
	 * 
	 * ScoreDoc[] result = new ScoreDoc[scoreDocs.length - frags]; int j = 0;
	 * for(int i = 0; i < scoreDocs.length; i++) if(scoreDocs[i] != null)
	 * result[i - j] = scoreDocs[i]; else j++;
	 * 
	 * return result;
	 * 
	 * }
	 */

	public SearchResult filtrarFragmentos(SearchResult prevResult) throws Exception {
		HashMap<Integer, Integer> duplicates = new HashMap<Integer, Integer>();
		int frags = 0;
		for (int i = 0; i < prevResult.length; i++) {
			Integer id = App.get().splitedDocs.get(prevResult.docs[i]);
			if (id != null) {
				Integer prevDoc = duplicates.get(id);
				if (prevDoc == null) {
					duplicates.put(id, prevResult.docs[i]);
				} else {
					prevResult.docs[i] = -1;
					frags++;
				}
			}
		}

		return clearResults(prevResult, frags);

	}

	/*
	 * public ScoreDoc[] filtrarFragmentos(ScoreDoc[] scoreDocs)throws
	 * Exception{ HashMap<Integer,Boolean> duplicates = new
	 * HashMap<Integer,Boolean>(); ArrayList<ScoreDoc> uniqueResults = new
	 * ArrayList<ScoreDoc>(); for (int i = 0; i < scoreDocs.length; i++) {
	 * Integer id = App.get().splitedDocs.get(scoreDocs[i].doc); if(id != null){
	 * if(duplicates.get(id) == null) uniqueResults.add(scoreDocs[i]);
	 * duplicates.put(id, true); }else uniqueResults.add(scoreDocs[i]); } return
	 * uniqueResults.toArray(new ScoreDoc[0]);
	 * 
	 * }
	 * 
	 * 
	 * public ScoreDoc[] filtrarVersoes1(ScoreDoc[] scoreDocs)throws Exception{
	 * if(App.get().viewToRawFileMap.size() == 0) return scoreDocs;
	 * HashSet<Integer> originals = new HashSet<Integer>(); TreeMap<Integer,
	 * ScoreDoc> uniqueResults = new TreeMap<Integer, ScoreDoc>(); for (int i =
	 * 0; i < scoreDocs.length; i++) { Integer original =
	 * App.get().viewToRawFileMap.get(scoreDocs[i].doc); if(original == null){
	 * if(!originals.contains(scoreDocs[i].doc))
	 * uniqueResults.put(scoreDocs[i].doc, scoreDocs[i]); }else{
	 * if(uniqueResults.remove(original) == null) originals.add(original);
	 * uniqueResults.put(scoreDocs[i].doc, scoreDocs[i]); } } ScoreDoc[] result
	 * = uniqueResults.values().toArray(new ScoreDoc[0]); Arrays.sort(result,
	 * new ScoreDocComparator(-1)); return result;
	 * 
	 * }
	 * 
	 * public ScoreDoc[] filtrarVersoes2(ScoreDoc[] scoreDocs)throws Exception{
	 * //if(App.get().viewToRawFileMap.size() == 0) // return scoreDocs;
	 * TreeMap<Integer, Integer> addedMap = new TreeMap<Integer, Integer>();
	 * ArrayList<ScoreDoc> results = new ArrayList<ScoreDoc>(); for (int i = 0;
	 * i < scoreDocs.length; i++) { Integer original =
	 * App.get().viewToRawFileMap.get(scoreDocs[i].doc); if(original == null){
	 * if(App.get().rawToViewFileMap.containsKey(scoreDocs[i].doc)){
	 * if(!addedMap.containsKey(scoreDocs[i].doc)){ results.add(scoreDocs[i]);
	 * addedMap.put(scoreDocs[i].doc, results.size() - 1); }else
	 * addedMap.remove(scoreDocs[i].doc); }else results.add(scoreDocs[i]);
	 * }else{ Integer pos = addedMap.get(original); if(pos != null){
	 * results.set(pos, scoreDocs[i]); addedMap.remove(original); }else{
	 * results.add(scoreDocs[i]); addedMap.put(original, null); } } }
	 * 
	 * ScoreDoc[] result = results.toArray(new ScoreDoc[results.size()]);
	 * //Arrays.sort(result, new ScoreDocComparator(-1)); return result;
	 * 
	 * }
	 */

	public SearchResult filtrarVersoes(SearchResult prevResult) throws Exception {
		if (App.get().viewToRawMap.getMappings() == 0)
			return prevResult;

		TreeMap<Integer, Integer> addedMap = new TreeMap<Integer, Integer>();
		int versions = 0;
		for (int i = 0; i < prevResult.length; i++) {
			Integer original = App.get().viewToRawMap.getRaw(prevResult.docs[i]);
			if (original == null) {
				if (App.get().viewToRawMap.isRaw(prevResult.docs[i])) {
					if (!addedMap.containsKey(prevResult.docs[i])) {
						addedMap.put(prevResult.docs[i], i);
					} else {
						addedMap.remove(prevResult.docs[i]);
						prevResult.docs[i] = -1;
						versions++;
					}
				}
			} else {
				Integer pos = addedMap.get(original);
				if (pos != null) {
					prevResult.docs[pos] = prevResult.docs[i];
					prevResult.scores[pos] = prevResult.scores[i];
					prevResult.docs[i] = -1;
					versions++;
					addedMap.remove(original);
				} else
					addedMap.put(original, null);

			}
		}

		// Arrays.sort(result, new ScoreDocComparator(-1));
		return clearResults(prevResult, versions);

	}

	private SearchResult clearResults(SearchResult prevResult, int blanks) {

		SearchResult result = new SearchResult(prevResult.length - blanks);
		int j = 0;
		for (int i = 0; i < prevResult.length; i++)
			if (prevResult.docs[i] != -1) {
				result.docs[i - j] = prevResult.docs[i];
				result.scores[i - j] = prevResult.scores[i];
			} else
				j++;

		return result;
	}

}
