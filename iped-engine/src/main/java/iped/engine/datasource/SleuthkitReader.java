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
package iped.engine.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_TYPE_ENUM;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteException;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.config.SplitLargeBinaryConfig;
import iped.engine.core.Manager;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.engine.localization.Messages;
import iped.engine.sleuthkit.SleuthkitClient;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.UIPropertyListenerProvider;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.properties.BasicProps;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;
import iped.utils.UTF8Properties;

public class SleuthkitReader extends DataSourceReader {

    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitReader.class);

    private static final String RANGE_ID_FILE = "data/SleuthkitIdsPerImage.txt";
    private static final String PASSWORD_PER_IMAGE = "data/PasswordPerImage.txt";

    // TODO update @deleteDatasource() when updating TSK
    public static final String MIN_TSK_VER_TESTED = "4.11.0";
    public static final String MAX_TSK_VER_TESTED = "4.12.0";

    public static String DB_NAME = "sleuth.db"; //$NON-NLS-1$
    public static MediaType UNALLOCATED_MIMETYPE = BaseCarveTask.UNALLOCATED_MIMETYPE;
    public static final String IN_FAT_FS = "inFatFs"; //$NON-NLS-1$

    private static boolean tskChecked = false;
    private static boolean isTskPatched = false;
    
    private static ExecutorService executor = Executors.newFixedThreadPool(1);

    private static ConcurrentHashMap<File, Long[]> idRangeMap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<File, Future<Void>> addImageFuture = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<File, Exception> exception = new ConcurrentHashMap<>();

    // this guarantees just one producer populates the DB at a time (e.g. when
    // decoding embedded disks recursively)
    private static Semaphore decodeImageSemaphore = new Semaphore(1);

    private CmdLineArgs args;
    private Long firstId, lastId;

    private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();

    // chamada content.getParent() é custosa, entao os valores já mapeados são
    // salvos neste cache
    private ArrayList<Integer> tskParentIds = new ArrayList<>();

    private List<Integer> inheritedParents;
    private String inheritedPath;
    private IItem parent;

    private AddImageProcess addImage;
    private String deviceName;
    private boolean isISO9660 = false;
    private boolean fastmode = false;
    private boolean embeddedDisk = false;
    private int itemCount = 0;
    private volatile boolean decodingError = false;

    private HashMap<Integer, String> idTotrackIDMap = new HashMap<>();

    // Referência estática para a JVM não finalizar o objeto que será usado
    // futuramente
    // via referência interna ao JNI para acessar os itens do caso
    public static volatile SleuthkitCase sleuthCase;

    public static File getSleuthkitDB(File output) {
        return new File(output.getParent(), DB_NAME);
    }

    public SleuthkitReader(boolean embeddedDisk, ICaseData caseData, File output) {
        this(caseData, output, false);
        this.embeddedDisk = embeddedDisk;
    }

    public SleuthkitReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    public int getItemCount() {
        return itemCount;
    }

    public boolean hasDecodingError() {
        return this.decodingError;
    }

    public boolean isSupported(File file) {
        isISO9660 = false;
        if (file.isDirectory())
            return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".000") //$NON-NLS-1$
                || name.endsWith(".001") //$NON-NLS-1$
                || name.endsWith(".e01") //$NON-NLS-1$
                || name.endsWith(".ex01") //$NON-NLS-1$
                || name.endsWith(".aff") //$NON-NLS-1$
                || name.endsWith(".l01") //$NON-NLS-1$
                || name.endsWith(".lx01") //$NON-NLS-1$
                || name.endsWith(".dd") //$NON-NLS-1$
                || name.endsWith(".vmdk") //$NON-NLS-1$
                || name.endsWith(".vhd") //$NON-NLS-1$
                || name.endsWith(".vhdx") //$NON-NLS-1$
                || isPhysicalDrive(file) || (isISO9660 = isISO9660(file)) || name.equals(DB_NAME);
    }

    public static boolean isPhysicalDrive(File file) {
        return Util.isPhysicalDrive(file);
    }

    private MediaType getMediaType(String ext) {
        if (isISO9660) {
            return MediaTypes.ISO_IMAGE;
        } else {
            switch (ext) {
                case "e01":
                    return MediaTypes.E01_IMAGE;
                case "ex01":
                    return MediaTypes.EX01_IMAGE;
                case "vmdk":
                    return MediaTypes.VMDK;
                case "vhd":
                    return MediaTypes.VHD;
                case "vhdx":
                    return MediaTypes.VHDX;
                default:
                    return MediaTypes.RAW_IMAGE;
            }
        }
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

        String tskVer = SleuthkitJNI.getVersion();
        LOGGER.info("Sleuthkit version " + tskVer + " detected."); //$NON-NLS-1$ //$NON-NLS-2$

        String patchSufix = "-iped-patch";
        if (tskVer.contains(patchSufix)) { // $NON-NLS-1$
            isTskPatched = true;
            tskVer = tskVer.substring(0, tskVer.indexOf(patchSufix));
        } else {
            LOGGER.error("We recommend to apply the iped patch on sleuthkit, see https://github.com/sepinf-inc/IPED/wiki/Linux#the-sleuthkit"); //$NON-NLS-1$
        }

        String[] minVerParts = MIN_TSK_VER_TESTED.split("\\.");
        String[] currVerParts = tskVer.split("\\.");

        int majorVerExpected = Integer.valueOf(minVerParts[0]);
        int majorVerFound = Integer.valueOf(currVerParts[0]);
        int minorVerExpected = Integer.valueOf(minVerParts[1]);
        int minorVerFound = Integer.valueOf(currVerParts[1]);
        int maxMinorVerTested = Integer.valueOf(MAX_TSK_VER_TESTED.split("\\.")[1]);

        if (majorVerExpected != majorVerFound || minorVerFound < minorVerExpected)
            throw new Exception("Sleuthkit version " + tskVer + " not supported. Install version " + MIN_TSK_VER_TESTED); //$NON-NLS-1$ //$NON-NLS-2$
        if (minorVerFound > maxMinorVerTested)
            LOGGER.error("Sleuthkit version " + tskVer + " not tested! It may contain incompatibilities!"); //$NON-NLS-1$ //$NON-NLS-2$

        tskChecked = true;
    }
    
    public static synchronized boolean isTSKPatched() throws Exception {
        if (!tskChecked) {
            checkTSKVersion();
        }
        return isTskPatched;
    }

    @Override
    public void read(File image, Item parent) throws Exception {

        checkTSKVersion();

        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (args.getProfile() != null) {
            if (args.getProfile().equals("fastmode")) //$NON-NLS-1$ //$NON-NLS-2$
                fastmode = true;
        }

        String timezone = TimeZone.getDefault().getID();
        if (args.getTimezone() != null) {
            timezone = args.getTimezone();
        }

        int sectorSize = args.getBlocksize();

        String password = getEvidencePassword(image);
        if (password != null) {
            setImagePassword(output, image.getName(), password);
        }

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
            this.parent = parent;
            dataSource = parent.getDataSource();
            inheritedParents = new ArrayList<>(parent.getParentIds());
            inheritedParents.add(parent.getId());
            inheritedPath = parent.getPath();
            if (embeddedDisk) {
                deviceName = parent.getName();
                inheritedPath = Util.getParentPath(parent);
                idTotrackIDMap.put(parent.getId(), (String) parent.getExtraAttribute(IndexItem.TRACK_ID));
            }
        }

        String dbPath = getSleuthkitDB(output).getAbsolutePath();

        if (listOnly || embeddedDisk) {

            Properties sysProps = System.getProperties();

            if (sleuthCase == null) {
                synchronized (this.getClass()) {
                    if (sleuthCase == null) {
                        if (new File(dbPath).exists()) {
                            sleuthCase = SleuthkitInputStreamFactory.openSleuthkitCase(dbPath);

                        } else {
                            UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                                    Messages.getString("SleuthkitReader.Creating") + dbPath); //$NON-NLS-1$
                            LOGGER.info("Creating database {}", dbPath); //$NON-NLS-1$
                            sleuthCase = SleuthkitCase.newCase(dbPath);
                            LOGGER.info("{} database created", dbPath); //$NON-NLS-1$
                        }
                    }
                }
            }

            // workaround for https://github.com/sepinf-inc/IPED/issues/1176
            for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
                sysProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
            System.setProperties(sysProps);

            SleuthkitClient.initSleuthkitServers(new File(dbPath));

            Long[] range = getDecodedRangeId(image, output);
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

                // get permit before any DB changes and getting last obj ID
                decodeImageSemaphore.acquire();

                if (args.isContinue() && !embeddedDisk) {
                    Long tskID = deleteDatasource(image);
                    if (tskID != null) {
                        removeDecodedRangeAfterId(tskID, output);
                    }
                }

                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("SleuthkitReader.WaitDecode") + image.getAbsolutePath()); //$NON-NLS-1$
                LOGGER.info("Decoding image {}", image.getAbsolutePath()); //$NON-NLS-1$

                firstId = sleuthCase.getLastObjectId() + 1;

                synchronized (idRangeMap) {
                    Long[] ids = { firstId, null };
                    idRangeMap.put(image, ids);
                    idRangeMap.notify();
                }

                addImage = sleuthCase.makeAddImageProcess(timezone, true, false, "");
                addImageInBackground(addImage, image, sectorSize);

            }

        }

        java.util.logging.Logger.getLogger("org.sleuthkit").setLevel(java.util.logging.Level.SEVERE); //$NON-NLS-1$

        if (!(listOnly && fastmode) || embeddedDisk) {
            try {
                readItensAdded(image);

            } catch (Exception e) {
                if (addImageFuture.get(image) != null)
                    addImageFuture.get(image).cancel(true);
                throw e;
            }

        } else if (addImageFuture.get(image) != null)
            addImageFuture.get(image).get();

    }
    
    /**
     * Deleting previous incomplete TSK entries is needed when resuming processing,
     * so items will get the same tskID, this is one of the requirements to
     * recognize the same item between processings.
     */
    private Long deleteDatasource(File image) throws TskCoreException, SQLException {
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
                    "DELETE FROM tsk_pool_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_vs_parts WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_image_names WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_image_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_fs_info WHERE obj_id >= '" + sourceId + "';",
                    "DELETE FROM tsk_file_attributes WHERE obj_id >= '" + sourceId + "';",
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
        return sourceId;
    }

    /**
     * Deletes basic info about an added image from TSK DB. Not all references are
     * deleted for now.
     */
    public static void deleteImageInfo(Integer tskID, File moduleDir) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getSleuthkitDB(moduleDir));
                Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM tsk_files WHERE data_source_obj_id == '" + tskID + "';");
            st.executeUpdate("DELETE FROM data_source_info WHERE obj_id == '" + tskID + "';");
            st.executeUpdate("DELETE FROM tsk_image_info WHERE obj_id == '" + tskID + "';");
            st.executeUpdate("DELETE FROM tsk_image_names WHERE obj_id == '" + tskID + "';");
        }
    }
    
    public static synchronized void loadImagePasswords(File output) {
        File file = new File(output, PASSWORD_PER_IMAGE);
        if (file.exists()) {
            UTF8Properties props = new UTF8Properties();
            try {
                props.load(file);
                for (String key : props.stringPropertyNames()) {
                    setEnvVar(key, props.getProperty(key));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static synchronized void setImagePassword(File output, String imageName, String password) {
        String envVar = imageName + "_PASSWORD";
        setEnvVar(envVar, password);

        File file = new File(output, PASSWORD_PER_IMAGE);
        UTF8Properties props = new UTF8Properties();
        try {
            if (file.exists()) {
                props.load(file);
            }
            props.setProperty(envVar, password);
            props.store(file);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setEnvVar(String envVar, String value) {
        Util.setEnvVar(envVar, value);
        SleuthkitClient.addEnvVar(envVar, value);
    }

    public void read(File image) throws Exception {
        read(image, null);
    }

    private static synchronized void removeDecodedRangeAfterId(Long id, File output) {
        File file = new File(output, RANGE_ID_FILE);
        if (file.exists()) {
            UTF8Properties props = new UTF8Properties();
            try {
                props.load(file);
                Iterator<Entry<Object, Object>> iterator = props.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<Object, Object> entry = iterator.next();
                    if (Long.valueOf(entry.getValue().toString()) >= id) {
                        iterator.remove();
                    }
                }
                props.store(file);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static synchronized void saveDecodedRangeId(File image, Long start, Long last, File output) {
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

    private static synchronized Long[] getDecodedRangeId(File image, File output) {
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

    private void addImageBlocking(AddImageProcess addImage, File image, int sectorSize)
            throws TskCoreException, TskDataException {
        try {
           addImage.run(UUID.randomUUID().toString(), new String[] { image.getAbsolutePath() }, sectorSize);

        } catch (Throwable e) {
            e.printStackTrace();
            String ignore = "org.sleuthkit.datamodel.TskDataException: Errors occurred while ingesting image";
            for (String error : e.toString().split("\\n")) {
                error = error.trim();
                if (error.isEmpty() || error.contains(ignore))
                    continue;
                // int idx0 = error.toLowerCase().indexOf("encryption detected"); //$NON-NLS-1$
                int idx1 = error.toLowerCase().indexOf("cannot determine file system type"); //$NON-NLS-1$
                int idx2 = error.toLowerCase().indexOf("microsoft reserved partition"); //$NON-NLS-1$
                String logMsg = error + " Image: " + image.getAbsolutePath(); //$NON-NLS-1$
                if (idx1 != -1 && idx2 != -1) {
                    LOGGER.warn(logMsg);
                } else {
                    this.decodingError = true;
                    if (this.embeddedDisk) {
                        LOGGER.warn(logMsg);
                    } else {
                        LOGGER.error(logMsg);
                    }
                }
            }
        }

        LOGGER.info("Image decoded: {}", image.getAbsolutePath()); //$NON-NLS-1$

        Long lastId = null;
        try {
            lastId = sleuthCase.getLastObjectId();
        } catch (TskCoreException e) {
            exception.put(image, e);
        }

        // release permit after DB changes and getting last obj ID
        decodeImageSemaphore.release();

        if (lastId != null) {
            saveDecodedRangeId(image, firstId, lastId, output);
        }

        synchronized (idRangeMap) {
            Long[] ids = { firstId, lastId };
            idRangeMap.put(image, ids);
            idRangeMap.notify();
        }
    }

    private void addImageInBackground(AddImageProcess addImage, File image, int sectorSize) {
        addImageFuture.put(image, executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                addImageBlocking(addImage, image, sectorSize);
                return null;
            }
        }));
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
                if (exception.get(file) != null) {
                    throw exception.get(file);
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

        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
        SplitLargeBinaryConfig splitConfig = ConfigurationManager.get().findObject(SplitLargeBinaryConfig.class);

        if (absFile != null && (absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {

            if (!fsConfig.isToAddUnallocated()) {
                return null;
            }

            // Contorna problema de primeiro acesso ao espaço não alocado na thread de
            // processamento caso haja lock de escrita no sqlite ao adicionar outra evidênca
            absFile.getRanges();

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
                if (len >= splitConfig.getMinItemSizeToFragment())
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
            path = path.replaceFirst("img_.+?\\/", Matcher.quoteReplacement(deviceName) + "/"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        path = inheritedPath + path;
        evidence.setPath(path);
    }

    private IItem addEvidenceFile(AbstractFile absFile) throws Exception {
        return addItem(absFile, null, false);
    }

    private IItem addItem(AbstractFile absFile, Item evidence, boolean unalloc) throws Exception {

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

            FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
            if (fsConfig.getMinOrphanSizeToIgnore() != -1 && absFile.getSize() >= fsConfig.getMinOrphanSizeToIgnore())
                return null;
        }

        if (evidence == null) {
            evidence = new Item();
            evidence.setLength(absFile.getSize());
        }

        if (listOnly || fastmode || embeddedDisk) {
            itemCount++;
            caseData.incDiscoveredEvidences(1);
            if (!embeddedDisk) {
                caseData.incDiscoveredVolume(evidence.getLength());
            } else {
                evidence.setSumVolume(false);
            }
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
        evidence.setIdInDataSource(Long.toString(absFile.getId()));
        evidence.setInputStreamFactory(new SleuthkitInputStreamFactory(sleuthCase, absFile));

        boolean first = true;
        Integer tskId = (int) absFile.getId();
        while ((tskId = getTskParentId(tskId)) != 0) {
            Integer parentId = sleuthIdToId.get(tskId - firstId.intValue());
            if (first) {
                evidence.setParentId(parentId);
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
            evidence.setType("slack"); //$NON-NLS-1$
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
            evidence.setChangeDate(new Date(time * 1000));

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

        if (embeddedDisk) {
            setSubitemProperties(evidence);
        }

        addToProcessingQueue(caseData, evidence);

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

        if (embeddedDisk && content instanceof Image) {
            return parent;
        }
        // Causes exception because parent is null. TODO add to the case properly (#1054)
        if (content instanceof OsAccount) {
            return null;
        }
        if (listOnly || fastmode || embeddedDisk) {
            itemCount++;
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
            evidence.setMediaType(getMediaType(evidence.getExt()));
        } else {
            evidence.setIsDir(true);
        }

        evidence.setHasChildren(content.hasChildren());

        // evidence.setSleuthFile(content);
        evidence.setHash(""); //$NON-NLS-1$
        evidence.setIdInDataSource(Long.toString(content.getId()));
        // below is used to don't process images, partitions or file systems raw data
        evidence.setInputStreamFactory(new SleuthkitInputStreamFactory(sleuthCase, null));

        boolean first = true;
        Integer tskId = (int) content.getId();
        while ((tskId = getTskParentId(tskId)) != 0) {
            Integer parentId = sleuthIdToId.get(tskId - firstId.intValue());
            if (first) {
                evidence.setParentId(parentId);
                first = false;
            }
            evidence.addParentId(parentId);
        }
        evidence.addParentIds(inheritedParents);

        if (embeddedDisk) {
            setSubitemProperties(evidence);
        }

        addToProcessingQueue(caseData, evidence);

        return evidence;
    }

    private void setSubitemProperties(Item item) {
        item.setSubItem(true);
        item.setSubitemId(itemCount);
        item.setExtraAttribute(IndexItem.CONTAINER_TRACK_ID, Util.getTrackID(parent));
    }

    private void addToProcessingQueue(ICaseData caseData, Item item) throws InterruptedException {
        // retrieve and store parenttrackID explicitly before adding to queue
        if (!item.isRoot()) {
            String parenttrackID = idTotrackIDMap.get(item.getParentId());
            if (parenttrackID != null) {
                item.setExtraAttribute(IndexItem.PARENT_TRACK_ID, parenttrackID);
            } else {
                throw new RuntimeException(IndexItem.PARENT_TRACK_ID + " cannot be null: " + item.getPath());
            }
        }

        if (embeddedDisk) {
            // add embedded disk subitems to queue head to process them first
            Manager.getInstance().getProcessingQueues().addItemFirst(item);
        } else {
            Manager.getInstance().getProcessingQueues().addItem(item);
        }
        // store parents trackID after adding to queue (where it is computed and ID
        // could be reassigned)
        if (item.hasChildren() || item.isDir() || item.isRoot()) {
            String trackID = (String) item.getExtraAttribute(IndexItem.TRACK_ID);
            idTotrackIDMap.put(item.getId(), trackID);
        }
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

}
