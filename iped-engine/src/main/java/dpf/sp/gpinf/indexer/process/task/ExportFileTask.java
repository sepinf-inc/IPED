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
package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.zip.Deflater;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Pragma;
import org.sqlite.SQLiteConfig.SynchronousMode;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.parsers.util.ExportFolder;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import dpf.sp.gpinf.indexer.util.SeekableInputStreamFactory;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IHashValue;
import iped3.IItem;
import iped3.exception.ZipBombException;
import iped3.io.SeekableInputStream;
import iped3.sleuthkit.ISleuthKitItem;

/**
 * Responsável por extrair subitens de containers. Também exporta itens ativos
 * em casos de extração automática de dados ou em casos de extração de itens
 * selecionados após análise.
 */
public class ExportFileTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(ExportFileTask.class);
    public static final String EXTRACT_CONFIG = "CategoriesToExport.txt"; //$NON-NLS-1$
    public static final String EXTRACT_DIR = Messages.getString("ExportFileTask.ExportFolder"); //$NON-NLS-1$
    private static final String SUBITEM_DIR = "subitens"; //$NON-NLS-1$

    private static final String STORAGE_PREFIX = "storage";
    public static final String STORAGE_CON_PREFIX = "storageConnection";
    private static final int MAX_BUFFER_SIZE = 1 << 24;
    private static final int SQLITE_CACHE_SIZE = 1 << 24;

    private static final byte DB_SUFFIX_BITS = 4; // current impl maximum is 8

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS t1(id TEXT PRIMARY KEY, data BLOB, thumb BLOB, text TEXT);";

    private static final String INSERT_DATA = "INSERT INTO t1(id, data) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET data=? WHERE data IS NULL;";

    private static final String CHECK_HASH = "SELECT id FROM t1 WHERE id=? AND data IS NOT NULL;";

    private static HashSet<String> categoriesToExtract = new HashSet<String>();
    public static int subDirCounter = 0, itensExtracted = 0;
    private static File subDir;

    private static boolean computeHash = false;
    private File extractDir;
    private HashMap<IHashValue, IHashValue> hashMap;
    private List<String> noContentLabels;

    private static HashMap<File, HashMap<Integer, File>> storage = new HashMap<>();
    private static HashMap<File, HashMap<Integer, Connection>> storageCon = new HashMap<>();

    public ExportFileTask() {
        ExportFolder.setExportPath(EXTRACT_DIR);
    }

    public static synchronized void incItensExtracted() {
        itensExtracted++;
    }

    public static int getItensExtracted() {
        return itensExtracted;
    }

    private void setExtractLocation() {
        if (output != null) {
            if (caseData.containsReport()) {
                this.extractDir = new File(output.getParentFile(), EXTRACT_DIR);
            } else {
                this.extractDir = new File(output, SUBITEM_DIR);
            }
        }
        IPEDConfig ipedConfig = (IPEDConfig) ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator()
                .next();
        if (!caseData.containsReport() || !ipedConfig.isHtmlReportEnabled()) {
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
                    stmt.executeUpdate(CREATE_TABLE);
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

    public static void load(File file) throws FileNotFoundException, IOException {

        String content = Util.readUTF8Content(file);
        for (String line : content.split("\n")) { //$NON-NLS-1$
            if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                continue;
            }
            categoriesToExtract.add(line.trim());
        }
    }

    private static synchronized File getSubDir(File extractDir) {
        if (subDirCounter % 1000 == 0) {
            subDir = new File(extractDir, Integer.toString(subDirCounter / 1000));
        }
        subDirCounter++;
        return subDir;
    }

    public static boolean hasCategoryToExtract() {
        return categoriesToExtract.size() > 0;
    }

    public static boolean isToBeExtracted(IItem evidence) {

        boolean result = false;
        for (String category : evidence.getCategorySet()) {
            if (categoriesToExtract.contains(category)) {
                result = true;
                break;
            }
        }

        return result;
    }

    public void process(IItem evidence) {

        // Exporta arquivo no caso de extração automatica ou no caso de relatório do
        // iped
        if ((caseData.isIpedReport() && evidence.isToAddToCase())
                || (!evidence.isSubItem() && (isToBeExtracted(evidence) || evidence.isToExtract()))) {

            evidence.setToExtract(true);
            if (doNotExport(evidence)) {
                if (evidence instanceof ISleuthKitItem) {
                    ((ISleuthKitItem) evidence).setSleuthId(null);
                }
                evidence.setExportedFile(null);
            } else {
                extract(evidence);
            }

            incItensExtracted();
            copyViewFile(evidence);
        }

        boolean isAutomaticFileExtractionOn = hasCategoryToExtract() || RegexTask.isExtractByKeywordsOn();

        // Renomeia subitem caso deva ser exportado
        if (!caseData.isIpedReport() && evidence.isSubItem()
                && (evidence.isToExtract() || isToBeExtracted(evidence) || !isAutomaticFileExtractionOn)) {

            evidence.setToExtract(true);
            if (!doNotExport(evidence)) {
                renameToHash(evidence);
            } else {
                // just clear path to be indexed, continues to point to file for processing
                evidence.setExportedFile(null);
                evidence.setDeleteFile(true);
            }
            incItensExtracted();
        }

        if ((hasCategoryToExtract() || RegexTask.isExtractByKeywordsOn()) && !evidence.isToExtract()) {
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
                if (label.equalsIgnoreCase(noContentLabel)) {
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
            is = evidence.getBufferedStream();
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
            if (viewFile.equals(destFile)) {
                return;
            }
            destFile.getParentFile().mkdirs();
            try {
                IOUtil.copiaArquivo(viewFile, destFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File getHashFile(String hash, String ext) {
        String path = hash.charAt(0) + "/" + hash.charAt(1) + "/" + Util.getValidFilename(hash + ext); //$NON-NLS-1$ //$NON-NLS-2$
        if (extractDir == null) {
            setExtractLocation();
        }
        return new File(extractDir, path);
    }

    public void renameToHash(IItem evidence) {

        String hash = evidence.getHash();
        if (hash != null && !hash.isEmpty() && evidence.getFile() != null) {
            File file = evidence.getFile();
            String ext = evidence.getType().getLongDescr();
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
                                    evidence.getFileToIndex());
                            e.printStackTrace();
                        }
                    }

                } else {
                    changeTargetFile(evidence, hashFile);
                    if (!file.delete()) {
                        LOGGER.warn("{} Error Deleting {}", Thread.currentThread().getName(), file.getAbsolutePath()); //$NON-NLS-1$
                    }
                }
            }

        }

    }

    private void changeTargetFile(IItem evidence, File file) {
        String relativePath = Util.getRelativePath(output, file);
        evidence.setExportedFile(relativePath);
        evidence.setFile(file);
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
            ext = evidence.getType().getLongDescr();
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
            setExtractLocation();
        }

        if (!computeHash) {
            outputFile = new File(getSubDir(extractDir),
                    Util.getValidFilename(Integer.toString(evidence.getId()) + ext));
        } else if ((hash = evidence.getHash()) != null && !hash.isEmpty()) {
            outputFile = getHashFile(hash, ext);
            IHashValue hashVal = new HashValue(hash);
            synchronized (hashMap) {
                hashLock = hashMap.get(hashVal);
            }

        } else {
            outputFile = new File(extractDir, Util.getValidFilename("0" + Integer.toString(evidence.getId()) + ext)); //$NON-NLS-1$
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
                                fileExists = outputFile.createNewFile();
                                bos = new BufferedOutputStream(new FileOutputStream(outputFile));
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
        byte[] hash = DigestUtils.md5(new ByteArrayInputStream(buf, 0, len));
        int k = getStorageSuffix(hash);
        HashValue md5 = new HashValue(hash);
        boolean alreadyInDB = false;
        try (PreparedStatement ps = storageCon.get(output).get(k).prepareStatement(CHECK_HASH)) {
            ps.setString(1, md5.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                alreadyInDB = true;
            }
        }
        if (!alreadyInDB) {
            try (PreparedStatement ps = storageCon.get(output).get(k).prepareStatement(INSERT_DATA)) {
                ps.setString(1, md5.toString());
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
        evidence.setIdInDataSource(md5.toString());
        evidence.setInputStreamFactory(
                new SQLiteInputStreamFactory(storage.get(output).get(k).toPath(), storageCon.get(output).get(k)));
        evidence.setExportedFile(null);
        evidence.setFile(null);
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
            super(datasource);
        }

        public SQLiteInputStreamFactory(Path datasource, Connection conn) {
            super(datasource);
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
                    conn = getSQLiteStorageCon(getDataSourcePath().toFile());
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
    public void init(Properties confProps, File confDir) throws Exception {
        load(new File(confDir, EXTRACT_CONFIG));

        if (hasCategoryToExtract()) {
            caseData.setContainsReport(true);
        }

        String value = confProps.getProperty("hash"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
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
            int i = 0;
            for (Connection con : storageCon.get(output).values()) {
                if (con != null && !con.isClosed() && !con.getAutoCommit()) {
                    con.commit();
                    con.close();
                    LOGGER.info("Closed connection to storage " + i);
                }
                i++;
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

}
