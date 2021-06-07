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
package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.tika.io.IOUtils;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_TYPE_ENUM;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteException;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.FileSystemConfig;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.task.BaseCarveTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import gpinf.dev.filetypes.GenericFileType;
import iped3.ICaseData;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import iped3.util.BasicProps;

public class SleuthkitReader extends DataSourceReader {

    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitReader.class);

    private static final String RANGE_ID_FILE = "data/SleuthkitIdsPerImage.txt";

    // TODO update @deleteDatasource() when updating TSK
    public static final String MIN_TSK_VER = "4.6.5";

    public static String DB_NAME = "sleuth.db"; //$NON-NLS-1$
    private static String IMG_NAME = "IMG_NAME"; //$NON-NLS-1$
    public static MediaType UNALLOCATED_MIMETYPE = BaseCarveTask.UNALLOCATED_MIMETYPE;
    public static final String IN_FAT_FS = "inFatFs"; //$NON-NLS-1$

    private static boolean tskChecked = false;
    private static boolean isTskPatched = false;

    private static ConcurrentHashMap<File, Long[]> idRangeMap = new ConcurrentHashMap<>();
    private static volatile Thread waitLoadDbThread;
    private static volatile Exception exception = null;

    private static String[] TSK_CMD = { "tsk_loaddb", "-a", "-d", DB_NAME, IMG_NAME }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private CmdLineArgs args;
    private Long firstId, lastId;

    private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();

    // chamada content.getParent() é custosa, entao os valores já mapeados são
    // salvos neste cache
    private ArrayList<Integer> tskParentIds = new ArrayList<>();

    private List<Integer> inheritedParents;
    private String inheritedPath;

    private AddImageProcess addImage;
    private String deviceName;
    private boolean isISO9660 = false;
    private boolean fastmode = false;

    // Referência estática para a JVM não finalizar o objeto que será usado
    // futuramente
    // via referência interna ao JNI para acessar os itens do caso
    public static volatile SleuthkitCase sleuthCase;

    public SleuthkitReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);

        if (Configuration.getInstance().loaddbPathWin != null)
            TSK_CMD[0] = Configuration.getInstance().loaddbPathWin;
    }

    public boolean isSupported(File file) {
        isISO9660 = false;
        if (file.isDirectory())
            return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".000") //$NON-NLS-1$
                || name.endsWith(".001") //$NON-NLS-1$
                || name.endsWith(".e01") //$NON-NLS-1$
                || name.endsWith(".aff") //$NON-NLS-1$
                || name.endsWith(".l01") //$NON-NLS-1$
                || name.endsWith(".dd") //$NON-NLS-1$
                || name.endsWith(".vmdk") //$NON-NLS-1$
                || name.endsWith(".vhd") //$NON-NLS-1$
                || isPhysicalDrive(file) || (isISO9660 = isISO9660(file)) || name.equals(DB_NAME);
    }

    public static boolean isPhysicalDrive(File file) {
        return Util.isPhysicalDrive(file);
    }

    private boolean isISO9660(File file) {

        FileInputStream fis = null;
        try {
            String magicString = "CD001"; //$NON-NLS-1$
            byte[] magic = magicString.getBytes("UTF-8"); //$NON-NLS-1$
            byte[] header = new byte[64 * 1024];
            fis = new FileInputStream(file);

            int read = 0, off = 0;
            while (read != -1 && (off += read) < header.length) {
                read = fis.read(header, off, header.length - off);
            }

            if (matchMagic(magic, header, 32769) || // CDROM sector 2048
                    matchMagic(magic, header, 34817) || // CDROM sector 2048
                    matchMagic(magic, header, 37649) || // CDROM RAW sector 2352
                    matchMagic(magic, header, 37657) || // CDROM RAW XA sector 2352
                    matchMagic(magic, header, 40001) || // CDROM RAW sector 2352
                    matchMagic(magic, header, 40009)) // CDROM RAW XA sector 2352
            {
                return true;
            }

        } catch (Exception e) {

        } finally {
            IOUtil.closeQuietly(fis);
        }
        return false;
    }

    private boolean matchMagic(byte[] magic, byte[] header, int off) {
        for (int i = 0; i < magic.length; i++) {
            if (magic[i] != header[off + i]) {
                return false;
            }
        }
        return true;
    }

    public String currentDirectory() {
        if (addImage != null) {
            return addImage.currentDirectory();
        } else {
            return null;
        }
    }

    private static synchronized void checkTSKVersion() throws Exception {

        if (tskChecked) {
            return;
        }

        String[] TSK_VERSION = { TSK_CMD[0], "-V" }; //$NON-NLS-1$
        ProcessBuilder pb = new ProcessBuilder(TSK_VERSION);
        Process process = pb.start();

        process.getOutputStream().close();
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new Exception("Error testing tsk_loaddb. It returned error code " + process.exitValue()); //$NON-NLS-1$
        }

        InputStreamReader reader = new InputStreamReader(process.getInputStream());
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[1024];
        for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
            out.append(buffer, 0, n);
        }

        reader.close();
        process.getErrorStream().close();

        String[] str = out.toString().split(" "); //$NON-NLS-1$
        String tskVer = str[str.length - 1].trim();
        LOGGER.info("Sleuthkit version " + tskVer + " detected."); //$NON-NLS-1$ //$NON-NLS-2$

        String patchSufix = "-iped-patch";
        if (tskVer.contains(patchSufix)) { // $NON-NLS-1$
            isTskPatched = true;
            tskVer = tskVer.substring(0, tskVer.indexOf(patchSufix));
        } else
            LOGGER.error("It is highly recommended to apply the iped patch (in sources folder) on sleuthkit!"); //$NON-NLS-1$

        if (tskVer.compareTo(MIN_TSK_VER) < 0) // $NON-NLS-1$
            throw new Exception("Sleuthkit version " + tskVer + " not supported. Install version " + MIN_TSK_VER); //$NON-NLS-1$ //$NON-NLS-2$
        else if (tskVer.compareTo(MIN_TSK_VER) > 0) // $NON-NLS-1$
            LOGGER.error("Sleuthkit version " + tskVer + " not tested! It may contain incompatibilities!"); //$NON-NLS-1$ //$NON-NLS-2$

        tskChecked = true;
    }

    @Override
    public void read(File image, Item parent) throws Exception {

        checkTSKVersion();

        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (args.getProfile() != null) {
            if (args.getProfile().equals("fastmode")) //$NON-NLS-1$ //$NON-NLS-2$
                fastmode = true;
        }

        int offset = TimeZone.getDefault().getRawOffset() / 3600000;
        String timezone = "GMT" + (-offset); //$NON-NLS-1$
        if (args.getTimezone() != null) { // $NON-NLS-1$
            timezone = args.getTimezone();
            if (timezone.contains("+")) //$NON-NLS-1$
                timezone = timezone.replace('+', '-');
            else
                timezone = timezone.replace('-', '+');
        }

        int sectorSize = args.getBlocksize();
        String password = args.getPassword();

        firstId = null;
        lastId = null;
        sleuthIdToId.clear();
        tskParentIds.clear();

        deviceName = getEvidenceName(image);
        if (parent == null) {
            dataSource = new DataSource(image);
            dataSource.setName(deviceName);
            inheritedParents = Collections.emptyList();
            inheritedPath = "";
        } else {
            inheritedParents = new ArrayList<>(parent.getParentIds());
            inheritedParents.add(parent.getId());
            inheritedPath = parent.getPath();
        }

        String dbPath = output.getParent() + File.separator + DB_NAME;

        if (listOnly) {

            if (sleuthCase == null) {
                if (new File(dbPath).exists()) {
                    sleuthCase = SleuthkitCase.openCase(dbPath);

                } else {
                    WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                            Messages.getString("SleuthkitReader.Creating") + dbPath); //$NON-NLS-1$
                    LOGGER.info("Creating database {}", dbPath); //$NON-NLS-1$
                    sleuthCase = SleuthkitCase.newCase(dbPath);
                    LOGGER.info("{} database created", dbPath); //$NON-NLS-1$
                }
            }

            FileSystemConfig fsConfig = ConfigurationManager.findObject(FileSystemConfig.class);
            if (fsConfig.isRobustImageReading()) {
                Manager.getInstance().initSleuthkitServers(sleuthCase.getDbDirPath());
            }
            Long[] range = getDecodedRangeId(image);
            if (range != null && args.isContinue()) {
                synchronized (idRangeMap) {
                    idRangeMap.put(image, range);
                    idRangeMap.notify();
                }
            } else if (image.getName().equals(DB_NAME)) {
                firstId = 0L;
                lastId = sleuthCase.getLastObjectId();

                synchronized (idRangeMap) {
                    Long[] ids = { firstId, lastId };
                    idRangeMap.put(image, ids);
                    idRangeMap.notify();
                }
            } else {
                if (args.isContinue()) {
                    deleteDatasource(image);
                }

                WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("SleuthkitReader.WaitDecode") + image.getAbsolutePath()); //$NON-NLS-1$
                LOGGER.info("Decoding image {}", image.getAbsolutePath()); //$NON-NLS-1$

                firstId = sleuthCase.getLastObjectId() + 1;

                synchronized (idRangeMap) {
                    Long[] ids = { firstId, null };
                    idRangeMap.put(image, ids);
                    idRangeMap.notify();
                }

                boolean extraParamsAdded = false;
                ArrayList<String> cmdArray = new ArrayList<>();
                for (String param : TSK_CMD) {
                    if (param.equals(DB_NAME)) {
                        cmdArray.add(dbPath);
                    } else if (param.equals(IMG_NAME)) {
                        cmdArray.add(image.getAbsolutePath());
                    } else {
                        cmdArray.add(param);
                    }
                    if (!extraParamsAdded) {
                        if (isTskPatched) {
                            cmdArray.add("-z"); //$NON-NLS-1$
                            cmdArray.add(timezone);
                        }
                        if (sectorSize > 0) {
                            cmdArray.add("-b"); //$NON-NLS-1$
                            cmdArray.add("" + sectorSize); //$NON-NLS-1$
                        }
                        if (password != null) {
                            cmdArray.add("-K"); //$NON-NLS-1$
                            cmdArray.add(password); // $NON-NLS-1$
                        }
                        extraParamsAdded = true;
                    }
                }

                String[] cmd = cmdArray.toArray(new String[0]);

                if (!isTskPatched) {
                    sleuthCase.acquireExclusiveLock();
                }

                try {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    Process process = pb.start();

                    process.getOutputStream().close();
                    logStream(process.getInputStream(), image.getAbsolutePath());
                    logStream(process.getErrorStream(), image.getAbsolutePath());

                    if (!isTskPatched) {
                        waitProcess(process, image);
                    } else {
                        waitProcessInOtherThread(process, image);
                    }

                } finally {
                    if (!isTskPatched) {
                        sleuthCase.releaseExclusiveLock();
                    }
                }

            }

        }

        java.util.logging.Logger.getLogger("org.sleuthkit").setLevel(java.util.logging.Level.SEVERE); //$NON-NLS-1$

        if (!(listOnly && fastmode)) {
            try {
                readItensAdded(image);

            } catch (Exception e) {
                if (waitLoadDbThread != null)
                    waitLoadDbThread.interrupt();
                throw e;
            }

        } else if (waitLoadDbThread != null)
            waitLoadDbThread.join();

    }
    
    private void deleteDatasource(File image) throws TskCoreException, SQLException {
        Long sourceId = null;
        for(Image img : sleuthCase.getImages()) {
            if(img.getName().equals(image.getName())) {
                sourceId = img.getId();
            }
        }
        if(sourceId != null) {
            String queries[] = {
                    "DELETE FROM tsk_files WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_vs_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_vs_parts WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_image_names WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_image_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_fs_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_file_layout WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_files_path WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM data_source_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_objects WHERE obj_id >= '" + sourceId + "';"
            };
            String dbPath = sleuthCase.getDbDirPath() + File.separator + DB_NAME;
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                for (String query : queries) {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate(query);
                    }
                }
            }
        }
    }

    public int read(File image) throws Exception {
        read(image, null);
        return 0;
    }

    private synchronized void saveDecodedRangeId(File image, Long start, Long last) {
        File file = new File(output, RANGE_ID_FILE);
        UTF8Properties props = new UTF8Properties();
        try {
            if (file.exists()) {
                props.load(file);
            }
            props.setProperty(image.getCanonicalPath() + ":startid", start.toString());
            props.setProperty(image.getCanonicalPath() + ":lastid", last.toString());
            props.store(file);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized Long[] getDecodedRangeId(File image) {
        File file = new File(output, RANGE_ID_FILE);
        if (file.exists()) {
            UTF8Properties props = new UTF8Properties();
            try {
                props.load(file);
                String start = props.getProperty(image.getCanonicalPath() + ":startid");
                String last = props.getProperty(image.getCanonicalPath() + ":lastid");
                if (start != null && last != null) {
                    return new Long[] { Long.valueOf(start.trim()), Long.valueOf(last.trim()) };
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void waitProcess(Process process, File image) {
        int exit = -1;
        try {
            exit = process.waitFor();
            if (exit != 0) {
                LOGGER.error("Sleuthkit LoadDb returned an error {}. Possibly" //$NON-NLS-1$
                        + " many items were not added to the case!", exit); //$NON-NLS-1$
            }

        } catch (InterruptedException ie) {
            process.destroyForcibly();
        }

        LOGGER.info("Image decoded: {}", image.getAbsolutePath()); //$NON-NLS-1$

        Long lastId = null;
        try {
            lastId = sleuthCase.getLastObjectId();
        } catch (TskCoreException e) {
            exception = e;
        }

        if (exit == 0 && lastId != null) {
            saveDecodedRangeId(image, firstId, lastId);
        }

        synchronized (idRangeMap) {
            Long[] ids = { firstId, lastId };
            idRangeMap.put(image, ids);
            idRangeMap.notify();
        }
    }

    private void waitProcessInOtherThread(final Process process, final File image) {

        waitLoadDbThread = new Thread() {
            public void run() {
                waitProcess(process, image);
            }
        };
        waitLoadDbThread.start();
    }

    /**
     * Processa os itens em grupos, conforme vão sendo adicionados ao sqlite pelo
     * loadDb, caso seja utilizado loaddb com patch de concorrência
     */
    private void readItensAdded(File file) throws Exception {

        synchronized (idRangeMap) {
            while ((idRangeMap.get(file)) == null) {
                idRangeMap.wait();
            }
            firstId = idRangeMap.get(file)[0];
        }

        Long endId, startId = firstId;
        do {
            endId = sleuthCase.getLastObjectId();

            if (lastId == null) {
                lastId = idRangeMap.get(file)[1];
                if (exception != null) {
                    throw exception;
                }
            }
            if (lastId != null) {
                endId = lastId;
            }

            if (startId > endId) {
                Thread.sleep(1000);
                continue;
            }
            if (endId - startId > 100000) {
                endId = startId + 100000;
            }
            addItems(startId, endId);
            startId = endId + 1;

        } while (!endId.equals(lastId));
    }

    private void addItems(long start, long last) throws Exception {

        if (!listOnly) {
            long[] ids = new long[(int) (last - start + 1)];
            for (int k = 0; k < ids.length; k++)
                ids[k] = start + k;
            cacheTskParentIds(ids);
        }

        TreeSet<Integer> idSet = new TreeSet<>();
        StringBuilder where = new StringBuilder();
        where.append("obj_id IN (");
        for (long id = start; id <= last; id++) {
            idSet.add((int) id);
            where.append(id);
            if (id < last)
                where.append(",");
        }
        where.append(") ORDER BY obj_id;");

        List<AbstractFile> absFiles = findFilesWhere(where.toString());
        where = null;

        idSet.removeAll(absFiles.stream().map(a -> (int) a.getId()).collect(Collectors.toSet()));

        for (int id : idSet) {
            Content content = getContentById(id);
            if (content == null) {
                continue;
            }
            addContent(content);
        }

        for (AbstractFile absFile : absFiles) {
            addContent(absFile);
        }
    }

    private void addContent(Content content) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        IItem item = addContentAndGetItem(content);
        if (item != null) {
            while (content.getId() - firstId >= sleuthIdToId.size()) {
                sleuthIdToId.add(null);
            }
            sleuthIdToId.set((int) (content.getId() - firstId), item.getId());
        }
    }

    private static final int SQLITE_BUSY_ERROR = 5;

    private List<AbstractFile> findFilesWhere(String where) throws TskCoreException, InterruptedException {
        long start = System.currentTimeMillis() / 1000;
        while (true) {
            try {
                return sleuthCase.findAllFilesWhere(where);

            } catch (TskCoreException e) {
                handleTskCoreException(e, start);
            }
        }
    }

    private Content getContentById(long id) throws TskCoreException, InterruptedException {
        long start = System.currentTimeMillis() / 1000;
        while (true) {
            try {
                return sleuthCase.getContentById(id);

            } catch (TskCoreException e) {
                handleTskCoreException(e, start);
            }
        }
    }

    private void handleTskCoreException(TskCoreException e, long start) throws TskCoreException, InterruptedException {
        if (e.getCause() instanceof SQLiteException) {
            long now = System.currentTimeMillis() / 1000;
            int errorCode = ((SQLiteException) e.getCause()).getErrorCode();
            LOGGER.warn(
                    "SQLite error " + errorCode + " after " + (now - start) + "s reading sleuth.db, trying again...");
            if (now - start > 3600)
                throw new RuntimeException("Timeout after 1h retrying!", e); //$NON-NLS-1$
            Thread.sleep(1000);
        } else
            throw e;
    }

    private Integer getTskParentId(long id) throws TskCoreException, SQLException {
        Integer parent = tskParentIds.get((int) id);
        if (parent == null)
            throw new IPEDException("No parent found for tsk objectId " + id);
        return parent;
    }

    private void cacheTskParentIds(long[] ids) throws TskCoreException, SQLException {
        StringBuilder query = new StringBuilder("SELECT obj_id, par_obj_id  FROM tsk_objects WHERE obj_id in ("); //$NON-NLS-1$
        for (int i = 0; i < ids.length - 1; i++) {
            query.append(ids[i]);
            query.append(","); //$NON-NLS-1$
        }
        query.append(ids[ids.length - 1] + ")"); //$NON-NLS-1$
        CaseDbQuery dbQuery = sleuthCase.executeQuery(query.toString());
        ResultSet rs = dbQuery.getResultSet();
        try {
            while (rs.next()) {
                long objId = rs.getLong(1);
                long parId = rs.getLong(2);
                while (objId >= tskParentIds.size())
                    tskParentIds.add(null);

                tskParentIds.set((int) objId, (int) parId);
                if (parId > objId) {
                    throw new IPEDException(
                            "Sleuthkit parentId greater then objectId, please report this unexpected behaviour to iped project.");
                }
            }
        } finally {
            dbQuery.close();
        }
    }

    private IItem addContentAndGetItem(Content content) throws Exception {

        AbstractFile absFile = null;
        if (content instanceof AbstractFile) {
            absFile = (AbstractFile) content;
        }

        if (content != null && absFile == null) {
            return addEvidenceFile(content);
        }

        FileSystemConfig fsConfig = ConfigurationManager.findObject(FileSystemConfig.class);
        if (absFile != null && (absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {

            if (!fsConfig.isToAddUnallocated()) {
                return null;
            }

            // Contorna problema de primeiro acesso ao espaço não alocado na thread de
            // processamento caso haja lock de escrita no sqlite ao adicionar outra evidênca
            absFile.getRanges();

            AdvancedIPEDConfig advancedConfig = ConfigurationManager.findObject(AdvancedIPEDConfig.class);
            long fragSize = fsConfig.getUnallocatedFragSize();
            int fragNum = 0;
            for (long offset = 0; offset < absFile.getSize(); offset += fragSize) {
                long len = offset + fragSize < absFile.getSize() ? fragSize : absFile.getSize() - offset;
                Item frag = new Item();
                String sufix = ""; //$NON-NLS-1$
                if (absFile.getSize() > fragSize) {
                    sufix = "-Frag" + fragNum++; //$NON-NLS-1$
                    frag.setFileOffset(offset);
                }
                frag.setName(absFile.getName() + sufix);
                frag.setLength(len);
                if (len >= advancedConfig.getMinItemSizeToFragment())
                    frag.setHash(""); //$NON-NLS-1$

                setPath(frag, absFile.getUniquePath() + sufix);

                frag.setMediaType(UNALLOCATED_MIMETYPE);
                addItem(absFile, frag, true);
            }

            return null;

        }

        if (absFile == null || absFile.getName().startsWith("$BadClus:$Bad")
                || absFile.getName().equals("$UPCASE_TABLE")) {
            return null;
        }

        if (!fsConfig.isToAddFileSlacks() && absFile.getType() == TSK_DB_FILES_TYPE_ENUM.SLACK
                && !isVolumeShadowCopy(absFile)) {
            return null;
        }

        return addEvidenceFile(absFile);

    }

    private boolean isVolumeShadowCopy(AbstractFile absFile) {
        return absFile.getName().toLowerCase().contains("{3808876b-c176-4e48-b7ae-04046e6cc752}"); //$NON-NLS-1$
    }

    private void setPath(IItem evidence, String path) {
        if (deviceName != null) {
            path = path.replaceFirst("img_.+?\\/", deviceName + "/"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        path = inheritedPath + path;
        evidence.setPath(path);
    }

    private IItem addEvidenceFile(AbstractFile absFile) throws Exception {
        return addItem(absFile, null, false);
    }

    private IItem addItem(AbstractFile absFile, IItem evidence, boolean unalloc) throws Exception {

        if (absFile.isDir() && (absFile.getName().equals(".") || absFile.getName().equals(".."))) { //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }

        if (absFile.getUniquePath().contains("/$OrphanFiles/")) { //$NON-NLS-1$
            if (absFile instanceof FsContent) {
                FileSystem fs = ((FsContent) absFile).getFileSystem();
                // ignore orphans greater than its FS
                if (absFile.getSize() > fs.getSize())
                    return null;
                // ignore orphans greater than its partition
                if (fs.getParent() != null && fs.getParent().getSize() < absFile.getSize())
                    return null;
            }

            FileSystemConfig fsConfig = ConfigurationManager.findObject(FileSystemConfig.class);
            if (fsConfig.getMinOrphanSizeToIgnore() != -1 && absFile.getSize() >= fsConfig.getMinOrphanSizeToIgnore())
                return null;
        }

        if (evidence == null) {
            evidence = new Item();
            evidence.setLength(absFile.getSize());
        }

        if (listOnly || fastmode) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(evidence.getLength());
            if (listOnly)
                return null;
        }

        evidence.setDataSource(dataSource);

        if (evidence.getName() == null) {
            if (absFile.isRoot() && absFile.getName().isEmpty()) {
                evidence.setName("/"); //$NON-NLS-1$
            } else {
                evidence.setName(absFile.getName());
            }

            setPath(evidence, absFile.getUniquePath());
        }

        if (absFile.isDir()) {
            evidence.setIsDir(true);
        }

        evidence.setHasChildren(absFile.hasChildren());
        if (evidence instanceof ISleuthKitItem) {
            ((ISleuthKitItem) evidence).setSleuthFile(absFile);
            ((ISleuthKitItem) evidence).setSleuthId((int) absFile.getId());
        }

        boolean first = true;
        Integer tskId = (int) absFile.getId();
        while ((tskId = getTskParentId(tskId)) != 0) {
            Integer parentId = sleuthIdToId.get(tskId - firstId.intValue());
            if (first) {
                evidence.setParentId(parentId);
                evidence.setParentIdInDataSource(String.valueOf(tskId));
                first = false;
            }
            evidence.addParentId(parentId);
        }
        evidence.addParentIds(inheritedParents);

        if (unalloc || absFile.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)
                || absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.UNALLOC)
                || absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.UNUSED)
                || absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.ORPHAN)) {
            evidence.setDeleted(true);
        }

        if (absFile instanceof SlackFile && !isVolumeShadowCopy(absFile)) {
            evidence.setMediaType(MediaType.application("x-fileslack")); //$NON-NLS-1$
            evidence.setType(new GenericFileType("slack")); //$NON-NLS-1$
            evidence.setDeleted(true);
        }

        long time = absFile.getAtime();
        if (time != 0) {
            evidence.setAccessDate(new Date(time * 1000));
        }
        if (isFATFile(absFile))
            evidence.setExtraAttribute(IN_FAT_FS, true);

        time = absFile.getMtime();
        if (time != 0) {
            evidence.setModificationDate(new Date(time * 1000));
        }
        time = absFile.getCrtime();
        if (time != 0) {
            evidence.setCreationDate(new Date(time * 1000));
        }
        time = absFile.getCtime();
        if (time != 0)
            evidence.setRecordDate(new Date(time * 1000));

        if (absFile.getMetaAddr() != 0) {
            evidence.setExtraAttribute(BasicProps.META_ADDRESS, Long.toString(absFile.getMetaAddr()));
        }

        if (absFile.getMetaSeq() != 0) {
            evidence.setExtraAttribute(BasicProps.MFT_SEQUENCE, Long.toString(absFile.getMetaSeq()));
        }
        
        long fileSystemId = getFileSystemId(absFile);
        if (fileSystemId != -1) {
            evidence.setExtraAttribute(BasicProps.FILESYSTEM_ID, Long.toString(fileSystemId));
        }

        caseData.addItem(evidence);

        return evidence;
    }

    private long getFileSystemId(Content content) {
        if (content instanceof FsContent) {
            try {
                return ((FsContent) content).getFileSystem().getId();
            } catch (TskCoreException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    private IItem addEvidenceFile(Content content) throws Exception {

        if (listOnly || fastmode) {
            caseData.incDiscoveredEvidences(1);
            if (listOnly)
                return null;
        }

        Item evidence = new Item();
        evidence.setLength(content.getSize());
        evidence.setSumVolume(false);

        evidence.setDataSource(dataSource);

        if (content.getName().isEmpty()) {
            if (content instanceof VolumeSystem) {
                evidence.setName(((VolumeSystem) content).getType().getName() + "_Partition_Table"); //$NON-NLS-1$

            } else if (content instanceof FileSystem) {
                String fsName = ((FileSystem) content).getFsType().name();
                fsName = fsName.replace("TSK_FS_TYPE_", ""); //$NON-NLS-1$ //$NON-NLS-2$
                evidence.setName(fsName);
            }

        } else {
            if (deviceName != null && content instanceof Image) {
                evidence.setName(deviceName);
            } else if (content instanceof Volume) {
                long lenGB = content.getSize() >> 30;
                String lenStr = lenGB + "GB"; //$NON-NLS-1$
                if (lenGB == 0) {
                    lenStr = (content.getSize() >> 20) + "MB"; //$NON-NLS-1$
                }

                evidence.setName(content.getName() + " [" + ((Volume) content).getDescription() + "] (" + lenStr + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            } else {
                evidence.setName(content.getName());
            }
        }

        // evidence.setType(new UnknownFileType(evidence.getExt()));
        String path = content.getUniquePath();
        if (deviceName != null) {
            if (path.indexOf('/', 1) == -1) {
                evidence.setPath(inheritedPath + "/" + deviceName); //$NON-NLS-1$
            } else {
                setPath(evidence, path);
            }
        } else {
            evidence.setPath(inheritedPath + path);
        }

        if (content instanceof Image) {
            evidence.setRoot(true);
            if (isISO9660) {
                evidence.setMediaType(MediaType.application("x-iso9660-image")); //$NON-NLS-1$
            } else {
                evidence.setMediaType(MediaType.application("x-disk-image")); //$NON-NLS-1$
            }
        } else {
            evidence.setIsDir(true);
        }

        evidence.setHasChildren(content.hasChildren());

        // evidence.setSleuthFile(content);
        evidence.setHash(""); //$NON-NLS-1$
        evidence.setSleuthId((int) content.getId());

        boolean first = true;
        Integer tskId = (int) content.getId();
        while ((tskId = getTskParentId(tskId)) != 0) {
            Integer parentId = sleuthIdToId.get(tskId - firstId.intValue());
            if (first) {
                evidence.setParentId(parentId);
                evidence.setParentIdInDataSource(String.valueOf(tskId));
                first = false;
            }
            evidence.addParentId(parentId);
        }
        evidence.addParentIds(inheritedParents);

        caseData.addItem(evidence);

        return evidence;
    }

    private boolean isFATFile(Content content) {
        if (content instanceof FsContent) {
            try {
                TSK_FS_TYPE_ENUM fsType = ((FsContent) content).getFileSystem().getFsType();
                if (fsType == TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12 || fsType == TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16
                        || fsType == TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32)
                    return true;

            } catch (TskCoreException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void logStream(final InputStream stream, final String image) {
        new Thread() {
            @Override
            public void run() {
                Reader reader = new InputStreamReader(stream);
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[1024];
                try {
                    for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                        out.append(buffer, 0, n);
                    }

                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    IOUtils.closeQuietly(stream);
                    String msg = out.toString().trim();
                    for (String line : msg.split("\n")) { //$NON-NLS-1$
                        if (!line.trim().isEmpty()) {
                            if (line.toLowerCase().contains("error") //$NON-NLS-1$
                                    && !line.toLowerCase().contains("microsoft reserved partition")) { //$NON-NLS-1$
                                LOGGER.error("Sleuthkit: " + line.trim()); //$NON-NLS-1$
                            } else {
                                LOGGER.info("Sleuthkit: " + line.trim()); //$NON-NLS-1$
                            }
                        }
                    }
                }
                return;
            }
        }.start();
    }

}
