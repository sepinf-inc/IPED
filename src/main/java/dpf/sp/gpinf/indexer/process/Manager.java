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

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import dpf.sp.gpinf.indexer.datasource.ItemProducer;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.IndexerSimilarity;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;

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

  public Manager(List<File> sources, File output, File palavras) {
    this.indexTemp = Configuration.indexTemp;
    this.sources = sources;
    this.output = output;
    this.palavrasChave = palavras;

    this.caseData = new CaseData(QUEUE_SIZE);

    EvidenceFile.setStartID(0);

    indexDir = new File(output, "index");
    if (indexTemp == null || IndexFiles.getInstance().appendIndex) {
      indexTemp = indexDir;
    }

    stats = Statistics.get(caseData, indexDir);

  }
  
  public File getIndexTemp(){
	  return indexTemp;
  }

  Worker[] getWorkers() {
    return workers;
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
      LOGGER.info("Evidência " + (i++) + ": '{}'", source.getAbsolutePath());
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

    salvarDocIdToIdMap();

    saveViewToOriginalFileMap();

    filtrarPalavrasChave();

    configurarCategorias();

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

  private void loadExistingData() throws Exception {

    IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
    stats.previousIndexedFiles = reader.numDocs();
    reader.close();

    if (new File(output, "data/containsReport.flag").exists()) {
      caseData.setContainsReport(true);
    }

  }

  private void iniciarIndexacao() throws Exception {
    IndexFiles.getInstance().firePropertyChange("mensagem", "", "Configurando índice...");
    LOGGER.info("Configurando índice...");

    IndexWriterConfig conf = new IndexWriterConfig(Versao.current, AppAnalyzer.get());
    conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    conf.setMaxThreadStates(Configuration.numThreads);
    conf.setSimilarity(new IndexerSimilarity());
    ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
    if ((Configuration.indexTempOnSSD && !IndexFiles.getInstance().appendIndex) ||
    		(Configuration.outputOnSSD && IndexFiles.getInstance().appendIndex)) {
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

    writer = new IndexWriter(FSDirectory.open(indexTemp), conf);

    workers = new Worker[Configuration.numThreads];
    for (int k = 0; k < workers.length; k++) {
      workers[k] = new Worker(k, caseData, writer, output, this);
    }

    //Execução dos workers após todos terem sido instanciados e terem inicializado suas tarefas
    for (int k = 0; k < workers.length; k++) {
      workers[k].start();
    }

    IndexFiles.getInstance().firePropertyChange("workers", 0, workers);
  }

  private void monitorarIndexacao() throws Exception {

    boolean someWorkerAlive = true;

    while (someWorkerAlive) {
      if (IndexFiles.getInstance().isCancelled()) {
        exception = new InterruptedException("Indexação cancelada!");
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        exception = new InterruptedException("Indexação cancelada!");
      }

      String currentDir = contador.currentDirectory();
      if (contador.isAlive() && currentDir != null && !currentDir.trim().isEmpty()) {
        IndexFiles.getInstance().firePropertyChange("mensagem", 0, "Adicionando \"" + currentDir.trim() + "\"");
      }
      IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences());
      IndexFiles.getInstance().firePropertyChange("processed", -1, stats.getProcessed());
      IndexFiles.getInstance().firePropertyChange("progresso", 0, (int) (stats.getVolume() / 1000000));

      someWorkerAlive = false;
      for (int k = 0; k < workers.length; k++) {
        if (workers[k].exception != null && exception == null) {
          exception = workers[k].exception;
        }

        /*
         *  TODO sincronizar teste, pois pode ocorrer condição de corrida e o teste não detectar um último item sendo processado
         *  não é demasiado grave pois será detectado o problema no log de estatísticas e o usuario sera informado do erro. 
         */
        if (caseData.getEvidenceFiles().size() > 0 || workers[k].evidence != null || produtor.isAlive()) //if(workers[k].isAlive())
        {
          someWorkerAlive = true;
        }

        // TODO verificar se algum worker morreu e reinicia-lo? (Nao deve ocorrer...)
      }

      if (exception != null) {
        throw exception;
      }

    }

    ParsingReader.shutdownTasks();

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

    if (Configuration.forceMerge) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Otimizando Índice...");
      LOGGER.info("Otimizando Índice...");
      try {
        writer.forceMerge(1);
      } catch (Throwable e) {
        LOGGER.error("Erro durante otimização: {}", e);
      }

    }

    IndexFiles.getInstance().firePropertyChange("mensagem", "", "Fechando Índice...");
    LOGGER.info("Fechando Índice...");
    writer.close();

    if (!indexTemp.getCanonicalPath().equalsIgnoreCase(indexDir.getCanonicalPath())) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Copiando Índice...");
      LOGGER.info("Copiando Índice...");
      try {
        Files.move(indexTemp.toPath(), indexDir.toPath());

      } catch (IOException e) {
        IOUtil.copiaDiretorio(indexTemp, indexDir);
      }
    }
    
    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
    if(args.getCmdArgs().containsKey("--portable"))
    	SleuthkitReader.updateImagePaths();

    try {
      IOUtil.deletarDiretorio(Configuration.indexerTemp);
    } catch (IOException e) {
      LOGGER.warn("Não foi possível apagar {}", Configuration.indexerTemp.getPath());
    }

    if (caseData.containsReport()) {
      new File(output, "data/containsReport.flag").createNewFile();
    }

    if (FTK3ReportReader.wasExecuted) {
      new File(output, "data/containsFTKReport.flag").createNewFile();
    }

  }

  private void configurarCategorias() throws Exception {
    LOGGER.info("Configurando categorias...");
    TreeSet<String> categories = Util.loadKeywordSet(output.getAbsolutePath() + "/categorias.txt", "UTF-8");

    if (caseData.getBookmarks().size() > 0) {
      for (FileGroup bookmark : caseData.getBookmarks()) {
        categories.add(bookmark.getName().replaceAll("\"", "\\\""));
      }
    }
    categories.addAll(SetCategoryTask.getCategories());
    
    CmdLineArgs args = (CmdLineArgs)caseData.getCaseObject(CmdLineArgs.class.getName());
    // filtra categorias vazias
    if (categories.size() != 0 && !args.getCmdArgs().containsKey("--nogui")) {
      IPEDSource ipedCase = new IPEDSource(output.getParentFile());
      ArrayList<String> palavrasFinais = new ArrayList<String>();
      for (String categoria : categories) {
        if (Thread.interrupted()) {
          ipedCase.close();
          throw new InterruptedException("Indexação cancelada!");
        }

        String query = "categoria:\"" + categoria.replace("\"", "\\\"") + "\"";
        IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, query);
		SearchResult result = pesquisa.pesquisarTodos();

        if (result.getLength() > 0) {
          palavrasFinais.add(categoria);
        }

      }
      ipedCase.close();

      Util.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/categorias.txt", "UTF-8");
      int filtradas = categories.size() - palavrasFinais.size();
      LOGGER.info("Filtradas {} categorias", filtradas);
    } else {
      //LOGGER.info("Nenhuma categoria detectada.");
    	ArrayList<String> categoryList = new ArrayList<String>();
    	categoryList.addAll(Arrays.asList(categories.toArray(new String[0])));
    	Util.saveKeywords(categoryList, output.getAbsolutePath() + "/categorias.txt", "UTF-8");
    }

  }

  private void filtrarPalavrasChave() {

    try {
      LOGGER.info("Filtrando palavras-chave...");
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Filtrando palavras-chave...");
      ArrayList<String> palavras = Util.loadKeywords(output.getAbsolutePath() + "/palavras-chave.txt", Charset.defaultCharset().name());

      if (palavras.size() != 0) {
    	IPEDSource ipedCase = new IPEDSource(output.getParentFile());
        ArrayList<String> palavrasFinais = new ArrayList<String>();
        for (String palavra : palavras) {
          if (Thread.interrupted()) {
        	ipedCase.close();
            throw new InterruptedException("Indexação cancelada!");
          }

          IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, palavra);
          if (pesquisa.pesquisarTodos().getLength() > 0) {
            palavrasFinais.add(palavra);
          }
        }
        ipedCase.close();
        
        Util.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/palavras-chave.txt", "UTF-8");
        int filtradas = palavras.size() - palavrasFinais.size();
        LOGGER.info("Filtradas {} palavras-chave.", filtradas);
      } else {
        LOGGER.info("Nenhuma palavra-chave pré-configurada para filtrar.");
      }

    } catch (Exception e) {
      LOGGER.error("Erro ao filtrar palavras-chave", e);
    }

  }

  private void saveViewToOriginalFileMap() throws Exception {

    VersionsMap viewToRaw = new VersionsMap(0);

    if (FTK3ReportReader.wasExecuted) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Obtendo mapeamento de versções de visualização para originais...");
      LOGGER.info("Obtendo mapa versões de visualização -> originais...");

      IPEDSource ipedCase = new IPEDSource(output.getParentFile());
      String query = IndexItem.EXPORT + ":(files && (\"AD html\" \"AD rtf\"))";
      IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, query);
      SearchResult alternatives = pesquisa.filtrarFragmentos(pesquisa.pesquisarTodos());

      HashMap<String, Integer> viewMap = new HashMap<String, Integer>();
      for (int i = 0; i < alternatives.getLength(); i++) {
        if (Thread.interrupted()) {
          ipedCase.close();
          throw new InterruptedException("Indexação cancelada!");
        }
        Document doc = ipedCase.getSearcher().doc(alternatives.getLuceneIds()[i]);
        String ftkId = doc.get(IndexItem.FTKID);
        int id = Integer.valueOf(doc.get(IndexItem.ID));
        viewMap.put(ftkId, id);
      }
      alternatives = null;
      ipedCase.close();

      IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(output, "index")));
      Bits liveDocs = MultiFields.getLiveDocs(reader);
      viewToRaw = new VersionsMap(stats.getLastId() + 1);

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

      LOGGER.info("Obtidos {} mapeamentos de versões de visualização para originais.", viewToRaw.getMappings());
    }

    FileOutputStream fileOut = new FileOutputStream(new File(output, "data/alternativeToOriginals.ids"));
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(viewToRaw);
    out.close();
    fileOut.close();
  }

  private void salvarDocIdToIdMap() throws IOException {

    IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando mapeamento indexId->id");
    LOGGER.info("Salvando mapeamento indexId->id");

    IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
    int[] ids = new int[reader.maxDoc()];
    Bits liveDocs = MultiFields.getLiveDocs(reader);
    for (int i = 0; i < reader.maxDoc(); i++) {
    	//ignore deleted docs
    	if(liveDocs != null && !liveDocs.get(i))
    		continue;
      Document doc = reader.document(i);
      ids[i] = Integer.parseInt(doc.get(IndexItem.ID));
    }

    reader.close();
    Util.writeObject(ids, output.getAbsolutePath() + "/data/ids.map");
  }

  private void prepararReport() throws Exception {
    if (output.exists() && !IndexFiles.getInstance().appendIndex) {
      throw new IOException("Diretório já existente: " + output.getAbsolutePath());
    }

    File export = new File(output.getParentFile(), ExportFileTask.EXTRACT_DIR);
    if (export.exists() && !IndexFiles.getInstance().appendIndex) {
      throw new IOException("Diretório já existente: " + export.getAbsolutePath());
    }

    if (!output.exists() && !output.mkdirs()) {
      throw new IOException("Não foi possível criar diretório " + output.getAbsolutePath());
    }

    if (!IndexFiles.getInstance().appendIndex) {
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib"), new File(output, "lib"), true);
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/nativeview"), new File(output, "lib/nativeview"));

      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/graphicsmagick"), new File(output, "tools/graphicsmagick"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/esedbexport"), new File(output, "tools/esedbexport"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/pffexport"), new File(output, "tools/pffexport"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/regripper"), new File(output, "tools/regripper"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/msiecfexport"), new File(output, "tools/msiecfexport"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "tools/tsk"), new File(output, "tools/tsk"));
      if (Configuration.embutirLibreOffice) {
        IOUtil.copiaArquivo(new File(Configuration.configPath, "tools/libreoffice.zip"), new File(output, "tools/libreoffice.zip"));
      }

      IOUtil.copiaDiretorio(new File(Configuration.configPath, "htm"), new File(output, "htm"));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "conf"), new File(output, "conf"), true);
      IOUtil.copiaArquivo(new File(Configuration.configPath, Configuration.CONFIG_FILE), new File(output, Configuration.CONFIG_FILE));
      IOUtil.copiaDiretorio(new File(Configuration.configPath, "bin"), output.getParentFile());
    }

    if (palavrasChave != null) {
      IOUtil.copiaArquivo(palavrasChave, new File(output, "palavras-chave.txt"));
    }

    File dataDir = new File(output, "data");
    if (!dataDir.exists()) {
      if (!dataDir.mkdir()) {
        throw new IOException("Não foi possível criar diretório " + dataDir.getAbsolutePath());
      }
    }

  }

}
