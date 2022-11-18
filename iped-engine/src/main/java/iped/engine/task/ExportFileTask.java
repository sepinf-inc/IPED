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
package iped.engine.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Pragma;
import org.sqlite.SQLiteConfig.SynchronousMode;

import iped.configuration.Configurable;
import iped.data.ICaseData;
import iped.data.IHashValue;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.config.ExportByCategoriesConfig;
import iped.engine.config.ExportByKeywordsConfig;
import iped.engine.config.HashTaskConfig;
import iped.engine.config.HtmlReportTaskConfig;
import iped.engine.data.Category;
import iped.engine.data.IPEDSource;
import iped.engine.localization.Messages;
import iped.engine.task.index.IndexItem;
import iped.engine.util.UIPropertyListenerProvider;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.exception.ZipBombException;
import iped.io.SeekableInputStream;
import iped.parsers.util.ExportFolder;
import iped.utils.FileInputStreamFactory;
import iped.utils.HashValue;
import iped.utils.IOUtil;
import iped.utils.SeekableFileInputStream;
import iped.utils.SeekableInputStreamFactory;

/**
 * Responsável por extrair subitens de containers. Também exporta itens ativos
 * em casos de extração automática de dados ou em casos de extração de itens
 * selecionados após análise.
 */
public class ExportFileTask extends AbstractTask {

    private static final String ENABLE_PARAM = ExportByCategoriesConfig.ENABLE_PARAM;

    private static Logger LOGGER = LogManager.getLogger(ExportFileTask.class);
    private final Level CONSOLE = Level.forName("MSG", 250);

    public static final String EXTRACT_DIR = Messages.getString("ExportFileTask.ExportFolder"); //$NON-NLS-1$
    private static final String SUBITEM_DIR = "subitens"; //$NON-NLS-1$

    private static final String STORAGE_PREFIX = "storage";
    public static final String STORAGE_CON_PREFIX = "storageConnection";
    private static final int MAX_BUFFER_SIZE = 1 << 24;
    private static final int SQLITE_CACHE_SIZE = 1 << 24;

    private static final byte DB_SUFFIX_BITS = 4; // current impl maximum is 8

    private static final String CREATE_TABLE1 = "CREATE TABLE IF NOT EXISTS thumbs(id TEXT PRIMARY KEY, thumb BLOB);";
    private static final String CREATE_TABLE2 = "CREATE TABLE IF NOT EXISTS t1(id TEXT PRIMARY KEY, data BLOB);";

    private static final String INSERT_DATA = "INSERT INTO t1(id, data) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET data=? WHERE data IS NULL;";

    private static final String CHECK_HASH = "SELECT id FROM t1 WHERE id=? AND data IS NOT NULL;";
    
    private static final String SELECT_IDS_WITH_DATA = "SELECT id FROM t1 WHERE data IS NOT NULL;";

    private static final String CLEAR_DATA = "DELETE FROM t1 WHERE id=?;";

    // maps below are used to track different storages/connections in multicases
    private static HashMap<File, HashMap<Integer, File>> storage = new HashMap<>();
    private static HashMap<File, HashMap<Integer, Connection>> storageCon = new HashMap<>();

    private static AtomicInteger counter = new AtomicInteger();
    
    private static AtomicBoolean warned = new AtomicBoolean();

    private static ArrayList<IHashValue> noContentHashes = new ArrayList<>();

    public static int subDirCounter = 0, itensExtracted = 0;
    private static File subDir;

    private static boolean computeHash = false;
    private static File extractDir;

    private HashMap<IHashValue, IHashValue> hashMap;
    private List<String> noContentLabels;
    private ExportByCategoriesConfig exportByCategories;
    private ExportByKeywordsConfig exportByKeywords;
    private CategoryConfig categoryConfig;
    private boolean automaticExportEnabled = false;

    public ExportFileTask() {
        ExportFolder.setExportPath(EXTRACT_DIR);
    }

    public static synchronized void incItensExtracted() {
        itensExtracted++;
    }

    public static int getItensExtracted() {
        return itensExtracted;
    }

    private static void setExtractLocation(ICaseData caseData, File output) {
        if (output != null && extractDir == null) {
            if (caseData.containsReport() || new File(output.getParentFile(), EXTRACT_DIR).exists()) {
                extractDir = new File(output.getParentFile(), EXTRACT_DIR);
            } else {
                extractDir = new File(output, SUBITEM_DIR);
            }
        }
        HtmlReportTaskConfig htmlReportConfig = ConfigurationManager.get()
                .findObject(HtmlReportTaskConfig.class);
        if (!caseData.containsReport() || new File(output, STORAGE_PREFIX).exists() || !htmlReportConfig.isEnabled()) {
            if (storageCon.get(output) == null) {
                configureSQLiteStorage(output);
            }
        }
    }

    public static Connection getSQLiteStorageCon(File output, byte[] hash) {
        if (storageCon.get(output) == null) {
            configureSQLiteStorage(output);
        }
        int dbSuffix = getStorageSuffix(hash);
        return storageCon.get(output).get(dbSuffix);
    }

    private static int getStorageSuffix(byte[] hash) {
        return (hash[0] & 0xFF) >> (8 - DB_SUFFIX_BITS);
    }

    private static Connection getSQLiteStorageCon(File db) {
        File output = db.getParentFile().getParentFile();
        if (storageCon.get(output) == null) {
            configureSQLiteStorage(output);
        }
        int dbSuffix = Integer
                .valueOf(db.getName().substring(STORAGE_PREFIX.length() + 1, db.getName().indexOf(".db")));
        return storageCon.get(output).get(dbSuffix);
    }

    private static synchronized void configureSQLiteStorage(File output) {
        if (storageCon.get(output) != null) {
            return;
        }
        HashMap<Integer, Connection> tempStorageCon = new HashMap<>();
        HashMap<Integer, File> tempStorage = new HashMap<>();
        for (int i = 0; i < Math.pow(2, DB_SUFFIX_BITS); i++) {
            String storageName = STORAGE_PREFIX + "-" + i + ".db";
            File db = new File(output, STORAGE_PREFIX + File.separator + storageName);
            db.getParentFile().mkdir();
            tempStorage.put(i, db);
            try {
                Connection con = getSQLiteConnection(db);
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate(CREATE_TABLE1);
                }
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate(CREATE_TABLE2);
                }
                tempStorageCon.put(i, con);

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        storage.put(output, tempStorage);
        storageCon.put(output, tempStorageCon);
    }

    private static Connection getSQLiteConnection(File storage) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setSynchronous(SynchronousMode.NORMAL);
        config.setPragma(Pragma.JOURNAL_MODE, "TRUNCATE");
        config.setPragma(Pragma.CACHE_SIZE, "-" + SQLITE_CACHE_SIZE / 1024);
        config.setBusyTimeout(3600000);
        Connection conn = config.createConnection("jdbc:sqlite:" + storage.getAbsolutePath());
        conn.setAutoCommit(false);
        return conn;
    }

    private static synchronized File getSubDir(File extractDir) {
        if (subDirCounter % 1000 == 0) {
            subDir = new File(extractDir, Integer.toString(subDirCounter / 1000));
        }
        subDirCounter++;
        return subDir;
    }

    public boolean isAutomaticExportEnabled() {
        return automaticExportEnabled && (exportByCategories.hasCategoryToExport() || exportByKeywords.isEnabled());
    }

    private boolean isToBeExtracted(IItem evidence) {

        for (String catName : evidence.getCategorySet()) {
            Category category = categoryConfig.getCategoryFromName(catName);
            while (category != null && category.getName() != null) {
                if (exportByCategories.isToExportCategory(category.getName())) {
                    return true;
                }
                category = category.getParent();
            }
        }
        return false;
    }

    public void process(IItem evidence) {

        // Exporta arquivo no caso de extração automatica ou no caso de relatório do
        // iped
        if ((caseData.isIpedReport() && evidence.isToAddToCase())
                || (!evidence.isSubItem() && isAutomaticExportEnabled() && (isToBeExtracted(evidence) || evidence.isToExtract()))) {

            evidence.setToExtract(true);
            if (doNotExport(evidence)) {
                evidence.setTempAttribute(IndexItem.IGNORE_CONTENT_REF, "true");

            } else if (!MinIOTask.isTaskEnabled() || caseData.isIpedReport()) {
                extract(evidence);
            }

            incItensExtracted();
            copyViewFile(evidence);
        }

        // Renomeia subitem caso deva ser exportado
        if (!caseData.isIpedReport() && evidence.isSubItem()
                && (evidence.isToExtract() || isToBeExtracted(evidence) || !isAutomaticExportEnabled())) {

            evidence.setToExtract(true);
            if (!doNotExport(evidence)) {
                renameToHash(evidence);
            } else {
                // clear path to be not indexed, continuing to point to File for processing,
                // this also makes subitems without 'export' property to be deleted later
                evidence.setTempAttribute(IndexItem.IGNORE_CONTENT_REF, "true");

                // store references to -nocontent items to be deleted from sqlite storages
                IHashValue hashValue = evidence.getHashValue();
                if (hashValue != null) {
                    synchronized (hashMap) {
                        // this uses less memory reusing a previous stored reference
                        hashValue = hashMap.get(hashValue);
                        noContentHashes.add(hashValue);
                    }
                }
            }
            incItensExtracted();
        }

        if (isAutomaticExportEnabled() && !evidence.isToExtract()) {
            evidence.setAddToCase(false);
        }

    }

    private boolean doNotExport(IItem evidence) {
        if (noContentLabels == null) {
            CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
            noContentLabels = args.getNocontent();
            if (noContentLabels == null) {
                noContentLabels = Collections.emptyList();
            }
        }
        if (noContentLabels.isEmpty())
            return false;
        Collection<String> evidenceLabels = evidence.getLabels().isEmpty() ? evidence.getCategorySet()
                : evidence.getLabels();
        for (String label : evidenceLabels) {
            boolean isNoContent = false;
            for (String noContentLabel : noContentLabels) {
                if (noContentLabel.equalsIgnoreCase("all") || label.equalsIgnoreCase(noContentLabel)) {
                    isNoContent = true;
                    break;
                }
            }
            if (!isNoContent)
                return false;
        }
        return true;
    }

    public void extract(IItem evidence) {
        InputStream is = null;
        try {
            is = evidence.getBufferedInputStream();
            extractFile(is, evidence, null);

        } catch (IOException e) {
            LOGGER.warn("{} Error exporting {} \t{}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                    e.toString());

        } finally {
            IOUtil.closeQuietly(is);
        }
    }

    private void copyViewFile(IItem evidence) {
        if (!caseData.isIpedReport()) {
            return;
        }
        File viewFile = evidence.getViewFile();
        if (viewFile != null) {
            String viewName = viewFile.getName();
            File destFile = new File(output, "view/" + viewName.charAt(0) + "/" + viewName.charAt(1) + "/" + viewName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (destFile.equals(viewFile)) {
                return;
            }
            destFile.getParentFile().mkdirs();
            try {
                IOUtil.copyFile(viewFile, destFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File getHashFile(String hash, String ext) {
        String path = hash.charAt(0) + "/" + hash.charAt(1) + "/" + Util.getValidFilename(hash + ext); //$NON-NLS-1$ //$NON-NLS-2$
        if (extractDir == null) {
            setExtractLocation(caseData, output);
        }
        return new File(extractDir, path);
    }

    public void renameToHash(IItem evidence) {

        String hash = evidence.getHash();
        if (hash != null && !hash.isEmpty() && IOUtil.hasFile(evidence)) {
            File file = IOUtil.getFile(evidence);
            String ext = evidence.getType();
            if (evidence.getLength() == null || evidence.getLength() == 0) {
                ext = "";
            }
            if (!ext.isEmpty()) {
                ext = convertCharsToASCII(ext);
                ext = "." + Util.removeNonLatin1Chars(ext);
            }

            File hashFile = getHashFile(hash, ext);
            if (!hashFile.getParentFile().exists()) {
                hashFile.getParentFile().mkdirs();
            }
            IHashValue hashVal = new HashValue(hash);
            IHashValue hashLock;
            synchronized (hashMap) {
                hashLock = hashMap.get(hashVal);
            }

            synchronized (hashLock) {
                if (!hashFile.exists()) {
                    try {
                        Files.move(file.toPath(), hashFile.toPath());
                        changeTargetFile(evidence, hashFile);

                    } catch (IOException e) {
                        // falha ao renomear pode ter sido causada por outra thread
                        // criando arquivo com mesmo hash entre as 2 chamadas acima
                        if (hashFile.exists()) {
                            changeTargetFile(evidence, hashFile);
                            if (!file.delete()) {
                                LOGGER.warn("{} Error deleting {}", Thread.currentThread().getName(), //$NON-NLS-1$
                                        file.getAbsolutePath());
                            }
                        } else {
                            LOGGER.warn("{} Error renaming to hash: {}", Thread.currentThread().getName(), //$NON-NLS-1$
                                    file.getAbsolutePath());
                            e.printStackTrace();
                        }
                    }

                } else {
                    changeTargetFile(evidence, hashFile);
                    if (!file.equals(hashFile) && !file.delete()) {
                        LOGGER.warn("{} Error Deleting {}", Thread.currentThread().getName(), file.getAbsolutePath()); //$NON-NLS-1$
                    }
                }
            }

        }

    }

    private void changeTargetFile(IItem evidence, File file) {
        String relativePath = Util.getRelativePath(output, file);
        evidence.setIdInDataSource(relativePath);
        evidence.setInputStreamFactory(new FileInputStreamFactory(output.getParentFile().toPath()));
        evidence.setFileOffset(-1);
        file.setReadOnly();
    }

    private boolean isMarkSupportedInputStreamEmpty(InputStream inputStream) {
        inputStream.mark(1);
        try {
            if (inputStream.read() == -1) {
                return true;
            }
        } catch (Exception e) {
            // ignore even runtime exceptions
        } finally {
            try {
                inputStream.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static final String convertCharsToASCII(String str) {
        char[] input = str.toCharArray();
        char[] output = new char[input.length * 4];
        int length = ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
        return new String(output, 0, length);
    }

    public void extractFile(InputStream inputStream, IItem evidence, Long parentSize) throws IOException {

        String hash = null;
        File outputFile = null;
        Object hashLock = new Object();

        String ext = ""; //$NON-NLS-1$
        if (evidence.getType() != null) {
            ext = evidence.getType();
        }
        if (!ext.isEmpty()) {
            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream);
            }
            if (isMarkSupportedInputStreamEmpty(inputStream)) {
                ext = "";
            }
        }
        if (!ext.isEmpty()) {
            ext = convertCharsToASCII(ext);
            ext = "." + Util.removeNonLatin1Chars(ext);
        }

        if (extractDir == null) {
            setExtractLocation(caseData, output);
        }

        if (!computeHash) {
            outputFile = new File(getSubDir(extractDir), Util.getValidFilename(counter.getAndIncrement() + ext));
        } else if ((hash = evidence.getHash()) != null && !hash.isEmpty()) {
            outputFile = getHashFile(hash, ext);
            IHashValue hashVal = new HashValue(hash);
            synchronized (hashMap) {
                hashLock = hashMap.get(hashVal);
            }

        } else {
            outputFile = new File(extractDir, Util.getValidFilename("0" + counter.getAndIncrement() + ext)); //$NON-NLS-1$
        }

        boolean fileExists = false;

        synchronized (hashLock) {
            if (hash == null || !(fileExists = outputFile.exists())) {
                BufferedOutputStream bos = null;
                try (TemporaryResources tmp = new TemporaryResources()) {

                    TikaInputStream tis = TikaInputStream.get(inputStream, tmp);
                    InputStream poiInputStream = Util.getPOIFSInputStream(tis);
                    inputStream = poiInputStream != null ? poiInputStream : tis;

                    long total = 0;
                    int i = 0;
                    while (i != -1 && !Thread.currentThread().isInterrupted()) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        Exception exception = null;
                        try {
                            byte[] buf = new byte[8 * 1024];
                            while (baos.size() <= MAX_BUFFER_SIZE - buf.length && (i = inputStream.read(buf)) != -1) {
                                baos.write(buf, 0, i);
                            }
                        } catch (Exception e) {
                            // catch exceptions here to extract some content, even runtime exceptions
                            exception = e;
                        }
                        if ((i == -1 || exception != null) && storageCon.get(output) != null && total == 0) {
                            if (baos.size() == 0) {
                                evidence.setLength(0L);
                            } else {
                                byte[] buf = baos.toByteArray();
                                baos = null;
                                insertIntoStorage(evidence, buf, buf.length);
                            }
                        } else {
                            if (bos == null) {
                                if (!outputFile.getParentFile().exists()) {
                                    outputFile.getParentFile().mkdirs();
                                }
                                // a read-only file using id as name may exist, could be a subitem left behind
                                // by a previous interrupted processing, see #721
                                if (outputFile.exists()) {
                                    outputFile.setWritable(true);
                                }
                                bos = new BufferedOutputStream(Files.newOutputStream(outputFile.toPath()));
                                fileExists = true;
                            }
                            bos.write(baos.toByteArray());
                            total += baos.size();
                        }

                        if (exception != null)
                            throw exception;

                        if (ZipBombException.isZipBomb(parentSize, total)) {
                            throw new ZipBombException("Potential zip bomb while extracting subitem!"); //$NON-NLS-1$
                        }

                    }

                    // must catch generic Exception because of Runtime exceptions while extracting
                    // corrupted subitems
                } catch (Exception e) {
                    if (e instanceof IOException && IOUtil.isDiskFull((IOException) e))
                        LOGGER.error("Error exporting {}\t{}", evidence.getPath(), "No space left on output disk!"); //$NON-NLS-1$ //$NON-NLS-2$
                    else
                        LOGGER.warn("Error exporting {}\t{}", evidence.getPath(), e.toString()); //$NON-NLS-1$

                    LOGGER.debug("", e);

                } finally {
                    if (bos != null) {
                        bos.close();
                    }
                }
            }
        }

        if (fileExists) {
            changeTargetFile(evidence, outputFile);
            if (evidence.isSubItem()) {
                evidence.setLength(outputFile.length());
            }
        }

    }

    private void insertIntoStorage(IItem evidence, byte[] buf, int len)
            throws InterruptedException, IOException, SQLException, CompressorException {
        byte[] hash = null;
        String hashString = (String) evidence.getExtraAttribute(HashTask.HASH.MD5.toString());
        if (hashString != null) {
            hash = new HashValue(hashString).getBytes();
        } else {
            hash = DigestUtils.md5(new ByteArrayInputStream(buf, 0, len));
        }
        int k = getStorageSuffix(hash);
        boolean alreadyInDB = false;
        String id = hashString != null ? hashString : new HashValue(hash).toString();
        try (PreparedStatement ps = storageCon.get(output).get(k).prepareStatement(CHECK_HASH)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                alreadyInDB = true;
            }
        }
        if (!alreadyInDB) {
            try (PreparedStatement ps = storageCon.get(output).get(k).prepareStatement(INSERT_DATA)) {
                ps.setString(1, id);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStream gzippedOut = new GzipCompressorOutputStream(baos, getGzipParams());
                // OutputStream gzippedOut = new LZ4BlockOutputStream(baos);
                gzippedOut.write(buf, 0, len);
                gzippedOut.close();
                byte[] bytes = baos.toByteArray();
                baos = null;
                ps.setBytes(2, bytes);
                ps.setBytes(3, bytes);
                ps.executeUpdate();
            }
        }
        evidence.setIdInDataSource(id);
        evidence.setInputStreamFactory(
                new SQLiteInputStreamFactory(storage.get(output).get(k).toPath(), storageCon.get(output).get(k)));
        evidence.setFileOffset(-1);
        evidence.setLength((long) len);
    }

    private GzipParameters getGzipParams() {
        GzipParameters compression = new GzipParameters();
        compression.setCompressionLevel(Deflater.BEST_SPEED);
        return compression;
    }

    public static class SQLiteInputStreamFactory extends SeekableInputStreamFactory {

        private static final String SELECT_DATA = "SELECT data FROM t1 WHERE id=?;";

        private Connection conn;

        public SQLiteInputStreamFactory(Path datasource) {
            super(datasource.toUri());
        }

        public SQLiteInputStreamFactory(Path datasource, Connection conn) {
            super(datasource.toUri());
            this.conn = conn;
        }

        @Override
        public boolean checkIfDataSourceExists() {
            // do nothing, it will always be into case folder
            // and files which content was not exported to report will not trigger a dialog
            // asking for datasource path
            return false;
        }

        @Override
        public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
            try {
                byte[] bytes = null;
                if (conn == null || conn.isClosed()) {
                    conn = getSQLiteStorageCon(Paths.get(getDataSourceURI()).toFile());
                }
                try (PreparedStatement ps = conn.prepareStatement(SELECT_DATA)) {
                    ps.setString(1, identifier);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            bytes = rs.getBytes(1);
                        }
                    }
                }
                InputStream gzippedIn = new GzipCompressorInputStream(new ByteArrayInputStream(bytes));
                // gzippedIn = new LZ4BlockInputStream(new ByteArrayInputStream(bytes));
                bytes = IOUtils.toByteArray(gzippedIn);
                gzippedIn.close();
                return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));

            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }

    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        EnableTaskProperty result = ConfigurationManager.get().findObject(EnableTaskProperty.class);
        if(result == null) {
            result = new EnableTaskProperty(ENABLE_PARAM);
        }
        ExportByCategoriesConfig result2 = ConfigurationManager.get().findObject(ExportByCategoriesConfig.class);
        if(result2 == null) {
            result2 = new ExportByCategoriesConfig();
        }
        ExportByKeywordsConfig result3 = ConfigurationManager.get().findObject(ExportByKeywordsConfig.class);
        if(result3 == null) {
            result3 = new ExportByKeywordsConfig();
        }
        return Arrays.asList(result, result2, result3);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        automaticExportEnabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
        exportByCategories = configurationManager.findObject(ExportByCategoriesConfig.class);
        exportByKeywords = configurationManager.findObject(ExportByKeywordsConfig.class);
        categoryConfig = configurationManager.findObject(CategoryConfig.class);

        if (automaticExportEnabled && !exportByCategories.hasCategoryToExport() && !exportByKeywords.isEnabled()) {
            throw new IPEDException("Inconsistent configuration: " + ENABLE_PARAM + "=true but " + ExportByCategoriesConfig.CONFIG_FILE + "/" + ExportByKeywordsConfig.CONFIG_FILE + " not configured!");
        }
        if (!automaticExportEnabled && (exportByCategories.hasCategoryToExport() || exportByKeywords.isEnabled()) && !warned.getAndSet(true)) {
            LOGGER.log(CONSOLE, ExportByCategoriesConfig.CONFIG_FILE + "/" + ExportByKeywordsConfig.CONFIG_FILE + " configured but {}=false, files won't be exported. Is your configuration OK?", ENABLE_PARAM);
        }

        if (isAutomaticExportEnabled()) {
            caseData.setContainsReport(true);
        }

        HashTaskConfig hashConfig = configurationManager.findObject(HashTaskConfig.class);
        if (hashConfig.isEnabled()) {
            computeHash = true;
        }

        itensExtracted = 0;
        subDirCounter = 0;

        hashMap = (HashMap<IHashValue, IHashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);

    }

    @Override
    public void finish() throws Exception {
        hashMap.clear();
        if (storageCon.get(output) != null) {
            for (Entry<Integer, Connection> entry : storageCon.get(output).entrySet()) {
                Connection con = entry.getValue();
                if (con != null && !con.isClosed() && !con.getAutoCommit()) {
                    con.commit();
                    con.close();
                    LOGGER.info("Closed connection to storage " + entry.getKey());
                }
            }
            storageCon.remove(output);
        }
    }

    public static void commitStorage(File output) throws SQLException {
        if (storageCon.get(output) != null) {
            for (Connection con : storageCon.get(output).values()) {
                if (con != null && !con.isClosed() && !con.getAutoCommit()) {
                    con.commit();
                }
            }
        }
    }

    public static void deleteIgnoredItemData(ICaseData caseData, File output) throws Exception {
        deleteIgnoredItemData(caseData, output, false, null);
    }

    public static void deleteIgnoredItemData(ICaseData caseData, File output, boolean removingEvidence,
            IndexWriter writer) throws Exception {
        if (!removingEvidence && (caseData.isIpedReport() || !caseData.containsReport())) {
            return;
        }
        if (removingEvidence) {
            setExtractLocation(caseData, output);
        }
        try (IPEDSource ipedCase = new IPEDSource(output.getParentFile(), writer)) {
            if (extractDir != null && extractDir.exists()) {
                SortedDocValues sdv = ipedCase.getAtomicReader().getSortedDocValues(IndexItem.ID_IN_SOURCE);
                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "",
                        Messages.getString("ExportFileTask.DeletingData1"));
                Integer deleted = deleteIgnoredSubitemsFromFS(sdv, output.getParentFile().toPath(), extractDir);
                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "",
                        Messages.getString("ExportFileTask.DeletedData1").replace("{}", deleted.toString()));
            }
            if (storage.get(output) != null && !storage.get(output).isEmpty()) {
                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "",
                        Messages.getString("ExportFileTask.DeletingData2"));
                Integer deleted = deleteIgnoredSubitemsFromStorage(ipedCase, output);
                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "",
                        Messages.getString("ExportFileTask.DeletedData2").replace("{}", deleted.toString()));
            }
        }
    }

    private static int deleteIgnoredSubitemsFromFS(SortedDocValues sdv, Path root, File file) throws IOException {
        int deleted = 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File f : files) {
                deleted += deleteIgnoredSubitemsFromFS(sdv, root, f);
            }
        } else {
            String exportPath = root.relativize(file.toPath()).toString();
            if (sdv == null || sdv.lookupTerm(new BytesRef(exportPath)) < 0) {
                if (file.delete()) {
                    deleted++;
                }
            }
        }
        return deleted;
    }
    
    private static int deleteIgnoredSubitemsFromStorage(IPEDSource ipedCase, File output) throws SQLException {
        final AtomicInteger deleted = new AtomicInteger();
        ArrayList<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        // connections were closed in finish(), open them again
        configureSQLiteStorage(output);
        Collections.sort(noContentHashes);
        for (Entry<Integer, Connection> entry : storageCon.get(output).entrySet()) {
            Integer storage = entry.getKey();
            Connection con = entry.getValue();
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    try (PreparedStatement ps = con.prepareStatement(SELECT_IDS_WITH_DATA);
                            PreparedStatement ps2 = con.prepareStatement(CLEAR_DATA);
                            Statement ps3 = con.createStatement()) {
                        LOGGER.info("Deleting data from storage {}", storage);
                        SortedDocValues sdv = ipedCase.getAtomicReader().getSortedDocValues(IndexItem.ID_IN_SOURCE);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            String id = rs.getString(1);
                            if (sdv == null || sdv.lookupTerm(new BytesRef(id)) < 0
                                    || Collections.binarySearch(noContentHashes, new HashValue(id)) >= 0) {
                                ps2.setString(1, id);
                                ps2.executeUpdate();
                                deleted.incrementAndGet();
                            }
                        }
                        con.commit();
                        con.setAutoCommit(true);
                        LOGGER.info("Running VACUUM on storage {}", storage);
                        ps3.executeUpdate("VACUUM");
                        LOGGER.info("Closing storage {}", storage);
                        con.close();
                    } catch (SQLException | IOException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Error deleting data from storage.", e);
            }
        }
        return deleted.intValue();
    }

}
