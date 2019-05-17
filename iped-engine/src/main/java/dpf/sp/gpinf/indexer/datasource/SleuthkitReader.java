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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

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
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.config.SleuthKitConfig;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.task.BaseCarveTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.DataSourceImpl;
import gpinf.dev.data.ItemImpl;
import gpinf.dev.filetypes.GenericFileType;
import iped3.CaseData;
import iped3.Item;
import iped3.sleuthkit.SleuthKitItem;

public class SleuthkitReader extends DataSourceReader {

    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitReader.class);
    
    public static final String MIN_TSK_VER = "4.6.5"; 
    public static String DB_NAME = "sleuth.db"; //$NON-NLS-1$
    private static String IMG_NAME = "IMG_NAME"; //$NON-NLS-1$
    public static MediaType UNALLOCATED_MIMETYPE = BaseCarveTask.UNALLOCATED_MIMETYPE;
    public static final String IN_FAT_FS = "inFatFs"; //$NON-NLS-1$

    private static boolean tskChecked = false;
    private static boolean isTskPatched = false;

    private static ConcurrentHashMap<File, Object[]> idRangeMap = new ConcurrentHashMap<File, Object[]>();

    private static String[] TSK_CMD = { "tsk_loaddb", "-a", "-d", DB_NAME, IMG_NAME }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private Long firstId, lastId;

    private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();

    // chamada content.getParent() é custosa, entao os valores já mapeados são
    // salvos neste cache
    private ArrayList<Integer> parentIds = new ArrayList<Integer>();

    private ArrayList<Long> tskParentIds = new ArrayList<Long>();

    private AddImageProcess addImage;
    private static volatile Thread waitLoadDbThread;
    private String deviceName;
    private boolean isISO9660 = false;
    private boolean fastmode = false;

    // Referência estática para a JVM não finalizar o objeto que será usado
    // futuramente
    // via referência interna ao JNI para acessar os itens do caso
    public static volatile SleuthkitCase sleuthCase;

    public SleuthkitReader(CaseData caseData, File output, boolean listOnly) {
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
        if (tskVer.contains(patchSufix)) { //$NON-NLS-1$
            isTskPatched = true;
            tskVer = tskVer.substring(0, tskVer.indexOf(patchSufix));
        } else
            LOGGER.error("It is highly recommended to apply the iped patch (in sources folder) on sleuthkit!"); //$NON-NLS-1$

        if (tskVer.compareTo(MIN_TSK_VER) < 0) //$NON-NLS-1$
            throw new Exception("Sleuthkit version " + tskVer + " not supported. Install version " + MIN_TSK_VER); //$NON-NLS-1$ //$NON-NLS-2$
        else if (tskVer.compareTo(MIN_TSK_VER) > 0) //$NON-NLS-1$
            LOGGER.error("Sleuthkit version " + tskVer + " not tested! It may contain incompatibilities!"); //$NON-NLS-1$ //$NON-NLS-2$

        tskChecked = true;
    }

    public int read(File image) throws Exception {

        checkTSKVersion();

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
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
        parentIds.clear();
        tskParentIds.clear();

        deviceName = getEvidenceName(image);
        dataSource = new DataSourceImpl(image);
        dataSource.setName(deviceName);

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

            SleuthKitConfig sleuthKitConfig = (SleuthKitConfig) ConfigurationManager.getInstance()
                    .findObjects(SleuthKitConfig.class).iterator().next();
            if (sleuthKitConfig.isRobustImageReading())
                Manager.getInstance().initSleuthkitServers(sleuthCase.getDbDirPath());

            if (image.getName().equals(DB_NAME)) {
                firstId = 0L;
                lastId = sleuthCase.getLastObjectId();

                synchronized (idRangeMap) {
                    Object[] ids = { firstId, lastId };
                    idRangeMap.put(image, ids);
                    idRangeMap.notify();
                }
            } else {
                WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("SleuthkitReader.WaitDecode") + image.getAbsolutePath()); //$NON-NLS-1$
                LOGGER.info("Decoding image {}", image.getAbsolutePath()); //$NON-NLS-1$

                firstId = sleuthCase.getLastObjectId() + 1;

                synchronized (idRangeMap) {
                    Object[] ids = { firstId, null };
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

        if (!(listOnly && fastmode))
            readItensAdded(image);

        else if (waitLoadDbThread != null)
            waitLoadDbThread.join();

        return 0;
    }

    private void waitProcess(Process process, File image) {
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                LOGGER.error("Sleuthkit LoadDb returned an error {}. Possibly" //$NON-NLS-1$
                        + " many items were not added to the case!", exit); //$NON-NLS-1$
            }

        } catch (InterruptedException ie) {
            process.destroyForcibly();
        }

        LOGGER.info("Image decoded: {}", image.getAbsolutePath()); //$NON-NLS-1$

        Object lastId;
        try {
            lastId = sleuthCase.getLastObjectId();
        } catch (TskCoreException e) {
            lastId = e;
        }

        synchronized (idRangeMap) {
            Object[] ids = { firstId, lastId };
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
            firstId = (Long) idRangeMap.get(file)[0];
        }

        Long endId, startId = firstId;
        do {
            endId = sleuthCase.getLastObjectId();

            if (lastId == null) {
                Object obj = idRangeMap.get(file)[1];
                if (obj != null && obj instanceof Long) {
                    lastId = (Long) obj;
                } else if (obj != null) {
                    throw (Exception) obj;
                }
            }
            if (lastId != null) {
                endId = lastId;
            }

            if (startId > endId) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    if (waitLoadDbThread != null) {
                        waitLoadDbThread.interrupt();
                    }
                    throw new InterruptedException();
                }
                continue;
            }
            if (endId - startId > 100000)
                endId = startId + 100000;
            readItensInOffsetOrder(startId, endId);
            startId = endId + 1;

        } while (!endId.equals(lastId));
    }

    private class ItemStart implements Comparable<ItemStart> {

        int id;
        long start = Long.MAX_VALUE; // processa itens da MFT ao final

        @Override
        public int compareTo(ItemStart o) {
            return start < o.start ? -1 : start > o.start ? 1 : 0;
        }
    }

    /**
     * Ordena os itens pelo primeiro setor utilizado, na tentativa de ler os itens
     * na ordem em que aparecem fisicamente no HD, evitando seeks na imagem
     */
    private void readItensInOffsetOrder(long start, long last) throws Exception {

        if (!fastmode && !listOnly) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    Messages.getString("SleuthkitReader.SortingByOffset") + start + " - " + last); //$NON-NLS-1$ //$NON-NLS-2$
            LOGGER.info("Sorting by sector offset: id " + start + " to " + last); //$NON-NLS-1$ //$NON-NLS-2$
        }

        ArrayList<ItemStart> items = new ArrayList<ItemStart>();
        for (long k = start; k <= last; k++) {
            if (Thread.currentThread().isInterrupted()) {
                if (waitLoadDbThread != null) {
                    waitLoadDbThread.interrupt();
                }
                throw new InterruptedException();
            }
            ItemStart item = new ItemStart();
            item.id = (int) k;
            if (!fastmode && !listOnly) {
                List<TskFileRange> rangeList = sleuthCase.getFileRanges(k);
                if (rangeList != null && !rangeList.isEmpty()) {
                    item.start = rangeList.get(0).getByteStart();
                }
            }
            items.add(item);
        }

        ItemStart[] itemArray = items.toArray(new ItemStart[0]);
        items.clear();

        if (!fastmode && !listOnly) {
            Arrays.sort(itemArray);
            LOGGER.info("Sorting by offset finished."); //$NON-NLS-1$
        }

        if (!listOnly) {
            long[] ids = new long[(int) (last - start + 1)];
            for (int k = 0; k < ids.length; k++)
                ids[k] = start + k;
            cacheTskParentIds(ids);
        }

        for (ItemStart item : itemArray) {
            if (Thread.currentThread().isInterrupted()) {
                if (waitLoadDbThread != null) {
                    waitLoadDbThread.interrupt();
                }
                throw new InterruptedException();
            }
            long k = item.id;

            // Faster than getContentById
            Content content = sleuthCase.getAbstractFileById(k);
            if (content == null) {
                content = sleuthCase.getContentById(k);
            }
            if (content == null) {
                continue;
            }

            Long parentId = null;
            if (!listOnly) {
                parentId = getTskParentId(k);
                createParendIds(parentId);
            }

            addContent(content, parentId);
        }
    }

    private Long getTskParentId(long id) throws TskCoreException, SQLException {
        return tskParentIds.get((int) id);
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
                if (parId != 0)
                    tskParentIds.set((int) objId, parId);
            }
        } finally {
            dbQuery.close();
        }
    }

    private void createParendIds(Long parent) throws Exception {
        boolean parentsAdded = false;
        int itemId = -1;
        if (parent != null) {
            do {
                int parentSleuthId = (int) (parent - firstId);
                int parentId;
                if (parentSleuthId < sleuthIdToId.size() && sleuthIdToId.get(parentSleuthId) != -1) {
                    parentId = sleuthIdToId.get(parentSleuthId);
                    parentsAdded = true;
                } else {
                    while (parentSleuthId >= sleuthIdToId.size()) {
                        sleuthIdToId.add(-1);
                    }
                    parentId = ItemImpl.getNextId();
                    sleuthIdToId.set(parentSleuthId, parentId);
                }
                while (parentId >= parentIds.size()) {
                    parentIds.add(-1);
                }
                if (itemId != -1) {
                    parentIds.set(itemId, parentId);
                }
                itemId = parentId;

            } while (!parentsAdded && (parent = getTskParentId(parent)) != null);
        }
    }

    private void addContent(Content content, Long parent) throws Exception {

        AbstractFile absFile = null;
        if (content instanceof AbstractFile) {
            absFile = (AbstractFile) content;
        }

        if (content != null && absFile == null) {
            addEvidenceFile(content);
        }

        IPEDConfig ipedConfig = (IPEDConfig) ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator()
                .next();
        if (absFile != null && (absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || absFile.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {

            if (!ipedConfig.isToAddUnallocated()) {
                return;
            }

            // Contorna problema de primeiro acesso ao espaço não alocado na thread de
            // processamento caso haja lock de escrita no sqlite ao adicionar outra evidênca
            absFile.getRanges();

            AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            long fragSize = advancedConfig.getUnallocatedFragSize();
            int fragNum = 0;
            for (long offset = 0; offset < absFile.getSize(); offset += fragSize) {
                long len = offset + fragSize < absFile.getSize() ? fragSize : absFile.getSize() - offset;
                ItemImpl frag = new ItemImpl();
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
                addItem(absFile, frag, true, parent);
            }

            return;

        }

        if (absFile == null || absFile.getName().startsWith("$BadClus:$Bad")) { //$NON-NLS-1$
            return;
        }

        if (!ipedConfig.isToAddFileSlacks() && absFile.getType() == TSK_DB_FILES_TYPE_ENUM.SLACK
                && !isVolumeShadowCopy(absFile))
            return;

        addEvidenceFile(absFile, parent);

    }

    private boolean isVolumeShadowCopy(AbstractFile absFile) {
        return absFile.getName().toLowerCase().contains("{3808876b-c176-4e48-b7ae-04046e6cc752}"); //$NON-NLS-1$
    }

    private void setPath(Item evidence, String path) {
        if (deviceName != null) {
            path = path.replaceFirst("img_.+?\\/", deviceName + "/"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        evidence.setPath(path);
    }

    private void addEvidenceFile(AbstractFile absFile, Long parent) throws Exception {
        addItem(absFile, null, false, parent);
    }

    private void addItem(AbstractFile absFile, Item evidence, boolean unalloc, Long parent) throws Exception {

        if (absFile.isDir() && (absFile.getName().equals(".") || absFile.getName().equals(".."))) { //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }

        if (absFile.getUniquePath().contains("/$OrphanFiles/")) { //$NON-NLS-1$
            if (absFile instanceof FsContent) {
                FileSystem fs = ((FsContent) absFile).getFileSystem();
                // ignore orphans greater than its FS
                if (absFile.getSize() > fs.getSize())
                    return;
                // ignore orphans greater than its partition
                if (fs.getParent() != null && fs.getParent().getSize() < absFile.getSize())
                    return;
            }

            AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                    .findObjects(AdvancedIPEDConfig.class).iterator().next();
            if (advancedConfig.getMinOrphanSizeToIgnore() != -1
                    && absFile.getSize() >= advancedConfig.getMinOrphanSizeToIgnore())
                return;
        }

        if (evidence == null) {
            evidence = new ItemImpl();
            evidence.setLength(absFile.getSize());
        }

        if (listOnly || fastmode) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(evidence.getLength());
            if (listOnly)
                return;
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
        if (evidence instanceof SleuthKitItem) {
            ((SleuthKitItem) evidence).setSleuthFile(absFile);
            ((SleuthKitItem) evidence).setSleuthId((int) absFile.getId());
        }

        int sleuthId = (int) (absFile.getId() - firstId);

        if (sleuthId < sleuthIdToId.size() && sleuthIdToId.get(sleuthId) != -1) {
            evidence.setId(sleuthIdToId.get(sleuthId));
        } else {
            while (sleuthId >= sleuthIdToId.size()) {
                sleuthIdToId.add(-1);
            }
            if (!unalloc) {
                sleuthIdToId.set(sleuthId, evidence.getId());
            }
        }

        Integer parentId = sleuthIdToId.get((int) (parent - firstId));
        evidence.setParentId(parentId);

        while (evidence.getId() >= parentIds.size()) {
            parentIds.add(-1);
        }
        parentIds.set(evidence.getId(), parentId);

        do {
            evidence.addParentId(parentId);
        } while ((parentId = parentIds.get(parentId)) != -1);

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

        caseData.addItem(evidence);

    }

    private void addEvidenceFile(Content content) throws Exception {

        if (listOnly || fastmode) {
            caseData.incDiscoveredEvidences(1);
            if (listOnly)
                return;
        }

        ItemImpl evidence = new ItemImpl();
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
                evidence.setPath("/" + deviceName); //$NON-NLS-1$
            } else {
                setPath(evidence, path);
            }
        } else {
            evidence.setPath(path);
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

        int sleuthId = (int) (content.getId() - firstId);

        if (sleuthId < sleuthIdToId.size() && sleuthIdToId.get(sleuthId) != -1) {
            evidence.setId(sleuthIdToId.get(sleuthId));
        } else {
            while (sleuthId >= sleuthIdToId.size()) {
                sleuthIdToId.add(-1);
            }
            sleuthIdToId.set(sleuthId, evidence.getId());
        }

        Content parent = content.getParent();
        Integer parentId = -1;
        if (parent != null) {
            parentId = sleuthIdToId.get((int) (parent.getId() - firstId));
            evidence.setParentId(parentId);
        }

        while (evidence.getId() >= parentIds.size()) {
            parentIds.add(-1);
        }
        parentIds.set(evidence.getId(), parentId);

        while (parentId != -1) {
            evidence.addParentId(parentId);
            parentId = parentIds.get(parentId);
        }

        caseData.addItem(evidence);

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
