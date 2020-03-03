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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Pragma;

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
    
    private static final String CREATE_TABLE = 
    		"CREATE TABLE IF NOT EXISTS t1(id TEXT, data BLOB, thumb BLOB, text TEXT);";
    
    private static final String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS idx1 ON t1(id);";
    
    private static final String INSERT_DATA = 
    		"INSERT INTO t1(id, data) VALUES(?,?);";

    private static final int MAX_SUBITEM_COMPRESSION = 100;
    private static final int ZIPBOMB_MIN_SIZE = 10 * 1024 * 1024;

    private static HashSet<String> categoriesToExtract = new HashSet<String>();
    public static int subDirCounter = 0, itensExtracted = 0;
    private static File subDir;

    private static boolean computeHash = false;
    private File extractDir;
    private static HashMap<HashValue, HashValue> insertedMD5 = new HashMap<>();
    private static HashValue insertedHash = new HashValue(new byte[16]);
    private HashMap<IHashValue, IHashValue> hashMap;
    private List<String> noContentLabels;
    
    private File storage;
    private Connection storageCon;
    private PreparedStatement ps;

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
            IPEDConfig ipedConfig = (IPEDConfig)ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator().next();
            if(!caseData.containsReport() || !ipedConfig.isHtmlReportEnabled()) {
            	configureSQLiteStorage();
            }
        }
    }
    
    private void configureSQLiteStorage() {
    	String connectionName = STORAGE_PREFIX + this.worker.id + ".db";
        storage = new File(output, STORAGE_PREFIX + File.separator + "storage.db");
        storage.getParentFile().mkdir();
        storageCon = (Connection)caseData.getCaseObject(STORAGE_CON_PREFIX + connectionName);
    	if(storageCon == null) {
    	    try {
    	        storageCon = getSQLiteConnection(storage);
                try(Statement stmt = storageCon.createStatement()){
                    stmt.executeUpdate(CREATE_TABLE);
                }
                try(Statement stmt = storageCon.createStatement()){
                    stmt.executeUpdate(CREATE_INDEX);
                }
                caseData.putCaseObject(STORAGE_CON_PREFIX + connectionName, storageCon);
                
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
    	}
    	ps = (PreparedStatement)caseData.getCaseObject("prepareStatement" + connectionName);
    	if(ps == null) {
    	    try {
                ps = storageCon.prepareStatement(INSERT_DATA);
                caseData.putCaseObject("prepareStatement" + connectionName, ps);
                
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
    	}
    }
    
    private static Connection getSQLiteConnection(File storage) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setPragma(Pragma.SYNCHRONOUS, "0");
        config.setPragma(Pragma.JOURNAL_MODE, "TRUNCATE");
        config.setPragma(Pragma.CACHE_SIZE, "-" + SQLITE_CACHE_SIZE / 1024);
        config.setBusyTimeout(3600000);
        Connection conn = config.createConnection("jdbc:sqlite:" + storage.getAbsolutePath());
        //conn.setAutoCommit(false);
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
            if (!subDir.exists()) {
                subDir.mkdirs();
            }
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

        // Renomeia subitem caso deva ser exportado
        if (!caseData.isIpedReport() && evidence.isSubItem() && (evidence.isToExtract() || isToBeExtracted(evidence)
                || !(hasCategoryToExtract() || RegexTask.isExtractByKeywordsOn()))) {

            evidence.setToExtract(true);
            if (!doNotExport(evidence)) {
                renameToHash(evidence);
            } else {
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
            evidence.setFileOffset(-1);

        } catch (IOException e) {
            LOGGER.warn("{} Error exporting {} \t{}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                    e.toString());

        } finally {
            IOUtil.closeQuietly(is);
        }
    }

    private void copyViewFile(IItem evidence) {
        File viewFile = evidence.getViewFile();
        if (viewFile != null) {
            String viewName = viewFile.getName();
            File destFile = new File(output, "view/" + viewName.charAt(0) + "/" + viewName.charAt(1) + "/" + viewName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
        File result = new File(extractDir, path);
        File parent = result.getParentFile();
        if (!parent.exists()) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public void renameToHash(IItem evidence) {

        String hash = evidence.getHash();
        if (hash != null && !hash.isEmpty() && evidence.getFile() != null) {
            File file = evidence.getFile();
            String ext = evidence.getType().getLongDescr();
            if (!ext.isEmpty()) {
                ext = "." + ext; //$NON-NLS-1$
            }
            ext = Util.removeNonLatin1Chars(ext);

            File hashFile = getHashFile(hash, ext);

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
        file.setReadOnly();
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
            ext = "." + ext; //$NON-NLS-1$
        }

        ext = Util.removeNonLatin1Chars(ext);

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
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
        }

        boolean fileExists = false;
        
        synchronized (hashLock) {
            if (hash == null || !(fileExists = outputFile.exists())) {
                BufferedOutputStream bos = null;
                try {
                    BufferedInputStream bis = new BufferedInputStream(inputStream);
                    byte[] buf = new byte[MAX_BUFFER_SIZE];
                    long total = 0;
                    int i = 0;
                    while(i != -1 && !Thread.currentThread().isInterrupted()) {
                    	int len = 0;
                    	while (len < buf.length && (i = bis.read(buf, len, buf.length - len)) != -1) {
                        	len += i;
                    	}
                        if(i == -1 && storageCon != null && total == 0) {
                        	if(len == 0) {
                        		evidence.setLength(0L);
                        		return;
                        	}
                        	insertIntoStorage(evidence, buf, len);
                            return;
                        }
                        if(bos == null) {
                            fileExists = outputFile.createNewFile();
                            bos = new BufferedOutputStream(new FileOutputStream(outputFile));
                        }
                        bos.write(buf, 0, len);
                        total += len;
                        if (parentSize != null && total >= ZIPBOMB_MIN_SIZE
                                && total > parentSize * MAX_SUBITEM_COMPRESSION)
                            throw new IOException("Potential zip bomb while extracting subitem!"); //$NON-NLS-1$
                    }

                //must catch generic Exception because of Runtime exceptions while extracting corrupted subitems
                } catch (Exception e) {
                    if (e instanceof IOException && IOUtil.isDiskFull((IOException)e))
                        LOGGER.error("Error exporting {}\t{}", evidence.getPath(), "No space left on output disk!"); //$NON-NLS-1$ //$NON-NLS-2$
                    else
                        LOGGER.warn("Error exporting {}\t{}", evidence.getPath(), e.toString()); //$NON-NLS-1$
                    
                } finally {
                    if (bos != null) {
                        bos.close();
                    }
                }
            }
        }

        if(fileExists) {
            changeTargetFile(evidence, outputFile);
            if (evidence.isSubItem()) {
                evidence.setLength(outputFile.length());
            }
        }

    }
    
    private void insertIntoStorage(IItem evidence, byte[] buf, int len) throws InterruptedException, IOException, SQLException, CompressorException {
        HashValue md5 = new HashValue(DigestUtils.md5(new ByteArrayInputStream(buf, 0, len)));
        HashValue prev;
        boolean first = false, inserted = false;
        synchronized(insertedMD5) {
            prev = insertedMD5.putIfAbsent(md5, md5);
        }
        if(prev == null) {
            first = true;
        }else if(!prev.equals(insertedHash)) {
            md5 = prev;
        }else {
            inserted = true;
        }
        if(!inserted) {
            synchronized(md5) {
                if(!first) {
                    boolean wait; 
                    synchronized(insertedMD5) {
                        wait = !insertedMD5.get(md5).equals(insertedHash);
                    }
                    if(wait) {
                        md5.wait();
                    }
                }else {
                    ps.setString(1, md5.toString());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    CompressorOutputStream gzippedOut = new CompressorStreamFactory()
                            .createCompressorOutputStream(CompressorStreamFactory.GZIP, baos);
                    gzippedOut.write(buf, 0, len);
                    gzippedOut.close();
                    buf = null;
                    ps.setBytes(2, baos.toByteArray());
                    baos = null;
                    ps.executeUpdate();
                    synchronized(insertedMD5) {
                        insertedMD5.put(md5, insertedHash);
                    }
                    md5.notifyAll();
                }
            }
        }
        evidence.setIdInDataSource(md5.toString());
        evidence.setInputStreamFactory(new SQLiteInputStreamFactory(storage.toPath(), storageCon));
        evidence.setLength((long)len);
    }
    
    public static class SQLiteInputStreamFactory extends SeekableInputStreamFactory{
    	
    	private static final String SELECT_DATA = "SELECT data FROM t1 WHERE id=?;";
    	
    	private Connection conn;
    	private PreparedStatement ps;
    	
    	public SQLiteInputStreamFactory(Path datasource) {
    		super(datasource);
    	}
    	
    	public SQLiteInputStreamFactory(Path datasource, Connection conn) {
    		super(datasource);
    		this.conn = conn;
    	}

		@Override
		public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
			ResultSet rs = null;
			try{
				byte[] bytes = null;
				if(conn == null) {
                    conn = getSQLiteConnection(getDataSourcePath().toFile());
                }
				if(ps == null) {
				    ps = conn.prepareStatement(SELECT_DATA);
				}
                ps.setString(1, identifier);
                rs = ps.executeQuery();
                if(rs.next()) {
                    bytes = rs.getBytes(1);
                }
				InputStream gzippedIn = new CompressorStreamFactory()
            		    .createCompressorInputStream(CompressorStreamFactory.GZIP, new ByteArrayInputStream(bytes));
				bytes = IOUtils.toByteArray(gzippedIn);
				gzippedIn.close();
				return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
				
			} catch (Exception e) {
				throw new IOException(e);
			}finally {
				try {
					if(rs != null)
						rs.close();
				}catch (Exception e) {
					//ignore
				}
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
        if(ps != null && !ps.isClosed()) {
            ps.close();
        }
        if(storageCon != null && !storageCon.isClosed()) {
        	//storageCon.commit();
        	storageCon.close();
        }
    }

}
