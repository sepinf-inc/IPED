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
package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationDirectoryImpl;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import dpf.sp.gpinf.indexer.datasource.ItemProducer;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcherImpl;
import dpf.sp.gpinf.indexer.search.IPEDSourceImpl;
import dpf.sp.gpinf.indexer.search.IndexerSimilarity;
import dpf.sp.gpinf.indexer.util.ExeFileFilter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SleuthkitClient;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMapImpl;
import gpinf.dev.data.CaseDataImpl;
import gpinf.dev.data.ItemImpl;
import iped3.CaseData;
import iped3.configuration.ConfigurationDirectory;
import iped3.search.LuceneSearchResult;

/**
 * Classe responsável pela preparação do processamento, inicialização do contador, produtor e
 * consumidores (workers) dos itens, monitoramento do processamento e pelas etapas
 * pós-processamento.
 *
 * O contador apenas enumera e soma o tamanho dos itens que serão processados, permitindo que seja
 * estimado o progresso e término do processamento.
 *
 * O produtor obtém os itens a partir de uma fonte de dados específica (relatório do FTK, diretório,
 * imagem), inserindo-os numa fila de processamento com tamanho limitado (para limitar o uso de
 * memória).
 *
 * Os consumidores (workers) retiram os itens da fila e são responsáveis pelo seu processamento.
 * Cada worker executa em uma thread diferente, permitindo o processamento em paralelo dos itens.
 * Por padrão, o número de workers é igual ao número de processadores disponíveis.
 *
 * Após inicializar o processamento, o manager realiza o monitoramento, verificando se alguma
 * exceção ocorreu, informando a interface sobre o estado do processamento e verificando se os
 * workers processaram todos os itens.
 *
 * O pós-processamento inclui a pré-ordenação das propriedades dos itens, o armazenamento do volume
 * de texto indexado de cada item, do mapeamento indexId para id, dos ids dos itens fragmentados, a
 * filtragem de categorias e palavras-chave e o log de estatísticas do processamento.
 *
 */
public class Manager {

  private static int QUEUE_SIZE = 100000;
  private static Logger LOGGER = LoggerFactory.getLogger(Manager.class);
  private static Manager instance;

  private CaseData caseData;

  public CaseData getCaseData() {
    return caseData;
  }

  private List<File> sources;
  private File output, indexDir, indexTemp, palavrasChave;

  private ItemProducer contador, produtor;
  private Worker[] workers;
  private IndexWriter writer;

  public Statistics stats;
  public Exception exception;

  private boolean isSearchAppOpen = false;
  private boolean isProcessingFinished = false;

  public static Manager getInstance(){
      return instance;
  }

  public Manager(List<File> sources, File output, File palavras) {
	LocalConfig ipedConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class).iterator().next();
	
    this.indexTemp = ipedConfig.getIndexTemp();
    this.sources = sources;
    this.output = output;
    this.palavrasChave = palavras;

    this.caseData = new CaseDataImpl(QUEUE_SIZE);

    ItemImpl.setStartID(0);

    indexDir = new File(output, "index"); //$NON-NLS-1$
    if (indexTemp == null || IndexFiles.getInstance().appendIndex) {
      indexTemp = indexDir;
    }

    stats = Statistics.get(caseData, indexDir);

    instance = this;
  }
  
  public File getIndexTemp(){
	  return indexTemp;
  }

  Worker[] getWorkers() {
    return workers;
  }
  
  public IndexWriter getIndexWriter(){
	  return this.writer;
  }

  public void process() throws Exception {

    stats.printSystemInfo();

    output = output.getCanonicalFile();

    prepararReport();

    if (IndexFiles.getInstance().appendIndex) {
      loadExistingData();
    }

    int i = 1;
    for (File source : sources) {
      LOGGER.info("Evidence " + (i++) + ": '{}'", source.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    try {
      iniciarIndexacao();

      // apenas conta o número de arquivos a indexar
      contador = new ItemProducer(this, caseData, true, sources, output);
      contador.start();

      // produz lista de arquivos e propriedades a indexar
      produtor = new ItemProducer(this, caseData, false, sources, output);
      produtor.start();

      monitorarIndexacao();
      finalizarIndexacao();

    } catch (Exception e) {
      interromperIndexacao();
      throw e;
    }

    saveViewToOriginalFileMap();

    filtrarPalavrasChave();
    
    removeEmptyTreeNodes();
    
    new P2PBookmarker(caseData).createBookmarksForSharedFiles(output.getParentFile());
    
    updateImagePaths();
    
    deleteTempDir();

    stats.logarEstatisticas(this);

  }

  private void interromperIndexacao() throws Exception {
    if (workers != null) {
      for (int k = 0; k < workers.length; k++) {
        if (workers[k] != null) {
          workers[k].interrupt();
          // workers[k].join(5000);
        }
      }
    }
    ParsingReader.shutdownTasks();
    if (writer != null) {
      writer.rollback();
    }

    if (contador != null) {
      contador.interrupt();
      // contador.join(5000);
    }
    if (produtor != null) {
      produtor.interrupt();
      // produtor.join(5000);
    }
  }
  
  public void initSleuthkitServers(final String dbPath) throws InterruptedException{
      ArrayList<Thread> threads = new ArrayList<Thread>(); 
      for(final Worker worker : workers){
          Thread t = new Thread(){
              public void run(){
                  SleuthkitClient.get(worker.getThreadGroup(), dbPath);
              }
          };
          t.start();
          threads.add(t);
      }
      for(Thread t : threads)
          t.join();
  }

  private void loadExistingData() throws Exception {

    IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
    stats.previousIndexedFiles = reader.numDocs();
    reader.close();

    if (new File(output, "data/containsReport.flag").exists()) { //$NON-NLS-1$
      caseData.setContainsReport(true);
    }

  }
  
  private IndexWriterConfig getIndexWriterConfig(){
	    IndexWriterConfig conf = new IndexWriterConfig(Versao.current, AppAnalyzer.get());
	    conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
	    
	    LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class).iterator().next();
	    conf.setMaxThreadStates(localConfig.getNumThreads());
	    conf.setSimilarity(new IndexerSimilarity());
	    ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
	    if ((localConfig.isIndexTempOnSSD() && localConfig.getIndexTemp() != null) || localConfig.isOutputOnSSD()) {
	      mergeScheduler.setMaxMergesAndThreads(8, 4);
	    }
	    conf.setMergeScheduler(mergeScheduler);
	    conf.setRAMBufferSizeMB(64);
	    TieredMergePolicy tieredPolicy = new TieredMergePolicy();
	    /*
	     * Seta tamanho máximo dos subíndices. Padrão é 5GB.
	     * Poucos subíndices grandes impactam processamento devido a merges parciais demorados.
	     * Muitos subíndices pequenos aumentam tempo e memória necessários p/ pesquisas.
	     * Usa 4000MB devido a limite do ISO9660
	     */
	    tieredPolicy.setMaxMergedSegmentMB(4000);
	    conf.setMergePolicy(tieredPolicy);
	    
	    return conf;
  }

  private void iniciarIndexacao() throws Exception {
    IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CreatingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    LOGGER.info("Creating index..."); //$NON-NLS-1$

    writer = new IndexWriter(FSDirectory.open(indexTemp), getIndexWriterConfig());

    LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class).iterator().next();
    workers = new Worker[localConfig.getNumThreads()];
    for (int k = 0; k < workers.length; k++) {
      workers[k] = new Worker(k, caseData, writer, output, this);
    }

    //Execução dos workers após todos terem sido instanciados e terem inicializado suas tarefas
    for (int k = 0; k < workers.length; k++) {
      workers[k].start();
    }

    IndexFiles.getInstance().firePropertyChange("workers", 0, workers); //$NON-NLS-1$
  }

  private void monitorarIndexacao() throws Exception {

    boolean someWorkerAlive = true;

    while (someWorkerAlive) {
      if (IndexFiles.getInstance().isCancelled()) {
        exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
      }

      String currentDir = contador.currentDirectory();
      if (contador.isAlive() && currentDir != null && !currentDir.trim().isEmpty()) {
        IndexFiles.getInstance().firePropertyChange("mensagem", 0, Messages.getString("Manager.Adding") + currentDir.trim() + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      }
      IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences()); //$NON-NLS-1$
      IndexFiles.getInstance().firePropertyChange("processed", -1, stats.getProcessed()); //$NON-NLS-1$
      IndexFiles.getInstance().firePropertyChange("progresso", 0, (int) (stats.getVolume() / 1000000)); //$NON-NLS-1$

      someWorkerAlive = false;
      for (int k = 0; k < workers.length; k++) {
        if (workers[k].exception != null && exception == null) {
          exception = workers[k].exception;
        }
        /** TODO sincronizar teste, pois pode ocorrer condição de corrida e o teste não detectar um último item sendo processado
         *  não é demasiado grave pois será detectado o problema no log de estatísticas e o usuario sera informado do erro. */
        if (caseData.getItemQueue().size() > 0 || workers[k].evidence != null || produtor.isAlive()) //if(workers[k].isAlive())
          someWorkerAlive = true;
      }
      
      if(!someWorkerAlive){
      	if(caseData.changeToNextQueue() != null){
      		someWorkerAlive = true;
          	for (int k = 0; k < workers.length; k++)
          		workers[k].processNextQueue();
      	}
      }

      if (exception != null) {
        throw exception;
      }

    }

  }

  public int numItensBeingProcessed() {
    int num = 0;
    for (int k = 0; k < workers.length; k++) {
      num += workers[k].itensBeingProcessed;
    }
    return num;
  }

  private void finalizarIndexacao() throws Exception {

    for (int k = 0; k < workers.length; k++) {
      workers[k].finish();
    }

    AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance().findObjects(AdvancedIPEDConfig.class).iterator().next();
    if (advancedConfig.isForceMerge()) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.Optimizing")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      LOGGER.info("Optimizing Index..."); //$NON-NLS-1$
      try {
        writer.forceMerge(1);
      } catch (Throwable e) {
        LOGGER.error("Error while optimizing: {}", e); //$NON-NLS-1$
      }

    }

    IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.ClosingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    LOGGER.info("Closing Index..."); //$NON-NLS-1$
    writer.close();
    writer = null;

    if (!indexTemp.getCanonicalPath().equalsIgnoreCase(indexDir.getCanonicalPath())) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CopyingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      LOGGER.info("Copying Index..."); //$NON-NLS-1$
      try {
        Files.move(indexTemp.toPath(), indexDir.toPath());

      } catch (IOException e) {
        IOUtil.copiaDiretorio(indexTemp, indexDir);
      }
    }

    if (caseData.containsReport()) {
      new File(output, "data/containsReport.flag").createNewFile(); //$NON-NLS-1$
    }

    if (FTK3ReportReader.wasExecuted) {
      new File(output, "data/containsFTKReport.flag").createNewFile(); //$NON-NLS-1$
    }

  }
  
  private void updateImagePaths(){
	  CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
	  if(args.isPortable()){ //$NON-NLS-1$
		  IPEDSourceImpl ipedCase = new IPEDSourceImpl(output.getParentFile());
		  ipedCase.updateImagePathsToRelative();
		  ipedCase.close();
	  }
  }
  
  public void deleteTempDir(){
	  LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class).iterator().next();
	  LOGGER.info("Deleting temp folder {}", localConfig.getIndexerTemp()); //$NON-NLS-1$
	  IOUtil.deletarDiretorio(localConfig.getIndexerTemp());
  }

  private void filtrarPalavrasChave() {

    try {
      LOGGER.info("Filtering keywords..."); //$NON-NLS-1$
      IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.FilteringKeywords")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      ArrayList<String> palavras = Util.loadKeywords(output.getAbsolutePath() + "/palavras-chave.txt", Charset.defaultCharset().name()); //$NON-NLS-1$

      if (palavras.size() != 0) {
    	IPEDSourceImpl ipedCase = new IPEDSourceImpl(output.getParentFile());
        ArrayList<String> palavrasFinais = new ArrayList<String>();
        for (String palavra : palavras) {
          if (Thread.interrupted()) {
        	ipedCase.close();
            throw new InterruptedException("Processing canceled!"); //$NON-NLS-1$
          }

          IPEDSearcherImpl pesquisa = new IPEDSearcherImpl(ipedCase, palavra);
          if (pesquisa.searchAll().getLength() > 0) {
            palavrasFinais.add(palavra);
          }
        }
        ipedCase.close();
        
        Util.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/palavras-chave.txt", "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
        int filtradas = palavras.size() - palavrasFinais.size();
        LOGGER.info("Filtered {} keywords.", filtradas); //$NON-NLS-1$
      } else {
        LOGGER.info("No keywords to filter out."); //$NON-NLS-1$
      }

    } catch (Exception e) {
      LOGGER.error("Error filtering keywords", e); //$NON-NLS-1$
    }

  }

  private void saveViewToOriginalFileMap() throws Exception {

    VersionsMapImpl viewToRaw = new VersionsMapImpl(0);

    if (FTK3ReportReader.wasExecuted) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CreatingViewMap")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      LOGGER.info("Creating preview to original file map..."); //$NON-NLS-1$

      IPEDSourceImpl ipedCase = new IPEDSourceImpl(output.getParentFile());
      String query = IndexItem.EXPORT + ":(files && (\"AD html\" \"AD rtf\"))"; //$NON-NLS-1$
      IPEDSearcherImpl pesquisa = new IPEDSearcherImpl(ipedCase, query);
      LuceneSearchResult alternatives = pesquisa.filtrarFragmentos(pesquisa.searchAll());

      HashMap<String, Integer> viewMap = new HashMap<String, Integer>();
      for (int i = 0; i < alternatives.getLength(); i++) {
        if (Thread.interrupted()) {
          ipedCase.close();
          throw new InterruptedException("Processing Canceled!"); //$NON-NLS-1$
        }
        Document doc = ipedCase.getSearcher().doc(alternatives.getLuceneIds()[i]);
        String ftkId = doc.get(IndexItem.FTKID);
        int id = Integer.valueOf(doc.get(IndexItem.ID));
        viewMap.put(ftkId, id);
      }
      alternatives = null;
      ipedCase.close();

      IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(output, "index"))); //$NON-NLS-1$
      Bits liveDocs = MultiFields.getLiveDocs(reader);
      viewToRaw = new VersionsMapImpl(stats.getLastId() + 1);

      for (int i = 0; i < reader.maxDoc(); i++) {
        if (liveDocs != null && !liveDocs.get(i)) {
          continue;
        }

        Document doc = reader.document(i);
        String ftkId = doc.get(IndexItem.FTKID);
        int id = Integer.valueOf(doc.get(IndexItem.ID));
        //String export = doc.get(IndexItem.EXPORT);

        Integer viewId = viewMap.get(ftkId);
        if (viewId != null && viewId != id /*&& !viewToRaw.isView(viewId) && !export.contains(".[AD].")*/) {
          viewToRaw.put(viewId, id);
        }

      }
      reader.close();

      LOGGER.info("Created {} preview mappings.", viewToRaw.getMappings()); //$NON-NLS-1$
    }

    FileOutputStream fileOut = new FileOutputStream(new File(output, "data/alternativeToOriginals.ids")); //$NON-NLS-1$
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(viewToRaw);
    out.close();
    fileOut.close();
  }
  
  private void removeEmptyTreeNodes() {

	    if (!caseData.containsReport() || caseData.isIpedReport()) {
	      return;
	    }

	    IndexFiles.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.DeletingTreeNodes")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	    LOGGER.info("Deleting empty tree nodes"); //$NON-NLS-1$

	    try {
	      IPEDSourceImpl ipedCase = new IPEDSourceImpl(output.getParentFile());
	      IPEDSearcherImpl searchAll = new IPEDSearcherImpl(ipedCase, new MatchAllDocsQuery());
	      LuceneSearchResult result = searchAll.searchAll();

	      boolean[] doNotDelete = new boolean[stats.getLastId() + 1];
	      for (int docID : result.getLuceneIds()) {
	        String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
	        if(!parentIds.trim().isEmpty()) {
	          for (String parentId : parentIds.trim().split(" ")) { //$NON-NLS-1$
	            doNotDelete[Integer.parseInt(parentId)] = true;            
	          }
	        }
	      }
	      
	      writer = new IndexWriter(FSDirectory.open(indexDir), getIndexWriterConfig());

	      BooleanQuery query;
	      int startId = 0, interval = 1000, endId = interval;
	      while (startId <= stats.getLastId()) {
	        if (endId > stats.getLastId()) {
	          endId = stats.getLastId();
	        }
	        query = new BooleanQuery();
	        query.add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST); //$NON-NLS-1$
	        query.add(NumericRangeQuery.newIntRange(IndexItem.ID, startId, endId, true, true), Occur.MUST);
	        for (int i = startId; i <= endId; i++) {
	          if (doNotDelete[i]) {
	            query.add(NumericRangeQuery.newIntRange(IndexItem.ID, i, i, true, true), Occur.MUST_NOT);
	          }
	        }
	        writer.deleteDocuments(query);
	        startId = endId + 1;
	        endId += interval;
	      }

	    } catch (Exception e) {
	      LOGGER.warn("Error deleting empty tree nodes", e); //$NON-NLS-1$
	      
	    }finally{
	    	IOUtil.closeQuietly(writer);
	    }    

  }

  private void prepararReport() throws Exception {
    if (output.exists() && !IndexFiles.getInstance().appendIndex) {
      throw new IOException("Directory already exists: " + output.getAbsolutePath()); //$NON-NLS-1$
    }

    File export = new File(output.getParentFile(), ExportFileTask.EXTRACT_DIR);
    if (export.exists() && !IndexFiles.getInstance().appendIndex) {
      throw new IOException("Directory already exists: " + export.getAbsolutePath()); //$NON-NLS-1$
    }

    if (!output.exists() && !output.mkdirs()) {
      throw new IOException("Fail to create folder " + output.getAbsolutePath()); //$NON-NLS-1$
    }

    if (!IndexFiles.getInstance().appendIndex) {
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "lib"), new File(output, "lib"), true); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "jre"), new File(output, "jre"), true); //$NON-NLS-1$ //$NON-NLS-2$

      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/imagemagick"), new File(output, "tools/imagemagick")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/esedbexport"), new File(output, "tools/esedbexport")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/pffexport"), new File(output, "tools/pffexport")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/msiecfexport"), new File(output, "tools/msiecfexport")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/tesseract"), new File(output, "tools/tesseract")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools/tsk"), new File(output, "tools/tsk")); //$NON-NLS-1$ //$NON-NLS-2$
      
      AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance().findObjects(AdvancedIPEDConfig.class).iterator().next();
      if (advancedConfig.isEmbutirLibreOffice()) {
        IOUtil.copiaArquivo(new File(Configuration.getInstance().appRoot, "tools/libreoffice.zip"), new File(output, "tools/libreoffice.zip")); //$NON-NLS-1$ //$NON-NLS-2$
      }

      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "htm"), new File(output, "htm")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "htmlreport"), new File(output, "htmlreport")); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaDiretorio(new File(Configuration.getInstance().configPath, "conf"), new File(output, "conf"), true); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaArquivo(new File(Configuration.getInstance().configPath, Configuration.CONFIG_FILE), new File(output, Configuration.CONFIG_FILE));
      IOUtil.copiaArquivo(new File(Configuration.getInstance().appRoot, Configuration.LOCAL_CONFIG), new File(output, Configuration.LOCAL_CONFIG));
      File binDir = new File(Configuration.getInstance().appRoot, "bin"); //$NON-NLS-1$
      if(binDir.exists())
          IOUtil.copiaDiretorio(binDir, output.getParentFile()); //$NON-NLS-1$
      else {
          for(File f : new File(Configuration.getInstance().appRoot).getParentFile().listFiles(new ExeFileFilter()))
              IOUtil.copiaArquivo(f, new File(output.getParentFile(), f.getName()));
      }
      //copia arquivo de assinaturas customizadas
      IOUtil.copiaArquivo(new File(Configuration.getInstance().appRoot, "conf/" + Configuration.CUSTOM_MIMES_CONFIG), new File(output, "conf/" + Configuration.CUSTOM_MIMES_CONFIG)); //$NON-NLS-1$ //$NON-NLS-2$
      IOUtil.copiaArquivo(new File(Configuration.getInstance().appRoot, "conf/ResultSetViewersConf.xml"), new File(output, "conf/ResultSetViewersConf.xml")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    if (palavrasChave != null) {
      IOUtil.copiaArquivo(palavrasChave, new File(output, "palavras-chave.txt")); //$NON-NLS-1$
    }

    File dataDir = new File(output, "data"); //$NON-NLS-1$
    if (!dataDir.exists()) {
      if (!dataDir.mkdir()) {
        throw new IOException("Fail to create folder " + dataDir.getAbsolutePath()); //$NON-NLS-1$
      }
    }

  }

	public boolean isSearchAppOpen() {
		return isSearchAppOpen;
	}
	
	public void setSearchAppOpen(boolean isSearchAppOpen) {
		this.isSearchAppOpen = isSearchAppOpen;
	}
	
	public boolean isProcessingFinished() {
		return isProcessingFinished;
	}
	
	public void setProcessingFinished(boolean isProcessingFinished) {
		this.isProcessingFinished = isProcessingFinished;
	}

}
