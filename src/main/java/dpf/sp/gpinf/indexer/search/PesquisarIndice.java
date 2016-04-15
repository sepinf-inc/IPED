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

import java.awt.Dialog.ModalityType;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.FieldType.NumericType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.NumericUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;

public class PesquisarIndice extends CancelableWorker<SearchResult, Object> {

  private static Logger LOGGER = LoggerFactory.getLogger(PesquisarIndice.class);
  private static Analyzer spaceAnalyzer = new WhitespaceAnalyzer(Versao.current);

  private static HashMap<String, NumericConfig> numericConfigMap;

  volatile static int numFilters = 0;
  Query query;
  String queryText;
  ProgressDialog progressDialog;
  boolean treeQuery;

  public PesquisarIndice(Query query) {
    this.query = query;
  }

  public PesquisarIndice(String query) {
    this.queryText = query;
  }

  public PesquisarIndice(Query query, boolean treeQuery) {
    this.query = query;
    this.treeQuery = treeQuery;
  }

  private Set<String> getQueryStrings(Query query) {
    HashSet<String> result = new HashSet<String>();
    if (query != null) {
      if (query instanceof BooleanQuery) {
        for (BooleanClause clause : ((BooleanQuery) query).clauses()) {
          if (clause.getQuery() instanceof PhraseQuery && ((PhraseQuery) clause.getQuery()).getSlop() == 0) {
            String queryStr = clause.getQuery().toString();
            // System.out.println("phrase: " + queryStr);
            String field = IndexItem.CONTENT + ":\"";
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
        for (Term term : termSet) {
          if (term.field().equalsIgnoreCase(IndexItem.CONTENT)) {
            result.add(term.text().toLowerCase());
            // System.out.println(term.text());
          }
        }
      }
    }

    return result;

  }

  private Set<String> getQueryStrings() {
    Query query = null;
    if (App.get().getQuery() != null) {
      try {
        Object termo = App.get().termo.getSelectedItem();
        String queryStr = termo != null ? termo.toString() : "";
        query = getQuery(queryStr, spaceAnalyzer).rewrite(App.get().reader);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    Set<String> result = getQueryStrings(query);

    if (App.get().getQuery() != null) {
      try {
        query = App.get().getQuery().rewrite(App.get().reader);
        result.addAll(getQueryStrings(query));

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // for(String term : result)
    // System.out.println(term);
    return result;
  }

  public static Query getQueryWithFilter(String texto) throws ParseException, QueryNodeException {

    numFilters = 0;
    if (!texto.trim().isEmpty()) {
      numFilters++;
    }

    if (App.get().filtro.getSelectedIndex() > 1) {
      String filter = App.get().filtro.getSelectedItem().toString();
      filter = App.get().filterManager.getFilterExpression(filter);
      if (texto.trim().isEmpty()) {
        texto = filter;
      } else {
        texto = "(" + texto + ") && (" + filter + ")";
      }
      numFilters++;
    }

    Query result = getQuery(texto, App.get().getAnalyzer());

    if (App.get().categoryListener.query != null) {
      BooleanQuery boolQuery = new BooleanQuery();
      boolQuery.add(result, Occur.MUST);
      boolQuery.add(App.get().categoryListener.query, Occur.MUST);
      result = boolQuery;
      numFilters++;
    }

    if (!App.get().isFTKReport) {
      Query treeQuery = App.get().treeListener.treeQuery;
      if (App.get().recursiveTreeList.isSelected()) {
        treeQuery = App.get().treeListener.recursiveTreeQuery;
      }

      if (treeQuery != null) {
        BooleanQuery boolQuery = new BooleanQuery();
        boolQuery.add(result, Occur.MUST);
        boolQuery.add(treeQuery, Occur.MUST);
        result = boolQuery;
        numFilters++;
      }
    }

    //System.out.println(result.toString());
    return result;
  }

  public static Query getQuery(String texto) throws ParseException, QueryNodeException {
    return getQuery(texto, App.get().getAnalyzer());
  }

  public static Query getQuery(String texto, Analyzer analyzer) throws ParseException, QueryNodeException {

    if (texto.trim().isEmpty() || texto.equals(App.SEARCH_TOOL_TIP)) {
      return new MatchAllDocsQuery();
    } else {
      String[] fields = {IndexItem.NAME, IndexItem.CONTENT};

      StandardQueryParser parser = new StandardQueryParser(analyzer);
      parser.setMultiFields(fields);
      parser.setAllowLeadingWildcard(true);
      parser.setFuzzyPrefixLength(2);
      parser.setFuzzyMinSim(0.7f);
      parser.setDateResolution(DateTools.Resolution.SECOND);
      parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
      parser.setNumericConfigMap(getNumericConfigMap());

      //remove acentos, pois StandardQueryParser não normaliza wildcardQueries
      if (analyzer != spaceAnalyzer) {
        char[] input = texto.toCharArray();
        char[] output = new char[input.length * 4];
        FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
        texto = (new String(output)).trim();
      }

      return parser.parse(texto, null);
    }

  }

  private static HashMap<String, NumericConfig> getNumericConfigMap() {

    if (numericConfigMap != null) {
      return numericConfigMap;
    }

    numericConfigMap = new HashMap<String, NumericConfig>();

    DecimalFormat nf = new DecimalFormat();
    NumericConfig configLong = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.LONG);
    NumericConfig configInt = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.INT);
    NumericConfig configFloat = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.FLOAT);
    NumericConfig configDouble = new NumericConfig(NumericUtils.PRECISION_STEP_DEFAULT, nf, NumericType.DOUBLE);

    numericConfigMap.put(IndexItem.LENGTH, configLong);
    numericConfigMap.put(IndexItem.ID, configInt);
    numericConfigMap.put(IndexItem.SLEUTHID, configInt);
    if (!App.get().isFTKReport) {
      numericConfigMap.put(IndexItem.PARENTID, configInt);
    }

    for (Entry<String, Class> e : IndexItem.getMetadataTypes().entrySet()) {
      if (e.getValue().equals(Integer.class) || e.getValue().equals(Byte.class)) {
        numericConfigMap.put(e.getKey(), configInt);
      } else if (e.getValue().equals(Long.class)) {
        numericConfigMap.put(e.getKey(), configLong);
      } else if (e.getValue().equals(Float.class)) {
        numericConfigMap.put(e.getKey(), configFloat);
      } else if (e.getValue().equals(Double.class)) {
        numericConfigMap.put(e.getKey(), configDouble);
      }
    }

    return numericConfigMap;
  }

  public SearchResult filtrarMarcadores(SearchResult result, Set<String> labelNames) throws Exception {

    Marcadores marcadores = App.get().marcadores;
    int[] ids = App.get().getIDs();

    int[] labelIds = new int[labelNames.size()];
    int i = 0;
    for (String labelName : labelNames) {
      labelIds[i++] = marcadores.getLabelId(labelName);
    }
    byte[] labelBits = marcadores.getLabelBits(labelIds);

    int removed = 0;
    for (i = 0; i < result.length; i++) {
      if (!marcadores.hasLabel(ids[result.docs[i]], labelBits)) {
        result.docs[i] = -1;
        removed++;
      }
    }

    return clearResults(result, removed);
  }

  public SearchResult filtrarSemEComMarcadores(SearchResult result, Set<String> labelNames) throws Exception {

    Marcadores marcadores = App.get().marcadores;
    int[] ids = App.get().getIDs();

    int[] labelIds = new int[labelNames.size()];
    int i = 0;
    for (String labelName : labelNames) {
      labelIds[i++] = marcadores.getLabelId(labelName);
    }
    byte[] labelBits = marcadores.getLabelBits(labelIds);

    int removed = 0;
    for (i = 0; i < result.length; i++) {
      if (marcadores.hasLabel(ids[result.docs[i]]) && !marcadores.hasLabel(ids[result.docs[i]], labelBits)) {
        result.docs[i] = -1;
        removed++;
      }
    }

    return clearResults(result, removed);
  }

  public SearchResult filtrarSemMarcadores(SearchResult result) {
    Marcadores marcadores = App.get().marcadores;
    int removed = 0;
    int[] ids = App.get().getIDs();
    for (int i = 0; i < result.length; i++) {
      if (marcadores.hasLabel(ids[result.docs[i]])) {
        result.docs[i] = -1;
        removed++;
      }
    }

    return clearResults(result, removed);
  }

  public SearchResult filtrarSelecionados(SearchResult result) throws Exception {

    Marcadores marcadores = App.get().marcadores;
    int[] ids = App.get().getIDs();
    int removed = 0;
    for (int i = 0; i < result.length; i++) {
      if (!marcadores.selected[ids[result.docs[i]]]) {
        result.docs[i] = -1;
        removed++;
      }
    }

    return clearResults(result, removed);
  }

  @Override
  public SearchResult doInBackground() {

    synchronized (this.getClass()) {

      if (this.isCancelled()) {
        return null;
      }

      SearchResult result = null;
      try {
        progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);

        result = pesquisar();

        String filtro = App.get().filtro.getSelectedItem().toString();
        if (filtro.equals(App.FILTRO_SELECTED)) {
          result = filtrarSelecionados(result);
          numFilters++;
        }

        HashSet<String> bookmarkSelection = (HashSet<String>) App.get().bookmarksListener.selection.clone();
        if (!bookmarkSelection.isEmpty() && !bookmarkSelection.contains(BookmarksTreeModel.ROOT)) {
          numFilters++;
          if (bookmarkSelection.contains(BookmarksTreeModel.NO_BOOKMARKS)) {
            if (bookmarkSelection.size() == 1) {
              result = filtrarSemMarcadores(result);
            } else {
              bookmarkSelection.remove(BookmarksTreeModel.NO_BOOKMARKS);
              result = filtrarSemEComMarcadores(result, bookmarkSelection);
            }
          } else {
            result = filtrarMarcadores(result, bookmarkSelection);
          }

        }

        countVolume(result);

        App.get().highlightTerms = getQueryStrings();

      } catch (Throwable e) {
        e.printStackTrace();
        return new SearchResult(0);

      }

      return result;
    }

  }

  @Override
  public void done() {

    if (numFilters > 1) {
      App.get().multiFilterAlert.setVisible(true);
    } else {
      App.get().multiFilterAlert.setVisible(false);
    }

    if (!this.isCancelled()) {
      try {
        App.get().results = this.get();
        App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(this.get().length);
        App.get().resultsTable.getTableHeader().repaint();
        if (App.get().results.length < 1 << 24) {
          App.get().resultsTable.getRowSorter().allRowsChanged();
          App.get().resultsTable.getRowSorter().setSortKeys(App.get().resultSortKeys);
        } else {
          App.get().resultsModel.fireTableDataChanged();
          App.get().galleryModel.fireTableStructureChanged();
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (progressDialog != null) {
      progressDialog.close();
    }

  }

  private static volatile Thread lastCounter;
  private long volume = 0;

  public void countVolume(final SearchResult result) {

    if (lastCounter != null) {
      lastCounter.interrupt();
    }

    Thread counter = new Thread() {
      @Override
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            App.get().resultsModel.updateLengthHeader(-1);
            App.get().resultsTable.getTableHeader().repaint();
          }
        });
        volume = 0;
        IndexSearcher searcher = App.get().searcher;
        Set<String> fieldsToLoad = Collections.singleton(IndexItem.LENGTH);
        for (int doc : result.docs) {
          try {
            String len = searcher.doc(doc, fieldsToLoad).get(IndexItem.LENGTH);
            if (len != null && !len.isEmpty()) {
              volume += Long.valueOf(len);
            }

          } catch (IOException e) {
            // e.printStackTrace();
          }

          if (Thread.currentThread().isInterrupted()) {
            return;
          }
        }
        volume = volume / (1024 * 1024);

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            App.get().resultsModel.updateLengthHeader(volume);
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
      LOGGER.error("Pesquisa cancelada!");
      RowComparator.closeAtomicReader();
      App.get().reader.close();
      String index = App.get().codePath + "/../index";
      Directory directory = FSDirectory.open(new File(index));
      App.get().reader = IndexReader.open(directory);
      if (Configuration.searchThreads > 1) {
        App.get().searcher = new IndexSearcher(App.get().reader, App.get().searchExecutorService);
      } else {
        App.get().searcher = new IndexSearcher(App.get().reader);
      }
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

  public SearchResult pesquisarTodos() throws Exception {

    if (query == null) {
      query = getQueryWithFilter(queryText);
      App.get().setQuery(query);
    }

    if (!treeQuery) {
      query = getNonTreeQuery();
    }

    int maxResults = 1000000;
    SearchResult searchResult = new SearchResult(0);

    ScoreDoc[] scoreDocs = null;
    do {
      ScoreDoc lastScoreDoc = null;
      if (scoreDocs != null) {
        lastScoreDoc = scoreDocs[scoreDocs.length - 1];
      }

      scoreDocs = App.get().searcher.searchAfter(lastScoreDoc, query, maxResults).scoreDocs;
      searchResult = searchResult.addResults(scoreDocs);

    } while (scoreDocs.length == maxResults);

    return searchResult;
  }

  private Query getNonTreeQuery() {
    BooleanQuery result = new BooleanQuery();
    result.add(query, Occur.MUST);
    result.add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST_NOT);
    return result;
  }

  public SearchResult filtrarFragmentos(SearchResult prevResult) throws Exception {
    HashSet<Integer> duplicates = new HashSet<Integer>();
    int frags = 0;
    int[] ids = App.get().getIDs();
    Set<Integer> splitedIds = App.get().splitedDocs;
    for (int i = 0; i < prevResult.length; i++) {
      int id = ids[prevResult.docs[i]];
      if (splitedIds.contains(id)) {
        if (!duplicates.contains(id)) {
          duplicates.add(id);
        } else {
          prevResult.docs[i] = -1;
          frags++;
        }
      }
    }

    return clearResults(prevResult, frags);

  }

  public SearchResult filtrarVersoes(SearchResult prevResult) throws Exception {
    if (App.get().viewToRawMap.getMappings() == 0) {
      return prevResult;
    }

    TreeMap<Integer, Integer> addedMap = new TreeMap<Integer, Integer>();
    int versions = 0;
    App app = App.get();
    for (int i = 0; i < prevResult.length; i++) {
      int id = app.getIDs()[prevResult.docs[i]];
      Integer original = app.viewToRawMap.getRaw(id);
      if (original == null) {
        if (app.viewToRawMap.isRaw(id)) {
          if (!addedMap.containsKey(id)) {
            addedMap.put(id, i);
          } else {
            addedMap.remove(id);
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
        } else {
          addedMap.put(original, null);
        }

      }
    }

    // Arrays.sort(result, new ScoreDocComparator(-1));
    return clearResults(prevResult, versions);

  }

  private SearchResult clearResults(SearchResult prevResult, int blanks) {

    SearchResult result = new SearchResult(prevResult.length - blanks);
    int j = 0;
    for (int i = 0; i < prevResult.length; i++) {
      if (prevResult.docs[i] != -1) {
        result.docs[i - j] = prevResult.docs[i];
        result.scores[i - j] = prevResult.scores[i];
      } else {
        j++;
      }
    }

    return result;
  }

}
