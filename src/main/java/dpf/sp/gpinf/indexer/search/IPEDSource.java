/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃƒÂªncias Digitais (IPED).
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.IndexTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;
import gpinf.dev.data.EvidenceFile;

public class IPEDSource implements Closeable{
	
	public static final String INDEX_DIR = "index";
	public static final String MODULE_DIR = "indexador";
	public static final String SLEUTH_DB = "sleuth.db";
	
	private static AtomicInteger nextId = new AtomicInteger();
	
	private File casePath;
	private File moduleDir;
	private File index;
	
	SleuthkitCase sleuthCase;
	IndexReader reader;
	IndexSearcher searcher;
	Analyzer analyzer;
	
	private ExecutorService searchExecutorService;
	
	protected ArrayList<String> categories = new ArrayList<String>(); 
	
	private Marcadores marcadores;
	MultiMarcadores globalMarcadores;
	
	private int[] ids, docs, textSizes;
	
	private int sourceId = -1;
	
	int totalItens = 0;
	
	private int lastId = 0;
	
	private Set<Integer> splitedDocs = Collections.emptySet();
	VersionsMap viewToRawMap = new VersionsMap(0);
	
	LinkedHashSet<String> keywords = new LinkedHashSet<String>();
	
	HashSet<String> extraAttributes = new HashSet<String>();
	
	boolean isFTKReport = false;
	
	public IPEDSource(File casePath) {
		
		this.casePath = casePath;
		moduleDir = new File(casePath, MODULE_DIR);
		index = new File(moduleDir, INDEX_DIR);
		
		if(!index.exists())
			return;
		
		sourceId = nextId.getAndIncrement();
		
		try {
			Configuration.getConfiguration(moduleDir.getAbsolutePath());
			
			File sleuthFile = new File(casePath,  SLEUTH_DB);
			if (sleuthFile.exists()){
				sleuthCase = SleuthkitCase.openCase(sleuthFile.getAbsolutePath());
				checkImagePaths(casePath, sleuthFile);
			}
				
			openIndex(index);
			
			analyzer = AppAnalyzer.get();
			
            ids = (int[]) Util.readObject(new File(moduleDir, "data/ids.map").getAbsolutePath());
            for(int i = 0; i < ids.length; i++)
            	if(ids[i] > lastId)
            		lastId = ids[i];
            
            invertIdToLuceneIdArray();
            
            countTotalItems();
            
            File splitedDocsFile = new File(moduleDir, "data/splits.ids");
            if(splitedDocsFile.exists())
            	splitedDocs = (Set<Integer>) Util.readObject(splitedDocsFile.getAbsolutePath());
			
			File viewToRawFile = new File(moduleDir, "data/alternativeToOriginals.ids");
			if (viewToRawFile.exists())
				viewToRawMap = (VersionsMap) Util.readObject(viewToRawFile.getAbsolutePath());
			
			File textSizesFile = new File(moduleDir, "data/texts.size");
			if(textSizesFile.exists())
				textSizes = (int[]) Util.readObject(textSizesFile.getAbsolutePath());
			else
				textSizes = new int[lastId + 1];
			
			loadCategories();
			
			loadKeywords();
			
			IndexItem.loadMetadataTypes(new File(moduleDir, "conf"));
			
			File extraAttrFile = new File(moduleDir, "data/" + IndexTask.extraAttrFilename);
			if(extraAttrFile.exists()){
				extraAttributes = (HashSet<String>)Util.readObject(extraAttrFile.getAbsolutePath());
				EvidenceFile.getAllExtraAttributes().addAll(extraAttributes);
			}
			
			marcadores = new Marcadores(this, moduleDir);
			marcadores.loadState();
			globalMarcadores = new MultiMarcadores(Collections.singletonList(this));
			
			BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
			
			isFTKReport = new File(moduleDir, "data/containsFTKReport.flag").exists();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void invertIdToLuceneIdArray(){
		docs = new int[lastId + 1];
		for(int i = ids.length - 1; i >= 0 ; i--)
			docs[ids[i]] = i;
	}
	
	private void countTotalItems(){
		for(int i = 0; i < docs.length; i++)
			if(docs[i] > 0)
				totalItens++;
		//inclui docId = 0 na contagem
		totalItens++;
	}
	
	private void loadCategories(){
		try {
			categories = Util.loadKeywords(moduleDir + "/categorias.txt", "UTF-8");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadKeywords(){
		ArrayList<String> words;
		try {
			words = Util.loadKeywords(moduleDir.getAbsolutePath() + "/palavras-chave.txt", "UTF-8");
		} catch (IOException e) {
		   	words = new ArrayList<String>();
		}
		for(String word : words)
			keywords.add(word);
	}
	
	private void openIndex(File index) throws IOException{
		System.out.println("Openning index " + index.getAbsolutePath());
		
		Directory directory = FSDirectory.open(index);
		reader = DirectoryReader.open(directory);
		
		openSearcher();
	}
	
	protected void openSearcher(){
		
		if(Configuration.searchThreads > 1){
			searchExecutorService = Executors.newFixedThreadPool(Configuration.searchThreads);
			searcher = new IndexSearcher(reader, searchExecutorService);
		}else
			searcher = new IndexSearcher(reader);
		
		searcher.setSimilarity(new IndexerSimilarity());
	}
	
	@Override
	public void close() {
		try {
			reader.close();
			
			if(searchExecutorService != null)
				searchExecutorService.shutdown();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public EvidenceFile getItemByLuceneID(int docID){
		try {
			Document doc = searcher.doc(docID);
			EvidenceFile item = IndexItem.getItem(doc, moduleDir, sleuthCase, false);
			return item;
			
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public EvidenceFile getItemByID(int id){
		return getItemByLuceneID(docs[id]);
	}
	
	public void reopen() throws IOException{
		close();
		openIndex(index);
	}
	
	private void checkImagePaths(File casePath, File sleuthFile) throws Exception {
		  File tmpCase = null;
		  char letter = casePath.getAbsolutePath().charAt(0);
	      Map<Long, List<String>> imgPaths = sleuthCase.getImagePaths();
	      for (Long id : imgPaths.keySet()) {
	        List<String> paths = imgPaths.get(id);
	        ArrayList<String> newPaths = new ArrayList<String>();
	        for (String path : paths) {
	          if (new File(path).exists()) {
	            break;
	          } else {
	            String newPath = letter + path.substring(1);
	            if (new File(newPath).exists()) {
	              newPaths.add(newPath);
	            }
	          }
	        }
	        if (newPaths.size() > 0) {
	          if (tmpCase == null && !sleuthFile.canWrite()) {
	            tmpCase = File.createTempFile("sleuthkit-", ".db");
	            tmpCase.deleteOnExit();
	            sleuthCase.close();
	            IOUtil.copiaArquivo(sleuthFile, tmpCase);
	            sleuthCase = SleuthkitCase.openCase(tmpCase.getAbsolutePath());
	          }
	          sleuthCase.setImagePaths(id, newPaths);
	        }
	      }
	  }
	
	public int getSourceId(){
		return sourceId;
	}
	
	public File getIndex(){
		return index;
	}
	
	public File getModuleDir(){
		return moduleDir;
	}
	
	public File getCaseDir(){
		return casePath;
	}
	
	protected int getId(int luceneId){
		return ids[luceneId];
	}
	
	public int getLuceneId(int id){
		return docs[id];
	}

	public int getTextSize(int id) {
		return textSizes[id];
	}
	
	boolean isSplited(int id){
		return splitedDocs.contains(id);
	}
	
	public List<String> getCategories(){
		return categories;
	}
	
	public Set<String> getKeywords(){
		return keywords;
	}
	
	public HashSet<String> getExtraAttributes(){
		return this.extraAttributes;
	}
	
	public Analyzer getAnalyzer() {
	    return analyzer;
	}

	public SleuthkitCase getSleuthCase() {
		return sleuthCase;
	}

	public IndexReader getReader() {
		return reader;
	}

	public IndexSearcher getSearcher() {
		return searcher;
	}

	public Marcadores getMarcador() {
		return marcadores;
	}
	
	public MultiMarcadores getMarcadores() {
		return this.globalMarcadores;
	}

	public int getTotalItens() {
		return totalItens;
	}

	public int getLastId() {
		return lastId;
	}

	public Set<Integer> getSplitedDocs() {
		return splitedDocs;
	}

	public VersionsMap getViewToRawMap() {
		return viewToRawMap;
	}

	public boolean isFTKReport() {
		return isFTKReport;
	}
	
}
