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
package iped.engine.core;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.AnalysisConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.config.LocalConfig;
import iped.engine.config.SplashScreenConfig;
import iped.engine.data.Bookmarks;
import iped.engine.data.CaseData;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.datasource.ItemProducer;
import iped.engine.datasource.SleuthkitReader;
import iped.engine.graph.GraphFileWriter;
import iped.engine.graph.GraphService;
import iped.engine.graph.GraphServiceFactoryImpl;
import iped.engine.graph.GraphTask;
import iped.engine.io.ParsingReader;
import iped.engine.localization.Messages;
import iped.engine.lucene.ConfiguredFSDirectory;
import iped.engine.lucene.CustomIndexDeletionPolicy;
import iped.engine.lucene.analysis.AppAnalyzer;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.IndexerSimilarity;
import iped.engine.search.ItemSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.sleuthkit.SleuthkitClient;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;
import iped.engine.task.ExportCSVTask;
import iped.engine.task.ExportFileTask;
import iped.engine.task.P2PBookmarker;
import iped.engine.task.index.ElasticSearchIndexTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.index.IndexTask;
import iped.engine.util.UIPropertyListenerProvider;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;
import iped.search.SearchResult;
import iped.utils.IOUtil;

/**
 * Classe responsável pela preparação do processamento, inicialização do
 * contador, produtor e consumidores (workers) dos itens, monitoramento do
 * processamento e pelas etapas pós-processamento.
 *
 * O contador apenas enumera e soma o tamanho dos itens que serão processados,
 * permitindo que seja estimado o progresso e término do processamento.
 *
 * O produtor obtém os itens a partir de uma fonte de dados específica
 * (relatório do UFED, diretório, imagem), inserindo-os numa fila de
 * processamento com tamanho limitado (para limitar o uso de memória).
 *
 * Os consumidores (workers) retiram os itens da fila e são responsáveis pelo
 * seu processamento. Cada worker executa em uma thread diferente, permitindo o
 * processamento em paralelo dos itens. Por padrão, o número de workers é igual
 * ao número de processadores disponíveis.
 *
 * Após inicializar o processamento, o manager realiza o monitoramento,
 * verificando se alguma exceção ocorreu, informando a interface sobre o estado
 * do processamento e verificando se os workers processaram todos os itens.
 *
 * O pós-processamento inclui a pré-ordenação das propriedades dos itens, o
 * armazenamento do volume de texto indexado de cada item, do mapeamento indexId
 * para id, dos ids dos itens fragmentados, a filtragem de categorias e
 * palavras-chave e o log de estatísticas do processamento.
 *
 */
public class Manager {

    private static long commitIntervalMillis = 30 * 60 * 1000;
    private static Logger LOGGER = LogManager.getLogger(Manager.class);
    private static Manager instance;

    private CaseData caseData;
    private ProcessingQueues processingQueues;

    private List<File> sources;
    private File output, finalIndexDir, indexDir, palavrasChave;

    private ItemProducer counter, producer;
    private Worker[] workers;
    private IndexWriter writer;

    public Statistics stats;
    public volatile Exception exception;

    private boolean isSearchAppOpen = false;
    private boolean isProcessingFinished = false;

    private LocalConfig localConfig;
    private AnalysisConfig analysisConfig;
    private IndexTaskConfig indexConfig;
    private CmdLineArgs args;

    private Thread commitThread = null;
    AtomicLong partialCommitsTime = new AtomicLong();

    private static final String appWinExeFileName = "IPED-SearchApp.exe";

    static {

        // installs the AmazonCorrettoCryptoProvider if it is available
        try {
            Class<?> clazz = Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");
            Method method = clazz.getMethod("install");
            method.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("AmazonCorrettoCryptoProvider not installed", e);
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static Manager getInstance() {
        return instance;
    }

    public ICaseData getCaseData() {
        return caseData;
    }

    public void addItemToQueue(IItem item) throws InterruptedException {
        this.processingQueues.addItem(item);
    }

    public ProcessingQueues getProcessingQueues() {
        return processingQueues;
    }

    public Manager(List<File> sources, File output, File palavras) {

        this.localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        this.analysisConfig = ConfigurationManager.get().findObject(AnalysisConfig.class);
        this.indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);

        this.indexDir = localConfig.getIndexTemp();
        this.sources = sources;
        this.output = output;
        this.palavrasChave = palavras;

        this.caseData = new CaseData();
        this.processingQueues = new ProcessingQueues(caseData);

        for (File source : sources) {
            if (source.getName().toLowerCase().endsWith(Bookmarks.EXT)) {
                this.caseData.setIpedReport(true);
                break;
            }
        }

        Item.setStartID(0);

        finalIndexDir = new File(output, "index"); //$NON-NLS-1$

        if (indexDir == null) {
            indexDir = finalIndexDir;
        }

        stats = Statistics.get(caseData, finalIndexDir);

        instance = this;

        commitIntervalMillis = indexConfig.getCommitIntervalSeconds() * 1000;
    }

    public File getIndexTemp() {
        return indexDir;
    }

    public Worker[] getWorkers() {
        return workers;
    }

    public int getNumWorkers() {
        return workers.length;
    }

    public IndexWriter getIndexWriter() {
        return this.writer;
    }

    public void process() throws Exception {

        stats.printSystemInfo();

        output = output.getCanonicalFile();

        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());

        prepareOutputFolder();

        if ((args.isContinue() || args.isRestart())) {
            if (finalIndexDir.exists()) {
                indexDir = finalIndexDir;
            } else if (indexDir != finalIndexDir) {
                changeTempDir();
            }
        }

        if (args.getEvidenceToRemove() != null) {
            indexDir = finalIndexDir;
        }

        saveCurrentTempDir();

        int i = 1;
        for (File source : sources) {
            LOGGER.info("Evidence " + (i++) + ": '{}'", source.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        PreviewRepositoryManager.configureWritable(output);

        EvidenceStatus status = new EvidenceStatus(output.getParentFile());

        try {
            openIndex();

            if (args.getEvidenceToRemove() != null) {
                removeEvidence(args.getEvidenceToRemove(), status);
                return;
            }

            initWorkers();

            status.addProcessingEvidences(args);
            status.save();

            // apenas conta o número de arquivos a indexar
            counter = new ItemProducer(this, caseData, true, sources, output);
            counter.start();

            // produz lista de arquivos e propriedades a indexar
            producer = new ItemProducer(this, caseData, false, sources, output);
            producer.start();

            monitorProcessing();

            finishProcessing();

        } catch (Exception e) {
            e.printStackTrace();
            interruptProcessing();
            throw e;

        } finally {
            closeItemProducers();
            PreviewRepositoryManager.close(output);
        }

        filterKeywords();

        removeEmptyTreeNodes();

        ExportFileTask.deleteIgnoredItemData(caseData, output);

        new P2PBookmarker(caseData).createBookmarksForSharedFiles(output.getParentFile());

        updateImagePaths();

        shutDownSleuthkitServers();

        deleteTempDir();

        stats.logStatistics(this);

        status.addSuccessfulEvidences(args);
        status.save();

    }

    private void closeItemProducers() {
        if (counter != null) {
            try {
                counter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (producer != null) {
            try {
                producer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void interruptProcessing() {
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
            try {
                writer.rollback();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (counter != null) {
            counter.interrupt();
            // contador.join(5000);
        }
        if (producer != null) {
            producer.interrupt();
            // produtor.join(5000);
        }
    }

    private void shutDownSleuthkitServers() {
        LOGGER.info("Closing Sleuthkit Servers."); //$NON-NLS-1$
        SleuthkitClient.shutDownServers();
    }

    private void saveCurrentTempDir() throws UnsupportedEncodingException, IOException {
        File temp = localConfig.getIndexerTemp();
        File prevTempInfoFile = IPEDSource.getTempDirInfoFile(output);
        prevTempInfoFile.getParentFile().mkdirs();
        Files.write(prevTempInfoFile.toPath(), temp.getAbsolutePath().getBytes("UTF-8"));
    }

    private void changeTempDir() throws UnsupportedEncodingException, IOException {
        File prevIndexTemp = IPEDSource.getTempIndexDir(output);
        if (!prevIndexTemp.exists()) {
            return;
        }
        localConfig.setIndexerTemp(prevIndexTemp.getParentFile());
        indexDir = localConfig.getIndexTemp();
    }

    private void loadExistingData() throws IOException {

        try (IndexReader reader = DirectoryReader.open(writer, true, true)) {
            stats.previousIndexedFiles = reader.numDocs();
        }

        if (new File(output, "data/containsReport.flag").exists()) { //$NON-NLS-1$
            caseData.setContainsReport(true);
        }

    }

    private IndexWriterConfig getIndexWriterConfig() {
        IndexWriterConfig conf = new IndexWriterConfig(AppAnalyzer.get());
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        conf.setCommitOnClose(true);
        conf.setSimilarity(new IndexerSimilarity());
        ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
        mergeScheduler.disableAutoIOThrottle();
        if ((localConfig.isIndexTempOnSSD() && indexDir != finalIndexDir) || localConfig.isOutputOnSSD()) {
            mergeScheduler.setMaxMergesAndThreads(8, 4);
        }
        conf.setMergeScheduler(mergeScheduler);
        conf.setRAMBufferSizeMB(64);
        TieredMergePolicy tieredPolicy = new TieredMergePolicy();
        /*
         * Seta tamanho máximo dos subíndices. Padrão é 5GB. Poucos subíndices grandes
         * impactam processamento devido a merges parciais demorados. Muitos subíndices
         * pequenos aumentam tempo e memória necessários p/ pesquisas. Usa 4000MB devido
         * a limite do ISO9660
         */
        tieredPolicy.setMaxMergedSegmentMB(4000);
        conf.setMergePolicy(tieredPolicy);

        conf.setIndexDeletionPolicy(new CustomIndexDeletionPolicy(args));

        return conf;
    }

    private void removeEvidence(String evidenceName, EvidenceStatus status) throws Exception {
        Level CONSOLE = Level.getLevel("MSG"); //$NON-NLS-1$
        LOGGER.log(CONSOLE, "Removing evidence '{}' from case...", evidenceName);

        // query evidenceUUID and tskID
        String evidenceUUID;
        Integer tskID = null;
        try (IPEDSource ipedCase = new IPEDSource(output.getParentFile(), writer)) {
            String query = BasicProps.NAME + ":\"" + evidenceName + "\" AND " + BasicProps.ISROOT + ":true";
            IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
            SearchResult result = searcher.search();
            if (result.getLength() == 0) {
                if (status.removeEvidence(evidenceName)) {
                    status.save();
                    return;
                }
                throw new IPEDException("Evidence name '" + evidenceName + "' not found!");
            }
            Item item = (Item) ipedCase.getItemByID(result.getId(0));
            evidenceUUID = item.getDataSource().getUUID();
            if (item.getInputStreamFactory() instanceof SleuthkitInputStreamFactory) {
                tskID = Integer.valueOf(item.getIdInDataSource());
            }
        }

        // remove from items from index
        LOGGER.log(CONSOLE, "Deleting items from index...");
        TermQuery query = new TermQuery(new Term(BasicProps.EVIDENCE_UUID, evidenceUUID));
        int prevDocs = writer.getDocStats().numDocs;
        writer.deleteDocuments(query);
        writer.commit();
        int deletes = prevDocs - writer.getDocStats().numDocs;
        LOGGER.log(CONSOLE, "Deleted {} raw documents from index.", deletes);

        // remove evidence from TSK DB
        if (tskID != null) {
            LOGGER.log(CONSOLE, "Deleting image reference from TSK DB...");
            SleuthkitReader.deleteImageInfo(tskID, output);
        }

        // remove item data from storage or file system
        ExportFileTask.deleteIgnoredItemData(caseData, output, true, writer);

        // clear bookmarks pointing to deleted items
        try (IPEDSource ipedCase = new IPEDSource(output.getParentFile(), writer)) {
            ipedCase.clearOldBookmarks();
        }

        writer.close();

        // removes graph connections from evidence
        LOGGER.log(CONSOLE, "Deleting connections from graph...");
        GraphService graphService = null;
        try {
            if (new File(output, GraphTask.DB_DATA_PATH).exists()) {
                graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
                graphService.start(new File(output, GraphTask.DB_HOME_DIR));
                int deletions = graphService.deleteRelationshipsFromDatasource(evidenceUUID);
                LOGGER.log(CONSOLE, "Deleted {} graph connections.", deletions);
            } else {
                LOGGER.log(CONSOLE, "Graph database not found.");
            }
        } finally {
            if (graphService != null) {
                graphService.stop();
            }
        }

        // Delete relations from graph source CSV
        LOGGER.log(CONSOLE, "Deleting connections from graph CSVs...");
        int deletions = GraphFileWriter.removeDeletedRelationships(evidenceUUID,
                new File(output, GraphTask.CSVS_PATH));
        LOGGER.log(CONSOLE, "Deleted {} CSV connections.", deletions);

        status.removeEvidence(evidenceName);
        status.save();
    }

    private void openIndex() throws IOException {
        UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.OpeningIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        boolean newIndex = !indexDir.exists();
        LOGGER.info((newIndex ? "Creating" : "Opening") + " index: {}", indexDir.getAbsoluteFile());
        Directory directory = ConfiguredFSDirectory.open(indexDir);
        IndexWriterConfig config = getIndexWriterConfig();
        
        if (args.isRestart()) {
            List<IndexCommit> commits = DirectoryReader.listCommits(directory);
            config.setIndexCommit(commits.get(0));
        }

        writer = new IndexWriter(directory, config);
        if (newIndex) {
            // first empty commit to be used by --restart
            writer.commit();
        }

        if (args.isRestart()) {
            try (IPEDSource ipedCase = new IPEDSource(output.getParentFile(), writer)) {
                ipedCase.clearOldBookmarks();
            }
        }

        if (args.isAppendIndex() || args.isContinue() || args.isRestart()) {
            loadExistingData();
        }

    }

    private void initWorkers() throws Exception {

        workers = new Worker[localConfig.getNumThreads()];
        for (int k = 0; k < workers.length; k++) {
            workers[k] = new Worker(k, caseData, writer, output, this);
        }
        for (Worker w : workers) {
            w.init();
        }

        // Execução dos workers após todos terem sido instanciados e terem inicializado
        // suas tarefas
        for (int k = 0; k < workers.length; k++) {
            workers[k].start();
        }

        UIPropertyListenerProvider.getInstance().firePropertyChange("workers", 0, workers); //$NON-NLS-1$
    }

    private void monitorProcessing() throws Exception {

        boolean someWorkerAlive = true;
        long start = System.currentTimeMillis();

        while (someWorkerAlive) {
            if (UIPropertyListenerProvider.getInstance().isCancelled()) {
                exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
            }

            String currentDir = counter.currentDirectory();
            if (counter.isAlive() && currentDir != null && !currentDir.trim().isEmpty()) {
                UIPropertyListenerProvider.getInstance().firePropertyChange("decodingDir", 0, //$NON-NLS-1$
                        Messages.getString("Manager.Adding") + currentDir.trim() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            UIPropertyListenerProvider.getInstance().firePropertyChange("update", 0, 0);

            boolean changeToNextQueue = !producer.isAlive();
            for (int k = 0; k < workers.length; k++) {
                if (workers[k].exception != null && exception == null) {
                    exception = workers[k].exception;
                }
                if (!workers[k].isWaiting()) {
                    changeToNextQueue = false;
                }
            }
            if (exception != null) {
                throw exception;
            }

            if (changeToNextQueue) {
                IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
                if (searcher != null) {
                    searcher.close();
                }
                IItem queueEnd = processingQueues.peekItemFromCurrentQueue();
                if (!queueEnd.isQueueEnd()) {
                    throw new IPEDException("Tried to get queue end from queue, but failed! Please warn the dev team.");
                }
                if (processingQueues.changeToNextQueue() != null) {
                    LOGGER.info(
                            "Changed to processing queue with priority " + processingQueues.getCurrentQueuePriority()); //$NON-NLS-1$
                    caseData.putCaseObject(IItemSearcher.class.getName(),
                            new ItemSearcher(output.getParentFile(), writer));
                    processingQueues.addToCurrentQueue(queueEnd);
                    for (int k = 0; k < workers.length; k++) {
                        workers[k].processNextQueue();
                    }
                } else {
                    someWorkerAlive = false;
                }
            }

            long t = System.currentTimeMillis();
            if (t - start >= commitIntervalMillis) {
                if (commitThread == null || !commitThread.isAlive()) {
                    commitThread = commit();
                }
                start = t;
            }
        }

    }

    private Thread commit() {
        // commit could be costly, do in another thread
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    long start = System.currentTimeMillis() / 1000;
                    UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CommitStarted"));
                    LOGGER.info("Prepare commit started...");
                    writer.prepareCommit();

                    // commit other control data
                    IndexTask.saveExtraAttributes(output);
                    IndexItem.saveMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
                    stats.commit();

                    LOGGER.info("Commiting sqlite storages...");
                    ExportFileTask.commitStorage(output);

                    GraphTask.commit();

                    ExportCSVTask.commit(output);

                    ElasticSearchIndexTask.commit();

                    writer.commit();

                    long end = System.currentTimeMillis() / 1000;
                    UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CommitFinished"));
                    LOGGER.info("Commit finished in " + (end - start) + "s");
                    partialCommitsTime.addAndGet(end - start);

                } catch (Exception e) {
                    if (exception == null) {
                        exception = e;
                    } else {
                        e.printStackTrace();
                    }
                    try {
                        LOGGER.error("Error commiting. Rollback commit started...");
                        writer.rollback();
                        LOGGER.error("Rollback commit finished.");

                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        };
        t.start();
        return t;
    }

    private void finishProcessing() throws Exception {

        if (commitThread != null && commitThread.isAlive()) {
            commitThread.join();
            if (exception != null) {
                throw exception;
            }
        }

        for (int k = 0; k < workers.length; k++) {
            workers[k].finish();
        }

        if (indexConfig.isForceMerge()) {
            UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.Optimizing")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("Optimizing Index..."); //$NON-NLS-1$
            try {
                writer.forceMerge(1);
            } catch (Throwable e) {
                LOGGER.error("Error while optimizing: {}", e); //$NON-NLS-1$
            }

        }

        stats.commit();

        UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.ClosingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        LOGGER.info("Closing Index..."); //$NON-NLS-1$
        writer.close();
        writer = null;

        if (!indexDir.getCanonicalPath().equalsIgnoreCase(finalIndexDir.getCanonicalPath())) {
            UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CopyingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("Moving Index..."); //$NON-NLS-1$
            try {
                Files.move(indexDir.toPath(), finalIndexDir.toPath());

            } catch (IOException e) {
                LOGGER.info("Move failed. Copying Index..."); //$NON-NLS-1$
                IOUtil.copyDirectory(indexDir, finalIndexDir);
            }
        }

        if (caseData.containsReport()) {
            new File(output, "data/containsReport.flag").createNewFile(); //$NON-NLS-1$
        }

    }

    private void updateImagePaths() {
        if (args.isPortable()) { // $NON-NLS-1$
            IPEDSource ipedCase = new IPEDSource(output.getParentFile());
            ipedCase.updateImagePathsToRelative();
            ipedCase.close();
        }
    }

    public void deleteTempDir() {
        LOGGER.info("Deleting temp folder {}", localConfig.getIndexerTemp()); //$NON-NLS-1$
        IOUtil.deleteDirectory(localConfig.getIndexerTemp());
    }

    private void filterKeywords() {

        try {
            LOGGER.info("Filtering keywords..."); //$NON-NLS-1$
            UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    Messages.getString("Manager.FilteringKeywords")); //$NON-NLS-1$
            ArrayList<String> palavras = Util.loadKeywords(output.getAbsolutePath() + "/palavras-chave.txt", //$NON-NLS-1$
                    Charset.defaultCharset().name());

            if (palavras.size() != 0) {
                IPEDSource ipedCase = new IPEDSource(output.getParentFile());
                ArrayList<String> palavrasFinais = new ArrayList<String>();
                for (String palavra : palavras) {
                    if (Thread.interrupted()) {
                        ipedCase.close();
                        throw new InterruptedException("Processing canceled!"); //$NON-NLS-1$
                    }

                    try {
                        IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, palavra);
                        if (pesquisa.search().getLength() > 0) {
                            palavrasFinais.add(palavra);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Erro filtering by {} {}", palavra, e.toString());
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

    private void removeEmptyTreeNodes() {

        if (!caseData.containsReport() || caseData.isIpedReport()) {
            return;
        }

        UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("Manager.DeletingTreeNodes")); //$NON-NLS-1$
        LOGGER.info("Deleting empty tree nodes"); //$NON-NLS-1$

        try (IPEDSource ipedCase = new IPEDSource(output.getParentFile())) {
            IPEDSearcher searchAll = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
            LuceneSearchResult result = LuceneSearchResult.get(ipedCase, searchAll.search());

            boolean[] doNotDelete = new boolean[stats.getLastId() + 1];
            for (int docID : result.getLuceneIds()) {
                String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
                if (!parentIds.trim().isEmpty()) {
                    for (String parentId : parentIds.trim().split(" ")) { //$NON-NLS-1$
                        doNotDelete[Integer.parseInt(parentId)] = true;
                    }
                }
            }

            writer = new IndexWriter(ConfiguredFSDirectory.open(finalIndexDir), getIndexWriterConfig());

            int startId = 0, interval = 1000, endId = interval;
            while (startId <= stats.getLastId()) {
                if (endId > stats.getLastId()) {
                    endId = stats.getLastId();
                }
                BooleanQuery.Builder builder = new BooleanQuery.Builder()
                        .add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST) //$NON-NLS-1$
                        .add(IntPoint.newRangeQuery(IndexItem.ID, startId, endId), Occur.MUST);
                for (int i = startId; i <= endId; i++) {
                    if (doNotDelete[i]) {
                        builder.add(IntPoint.newExactQuery(IndexItem.ID, i), Occur.MUST_NOT);
                    }
                }
                BooleanQuery query = builder.build();
                writer.deleteDocuments(query);
                startId = endId + 1;
                endId += interval;
            }

        } catch (Exception e) {
            LOGGER.warn("Error deleting empty tree nodes", e); //$NON-NLS-1$

        } finally {
            IOUtil.closeQuietly(writer);
        }

    }

    private void prepareOutputFolder() throws Exception {
        if (output.exists() && !args.isAppendIndex() && !args.isContinue() && !args.isRestart()
                && args.getEvidenceToRemove() == null) {
            throw new IPEDException("Directory already exists: " + output.getAbsolutePath()); //$NON-NLS-1$
        }

        File export = new File(output.getParentFile(), ExportFileTask.EXTRACT_DIR);
        if (export.exists() && !args.isAppendIndex() && !args.isContinue() && !args.isRestart()
                && args.getEvidenceToRemove() == null) {
            throw new IPEDException("Directory already exists: " + export.getAbsolutePath()); //$NON-NLS-1$
        }

        if (!output.exists() && !output.mkdirs()) {
            throw new IOException("Fail to create folder " + output.getAbsolutePath()); //$NON-NLS-1$
        }

        if (!args.isAppendIndex() && !args.isContinue() && !args.isRestart() && args.getEvidenceToRemove() == null) {
            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, "lib"), new File(output, "lib"), true); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, "scripts"), new File(output, "scripts"), true); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, "jre"), new File(output, "jre"), true); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, iped.localization.Messages.BUNDLES_FOLDER),
                    new File(output, iped.localization.Messages.BUNDLES_FOLDER), true); // $NON-NLS-1$ //$NON-NLS-2$

            // Copy tools. For now, skip copying mplayer
            File source = new File(Configuration.getInstance().appRoot, "tools");
            for (File file : source.listFiles()) {
                if (!file.getName().equals("mplayer")) {
                    File dest = new File(output, "tools/" + file.getName());
                    if (file.isDirectory()) {
                        IOUtil.copyDirectory(file, dest); // $NON-NLS-1$ //$NON-NLS-2$
                    } else {
                        dest.getParentFile().mkdirs();
                        IOUtil.copyFile(file, dest);
                    }
                }
            }

            if (!analysisConfig.isEmbedLibreOffice()) {
                new File(output, "tools/libreoffice.zip").delete(); //$NON-NLS-1$
            }

            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, "help"), new File(output, "help")); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copyDirectory(new File(Configuration.getInstance().appRoot, "htmlreport"), //$NON-NLS-1$
                    new File(output, "htmlreport")); //$NON-NLS-1$

            // copy default configs
            File defaultProfile = new File(Configuration.getInstance().appRoot);
            IOUtil.copyDirectory(new File(defaultProfile, "conf"), new File(output, "conf"));
            IOUtil.copyFile(new File(defaultProfile, Configuration.LOCAL_CONFIG), new File(output, Configuration.LOCAL_CONFIG));
            IOUtil.copyFile(new File(defaultProfile, Configuration.CONFIG_FILE), new File(output, Configuration.CONFIG_FILE));
            resetLocalConfigToPortable(new File(output, Configuration.LOCAL_CONFIG));
            setSplashMessage(output);

            // copy non default profile
            File currentProfile = new File(Configuration.getInstance().configPath);
            if (!currentProfile.equals(defaultProfile)) {
                IOUtil.copyDirectory(currentProfile, new File(output, Configuration.CASE_PROFILE_DIR), true);
                resetLocalConfigToPortable(new File(output, Configuration.CASE_PROFILE_DIR + "/" + Configuration.LOCAL_CONFIG));
            }
            if (caseData.isIpedReport()) {
                File caseProfile = new File(Configuration.getInstance().appRoot, Configuration.CASE_PROFILE_DIR);
                if (caseProfile.exists()) {
                    IOUtil.copyDirectory(caseProfile, new File(output, Configuration.CASE_PROFILE_DIR));
                }
            }

            File binDir = new File(Configuration.getInstance().appRoot, "bin"); //$NON-NLS-1$
            if (binDir.exists())
                IOUtil.copyDirectory(binDir, output.getParentFile()); // $NON-NLS-1$
            else {
                // Copy only IPED Windows executable (#1698)
                File exe = new File(new File(Configuration.getInstance().appRoot).getParentFile(), appWinExeFileName);
                if (exe.exists()) {
                    IOUtil.copyFile(exe, new File(output.getParentFile(), exe.getName()));
                }
            }
        }

        if (palavrasChave != null) {
            IOUtil.copyFile(palavrasChave, new File(output, "palavras-chave.txt")); //$NON-NLS-1$
        }

        File dataDir = new File(output, "data"); //$NON-NLS-1$
        if (!dataDir.exists()) {
            if (!dataDir.mkdir()) {
                throw new IOException("Fail to create folder " + dataDir.getAbsolutePath()); //$NON-NLS-1$
            }
        }

    }

    // See https://github.com/sepinf-inc/IPED/issues/1142
    private void resetLocalConfigToPortable(File localConfig) throws IOException {
        if (localConfig.exists() && (caseData.isIpedReport() || args.isPortable())) {
            LocalConfig.clearLocalParameters(localConfig);
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
    
    private void setSplashMessage(File dir) throws IOException {
        String msg = args.getSplashMessage();
        if (msg != null && !msg.isBlank()) {
            File splashConfigFile = new File(dir, SplashScreenConfig.CONFIG_FILE);
            if (splashConfigFile.exists()) {
                List<String> l = Files.readAllLines(splashConfigFile.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < l.size(); i++) {
                    String line = l.get(i);
                    if (line.trim().startsWith(SplashScreenConfig.CUSTOM_MESSAGE)) {
                        l.set(i, SplashScreenConfig.CUSTOM_MESSAGE + " = " + msg);
                        Files.write(splashConfigFile.toPath(), l, StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING);
                        break;
                    }
                }
            }
        }
    }

}
