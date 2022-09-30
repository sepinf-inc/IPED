package iped.engine.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JOptionPane;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ExportByCategoriesConfig;
import iped.engine.config.ExportByKeywordsConfig;
import iped.engine.config.LocalConfig;
import iped.engine.localization.Messages;
import iped.engine.lucene.ConfiguredFSDirectory;
import iped.engine.task.ExportFileTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.parsers.standard.StandardParser;
import iped.utils.HashValue;

/**
 * Classe que armazena estatísticas diversas, como número de itens processados,
 * volume processado, número de timeouts, duplicados ignorados, etc. Contém
 * métodos para enviar as estatísticas para arquivo de log.
 */
public class Statistics {

    private static final String CARVED_IGNORED_MAP_FILE = "data/carvedIgnoredMap.dat";

    private static Logger LOGGER = LoggerFactory.getLogger(Statistics.class);
    private static Statistics instance = null;

    private static final float IO_ERROR_RATE_TO_WARN = 0.05f;

    private HashMap<HashValue, Integer> ignoredMap = new HashMap<>();

    ICaseData caseData;
    File indexDir;

    // EstatÃ­sticas
    Date start = new Date();
    int splits = 0;
    int timeouts = 0;
    int processed = 0;
    int activeProcessed = 0;
    long volumeIndexed = 0;
    int lastId = -1;
    int corruptCarveIgnored = 0;
    int ignored = 0;
    int previousIndexedFiles = 0;
    int ioerrors = 0;
    AtomicInteger subitensDiscovered = new AtomicInteger();

    public static Statistics get(ICaseData caseData, File indexDir) {
        if (instance == null) {
            instance = new Statistics(caseData, indexDir);
        }
        return instance;
    }

    public static Statistics get() {
        return instance;
    }

    public int getCarvedIgnoredNum(HashValue trackId) {
        synchronized (ignoredMap) {
            return ignoredMap.getOrDefault(trackId, 0);
        }
    }

    private Statistics(ICaseData caseData, File indexDir) {
        this.caseData = caseData;
        this.indexDir = indexDir;
        loadPrevCarvedIgnoredMap();
    }

    public ICaseData getCaseData() {
        return this.caseData;
    }

    private void loadPrevCarvedIgnoredMap() {
        File file = new File(indexDir.getParentFile(), CARVED_IGNORED_MAP_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                ignoredMap = (HashMap<HashValue, Integer>) ois.readObject();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void incCarvedIgnored(IItem item) {
        this.incCorruptCarveIgnored();
        HashValue parentPersistId = new HashValue((String) item.getExtraAttribute(IndexItem.PARENT_TRACK_ID));
        synchronized (ignoredMap) {
            Integer ignored = ignoredMap.getOrDefault(parentPersistId, 0);
            ignoredMap.put(parentPersistId, ++ignored);
        }
    }

    public void resetCarvedIgnored(IItem item) {
        HashValue parentPersistId = new HashValue((String) item.getExtraAttribute(IndexItem.TRACK_ID));
        synchronized (ignoredMap) {
            ignoredMap.remove(parentPersistId);
        }
    }

    public void commit() throws IOException {
        File file = new File(indexDir.getParentFile(), CARVED_IGNORED_MAP_FILE);
        synchronized (ignoredMap) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(ignoredMap);
            }
        }
        Util.fsync(file.toPath());
    }

    synchronized public int getSplits() {
        return splits;
    }

    synchronized public void incSplits() {
        splits++;
    }

    synchronized public int getTimeouts() {
        return timeouts;
    }

    synchronized public void incTimeouts() {
        timeouts++;
    }

    synchronized public void incProcessed() {
        processed++;
    }

    synchronized public int getProcessed() {
        return processed;
    }

    synchronized public void incIoErrors() {
        ioerrors++;
    }

    synchronized public int getIoErrors() {
        return ioerrors;
    }

    synchronized public void incActiveProcessed() {
        activeProcessed++;
    }

    synchronized public int getActiveProcessed() {
        return activeProcessed;
    }

    synchronized public void addVolume(long volume) {
        volumeIndexed += volume;
    }

    synchronized public long getVolume() {
        return volumeIndexed;
    }

    synchronized public int getCorruptCarveIgnored() {
        return corruptCarveIgnored;
    }

    private synchronized void incCorruptCarveIgnored() {
        corruptCarveIgnored++;
    }

    synchronized public int getIgnored() {
        return ignored;
    }

    synchronized public void incIgnored() {
        ignored++;
    }

    synchronized public void updateLastId(int id) {
        if (id > lastId) {
            lastId = id;
        }
    }

    synchronized public int getLastId() {
        return lastId;
    }

    synchronized public void setLastId(int id) {
        lastId = id;
    }

    public void incSubitemsDiscovered() {
        this.subitensDiscovered.incrementAndGet();
    }

    public int getSubitemsDiscovered() {
        return this.subitensDiscovered.get();
    }

    public void logStatistics(Manager manager) throws Exception {

        int processed = getProcessed();
        int extracted = ExportFileTask.getItensExtracted();
        int activeFiles = getActiveProcessed();
        int carvedIgnored = getCorruptCarveIgnored();
        int ignored = getIgnored();

        long totalTime = 0;
        Worker[] workers = manager.getWorkers();
        long[] taskTimes = new long[workers[0].tasks.size()];
        for (Worker worker : workers) {
            for (int i = 0; i < taskTimes.length; i++) {
                taskTimes[i] += worker.tasks.get(i).getTaskTime();
                totalTime += worker.tasks.get(i).getTaskTime();
            }
        }
        LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        totalTime = totalTime / (1000000 * localConfig.getNumThreads());
        for (int i = 0; i < taskTimes.length; i++) {
            long sec = taskTimes[i] / (1000000 * localConfig.getNumThreads());
            LOGGER.info(workers[0].tasks.get(i).getName() + ":\tProcessing Time:\t" + sec + "s (" //$NON-NLS-1$ //$NON-NLS-2$
                    + Math.round((100f * sec) / totalTime) + "%)"); //$NON-NLS-1$
        }

        int numDocs;
        try (IndexReader reader = DirectoryReader.open(ConfiguredFSDirectory.open(indexDir))) {
            numDocs = reader.numDocs();
        }

        LOGGER.info("Partial commits took {} seconds", manager.partialCommitsTime.get());
        LOGGER.info("Index internal docs: {}", numDocs); //$NON-NLS-1$
        LOGGER.info("Text Splits: {}", getSplits()); //$NON-NLS-1$
        LOGGER.info("Timeouts: {}", getTimeouts()); //$NON-NLS-1$
        LOGGER.info("Parsing Exceptions: {}", StandardParser.parsingErrors); //$NON-NLS-1$
        LOGGER.info("I/O read errors: {}", this.getIoErrors()); //$NON-NLS-1$
        LOGGER.info("Subitems Found: {}", getSubitemsDiscovered()); //$NON-NLS-1$
        LOGGER.info("Exported Items: {}", extracted); //$NON-NLS-1$
        LOGGER.info("Total Carved Items: {}", BaseCarveTask.getItensCarved()); //$NON-NLS-1$
        LOGGER.info("Carved Ignored (corrupted): {}", carvedIgnored); //$NON-NLS-1$
        LOGGER.info("Ignored Items: {}", ignored); //$NON-NLS-1$

        int indexed = (numDocs - getSplits() - previousIndexedFiles) / 2;
        LOGGER.info("Total Indexed: {}", indexed); //$NON-NLS-1$

        LOGGER.info("Discovered volume: {} bytes", caseData.getDiscoveredVolume());
        LOGGER.info("Processed  volume: {} bytes", getVolume());

        long processedVolume = getVolume() / (1024 * 1024);

        if (activeFiles != processed) {
            LOGGER.info("Active Items: {}", activeFiles); //$NON-NLS-1$
        }

        LOGGER.info("Total processed: {} items in {} seconds ({} MB)", processed, //$NON-NLS-1$
                ((new Date()).getTime() - start.getTime()) / 1000, processedVolume);

        int discovered = caseData.getDiscoveredEvidences();
        if (processed != discovered) {
            LOGGER.error("Alert: Processed " + processed + " items of " + discovered); //$NON-NLS-1$ //$NON-NLS-2$
        }

        ExportByCategoriesConfig exportByCategories = ConfigurationManager.get()
                .findObject(ExportByCategoriesConfig.class);
        ExportByKeywordsConfig exportByKeywords = ConfigurationManager.get().findObject(ExportByKeywordsConfig.class);

        if (!(exportByCategories.hasCategoryToExport() || exportByKeywords.isEnabled())) {
            if (indexed != discovered - carvedIgnored - ignored) {
                LOGGER.error("Alert: Indexed " + indexed + " items of " + (discovered - carvedIgnored - ignored)); //$NON-NLS-1$ //$NON-NLS-2$
            }
        } /*
           * else if (indexed != extracted) throw new Exception("Indexados " + indexed +
           * " itens de " + extracted);
           */

        if (this.getIoErrors() > processed * IO_ERROR_RATE_TO_WARN)
            LOGGER.error("Warning: IO Errors happened while reading {} items from {}!", getIoErrors(), processed); //$NON-NLS-1$
    }

    public void printSystemInfo() throws Exception {
        LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        LOGGER.info("Operating System: {}", System.getProperty("os.name")); //$NON-NLS-1$ //$NON-NLS-2$
        LOGGER.info("Java Version: {}", System.getProperty("java.version")); //$NON-NLS-1$ //$NON-NLS-2$
        String warn = Util.getJavaVersionWarn();
        if (warn != null)
            LOGGER.error(warn); // $NON-NLS-1$ //$NON-NLS-2$

        String arch = System.getProperty("os.arch");
        LOGGER.info("Architecture: {}", arch); //$NON-NLS-1$
        if (!arch.contains("64")) {
            throw new IPEDException("Java 32 bits not supported anymore. Please update to a 64 bits version.");
        }
        LOGGER.info("Current Directory: {}", System.getProperty("user.dir")); //$NON-NLS-1$ //$NON-NLS-2$
        LOGGER.info("CPU Cores: {}", Runtime.getRuntime().availableProcessors()); //$NON-NLS-1$
        LOGGER.info("numThreads: {}", localConfig.getNumThreads()); //$NON-NLS-1$

        long maxMemory = Runtime.getRuntime().maxMemory() / 1000000;
        LOGGER.info("Memory (Heap) Available: {} MB", maxMemory); //$NON-NLS-1$

        for (String path : System.getProperty("java.class.path").split(";")) {
            LOGGER.info("ClassPath: {}", path);
        }

        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        for (String arg : bean.getInputArguments()) {
            LOGGER.info("JVM Argument: {}", arg);
        }

        LOGGER.info("Java Command: {}", System.getProperty("sun.java.command"));

        StringBuilder options = new StringBuilder();
        options.append("Config Options: "); //$NON-NLS-1$
        for (Entry<Object, Object> entry : Configuration.getInstance().properties.entrySet()) {
            options.append(entry.getKey() + "=" + entry.getValue() + " | "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        LOGGER.info(options.toString());

        int minMemPerThread = 200;
        if (maxMemory / localConfig.getNumThreads() < minMemPerThread) {
            String memoryAlert = Messages.getString("Statistics.LowMemory.Msg").replace("{}", //$NON-NLS-1$
                    Integer.toString(minMemPerThread));
            CmdLineArgs cmdArgs = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
            if (!cmdArgs.isNogui()) {
                JOptionPane.showMessageDialog(null, memoryAlert, Messages.getString("Statistics.LowMemory.Title"), //$NON-NLS-1$
                        JOptionPane.WARNING_MESSAGE);
            }
            throw new IPEDException(memoryAlert);
        }

    }

}
