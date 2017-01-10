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
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;

public class SleuthkitReader extends DataSourceReader {

  private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitReader.class);

  public static String DB_NAME = "sleuth.db";
  private static String IMG_NAME = "IMG_NAME";
  public static MediaType UNALLOCATED_MIMETYPE = CarveTask.UNALLOCATED_MIMETYPE;

  private static boolean tskChecked = false;
  private static boolean isTskPatched = false;

  private static ConcurrentHashMap<File, Object[]> idRangeMap = new ConcurrentHashMap<File, Object[]>();

  private static String[] TSK_CMD = {"tsk_loaddb", "-a", "-d", DB_NAME, IMG_NAME};

  private Long firstId, lastId;

  private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();

  //chamada content.getParent() é custosa, entao os valores já mapeados são salvos neste cache
  private ArrayList<Integer> parentIds = new ArrayList<Integer>();

  private AddImageProcess addImage;
  private static volatile Thread waitLoadDbThread;
  private String deviceName;
  private boolean isISO9660 = false;
  private boolean fastmode = false;

  //Referência estática para a JVM não finalizar o objeto que será usado futuramente
  //via referência interna ao JNI para acessar os itens do caso
  public static volatile SleuthkitCase sleuthCase;

  public SleuthkitReader(CaseData caseData, File output, boolean listOnly) {
    super(caseData, output, listOnly);
  }

  public static void setTskPath(String tskPath) throws Exception {
    TSK_CMD[0] = tskPath;
  }

  public boolean isSupported(File file) {
    isISO9660 = false;
    String name = file.getName().toLowerCase();
    return name.endsWith(".000")
        || name.endsWith(".001")
        || name.endsWith(".e01")
        || name.endsWith(".aff")
        || name.endsWith(".l01")
        || name.endsWith(".dd")
        || name.endsWith(".vmdk")
        || name.endsWith(".vhd")
        || isPhysicalDrive(file)
        || (isISO9660 = isISO9660(file))
        || name.equals(DB_NAME);
  }

  public static boolean isPhysicalDrive(File file) {
    return Util.isPhysicalDrive(file);
  }

  private boolean isISO9660(File file) {

    FileInputStream fis = null;
    try {
      String magicString = "CD001";
      byte[] magic = magicString.getBytes("UTF-8");
      byte[] header = new byte[64 * 1024];
      fis = new FileInputStream(file);

      int read = 0, off = 0;
      while (read != -1 && (off += read) < header.length) {
        read = fis.read(header, off, header.length - off);
      }

      if (matchMagic(magic, header, 32769) || //CDROM sector 2048
          matchMagic(magic, header, 34817) || //CDROM sector 2048
          matchMagic(magic, header, 37649) || //CDROM RAW sector 2352
          matchMagic(magic, header, 37657) || //CDROM RAW XA sector 2352
          matchMagic(magic, header, 40001) || //CDROM RAW sector 2352
          matchMagic(magic, header, 40009)) //CDROM RAW XA sector 2352
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

    String[] TSK_VERSION = {TSK_CMD[0], "-V"};
    ProcessBuilder pb = new ProcessBuilder(TSK_VERSION);
    Process process = pb.start();

    process.getOutputStream().close();
    process.waitFor();
    if (process.exitValue() != 0) {
      throw new Exception("Erro ao testar tsk_loaddb. Execução terminou com erro " + process.exitValue());
    }

    InputStreamReader reader = new InputStreamReader(process.getInputStream());
    StringBuilder out = new StringBuilder();
    char[] buffer = new char[1024];
    for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
      out.append(buffer, 0, n);
    }

    reader.close();
    process.getErrorStream().close();

    if (!out.toString().contains(" 4.3")) {
      throw new Exception("Versao do Sleuthkit nao suportada. Instale a versao 4.3");
    }

    if (out.toString().contains("iped-patch04")) {
      isTskPatched = true;
    }else
      LOGGER.error("Recomenda-se fortemente aplicar o patch04 do iped no tsk-4.3, disponível na pasta sources!");

    tskChecked = true;
  }

  public int read(File image) throws Exception {

    checkTSKVersion();

    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
    if (args.getCmdArgs().containsKey("--fastmode")) {
      fastmode = true;
    }
    
    int offset = TimeZone.getDefault().getRawOffset() / 3600000;
    String timezone = "GMT" + (-offset);
    if (args.getCmdArgs().containsKey("-tz")) {
    	timezone = args.getCmdArgs().get("-tz").get(0);
    	if(timezone.contains("+")) timezone = timezone.replace('+', '-');
    	else timezone = timezone.replace('-', '+');
    }
    
    String sectorSize = null;
    if (args.getCmdArgs().containsKey("-b"))
    	sectorSize = args.getCmdArgs().get("-b").get(0);

    firstId = null;
    lastId = null;
    sleuthIdToId.clear();
    parentIds.clear();
    
    deviceName = getEvidenceName(image);
    dataSource = new DataSource(image);
    dataSource.setName(deviceName);

    String dbPath = output.getParent() + File.separator + DB_NAME;

    if (listOnly) {

      if (sleuthCase == null) {
        if (new File(dbPath).exists()) {
          sleuthCase = SleuthkitCase.openCase(dbPath);

        } else {
          IndexFiles.getInstance().firePropertyChange("mensagem", "", "Criando " + dbPath);
          LOGGER.info("Criando {}", dbPath);
          sleuthCase = SleuthkitCase.newCase(dbPath);
          LOGGER.info("{} criado", dbPath);
        }
      }

      if (image.getName().equals(DB_NAME)) {
        firstId = 0L;
        lastId = sleuthCase.getLastObjectId();

        synchronized (idRangeMap) {
          Object[] ids = {firstId, lastId};
          idRangeMap.put(image, ids);
          idRangeMap.notify();
        }
      } else {
        IndexFiles.getInstance().firePropertyChange("mensagem", "", "Aguarde, decodificando imagem " + image.getAbsolutePath());
        LOGGER.info("Decodificando imagem {}", image.getAbsolutePath());

        firstId = sleuthCase.getLastObjectId() + 1;

        synchronized (idRangeMap) {
          Object[] ids = {firstId, null};
          idRangeMap.put(image, ids);
          idRangeMap.notify();
        }
        
        int cmdLen = TSK_CMD.length;
        if(isTskPatched) cmdLen += 2;
        if(sectorSize != null) cmdLen += 2;

        String[] cmd = new String[cmdLen];
        for (int i = 0; i < TSK_CMD.length; i++) {
          cmd[i] = TSK_CMD[i];
          if (cmd[i].equals(DB_NAME))
            cmd[i] = dbPath;
          if (cmd[i].equals(IMG_NAME)){
        	  if(isTskPatched){
              	cmd[TSK_CMD.length - 1] = "-z";
              	cmd[TSK_CMD.length] = timezone;
              }
              if(sectorSize != null){
              	cmd[cmdLen - 3] = "-b";
              	cmd[cmdLen - 2] = sectorSize;
              }
              cmd[cmdLen - 1] = image.getAbsolutePath();
          }
        }

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

    java.util.logging.Logger.getLogger("org.sleuthkit").setLevel(java.util.logging.Level.SEVERE);

    readItensAdded(image);

    return 0;
  }

  private void waitProcess(Process process, File image) {
    try {
      int exit = process.waitFor();
      if (exit != 0) {
        LOGGER.error("Sleuthkit LoadDb terminou com erro {}. Possivelmente"
            + " muitos itens nao foram adicionados ao caso!", exit);
      }

    } catch (InterruptedException ie) {
      process.destroy();
    }

    LOGGER.info("Imagem {} decodificada.", image.getAbsolutePath());

    Object lastId;
    try {
      lastId = sleuthCase.getLastObjectId();
    } catch (TskCoreException e) {
      lastId = e;
    }

    synchronized (idRangeMap) {
      Object[] ids = {firstId, lastId};
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
   * Processa os itens em grupos, conforme vão sendo adicionados ao sqlite pelo loadDb, caso seja
   * utilizado loaddb com patch de concorrência
   */
  private void readItensAdded(File file) throws Exception {

    synchronized (idRangeMap) {
      if ((idRangeMap.get(file)) == null) {
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
      readItensInOffsetOrder(startId, endId);
      startId = endId + 1;

    } while (!endId.equals(lastId));
  }

  private class ItemStart implements Comparable<ItemStart> {

    int id;
    long start = Long.MAX_VALUE; //processa itens da MFT ao final

    @Override
    public int compareTo(ItemStart o) {
      return start < o.start ? -1 : start > o.start ? 1 : 0;
    }
  }

  /**
   * Ordena os itens pelo primeiro setor utilizado, na tentativa de ler os itens na ordem em que
   * aparecem fisicamente no HD, evitando seeks na imagem
   */
  private void readItensInOffsetOrder(long start, long last) throws Exception {

    if (!fastmode && !listOnly) {
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Ordenando pelo offset: id " + start + " a " + last);
      LOGGER.info("Ordenando pelo offset: id " + start + " a " + last);
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
    Arrays.sort(itemArray);

    if (!listOnly) {
      LOGGER.info("Ordenação pelo offset finalizada.");
    }

    for (ItemStart item : itemArray) {
      if (Thread.currentThread().isInterrupted()) {
        if (waitLoadDbThread != null) {
          waitLoadDbThread.interrupt();
        }
        throw new InterruptedException();
      }
      long k = item.id;

      //Faster than getContentById
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

  /*
   * Implementação mais rápida que a chamada content.getParent().getId()
   */
  private Long getTskParentId(long id) throws TskCoreException, SQLException {
    CaseDbQuery dbQuery = sleuthCase.executeQuery("SELECT par_obj_id  FROM tsk_objects WHERE obj_id = " + id);
    try {
      if (dbQuery.getResultSet().next()) {
        Long parId = dbQuery.getResultSet().getLong(1);
        if (parId != 0) {
          return parId;
        }
      }
    } finally {
      dbQuery.close();
    }
    return null;
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
          parentId = EvidenceFile.getNextId();
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

  /*private void readItemsInTreeOrder() throws TskCoreException, Exception{
   if(firstId != 0)
   recurseIntoContent(sleuthCase.getImageById(firstId), null);
   else
   for(Content child : sleuthCase.getImages())
   recurseIntoContent(child, null);
   }
	
   private void recurseIntoContent(Content content, Content parent) throws Exception{
		
   addContent(content, parent.getId());
   Content unallocFolder = null;
   for(Content child : content.getChildren()){
   //Processa não alocado no final
   if("$Unalloc".equals(child.getName()))
   unallocFolder = child;
   else
   recurseIntoContent(child, content);
   }
   if(unallocFolder != null)
   recurseIntoContent(unallocFolder, content);
   }*/
  private void addContent(Content content, Long parent) throws Exception {

    AbstractFile absFile = null;
    if (content instanceof AbstractFile) {
      absFile = (AbstractFile) content;
    }

    if (content != null && absFile == null) {
      addEvidenceFile(content);
    }

    if (absFile != null && absFile.getType().compareTo(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) == 0) {
      if (!Configuration.addUnallocated) {
        return;
      }

      //Contorna problema de primeiro acesso ao espaço não alocado na thread de
      //processamento caso haja lock de escrita no sqlite ao adicionar outra evidênca
      absFile.getRanges();

      long fragSize = Configuration.unallocatedFragSize;
      int fragNum = 0;
      for (long offset = 0; offset < absFile.getSize(); offset += fragSize) {
        long len = offset + fragSize < absFile.getSize() ? fragSize : absFile.getSize() - offset;
        EvidenceFile frag = new EvidenceFile();
        String sufix = "";
        if (absFile.getSize() > fragSize) {
          sufix = "-Frag" + fragNum++;
          frag.setFileOffset(offset);
        }
        frag.setName(absFile.getName() + sufix);
        frag.setLength(len);
        if(len >= Configuration.minItemSizeToFragment)
        	frag.setHash("");

        setPath(frag, absFile.getUniquePath() + sufix);

        frag.setMediaType(UNALLOCATED_MIMETYPE);
        addEvidenceFile(absFile, frag, true, parent);
      }

      return;

    }

    if (absFile == null || absFile.getName().equals("$BadClus:$Bad")) {
      return;
    }

    addEvidenceFile(absFile, parent);

  }

  private void setPath(EvidenceFile evidence, String path) {
    if (deviceName != null) {
      path = path.replaceFirst("img_.+?\\/", deviceName + "/");
    }
    evidence.setPath(path);
  }

  private void addEvidenceFile(AbstractFile absFile, Long parent) throws Exception {
    addEvidenceFile(absFile, null, false, parent);
  }

  private void addEvidenceFile(AbstractFile absFile, EvidenceFile evidence, boolean unalloc, Long parent) throws Exception {

    if (absFile.isDir() && (absFile.getName().equals(".") || absFile.getName().equals(".."))) {
      return;
    }

    if (Configuration.minOrphanSizeToIgnore != -1 && absFile.getUniquePath().contains("/$OrphanFiles/")
        && absFile.getSize() >= Configuration.minOrphanSizeToIgnore) {
      return;
    }

    if (evidence == null) {
      evidence = new EvidenceFile();
      evidence.setLength(absFile.getSize());
    }

    if (listOnly) {
      caseData.incDiscoveredEvidences(1);
      caseData.incDiscoveredVolume(evidence.getLength());
      return;
    }
    
    evidence.setDataSource(dataSource);

    if (evidence.getName() == null) {
      if (absFile.isRoot() && absFile.getName().isEmpty()) {
        evidence.setName("/");
      } else {
        evidence.setName(absFile.getName());
      }

      setPath(evidence, absFile.getUniquePath());
    }

    if (absFile.isDir()) {
      evidence.setIsDir(true);
    }

    evidence.setHasChildren(absFile.hasChildren());
    evidence.setSleuthFile(absFile);
    evidence.setSleuthId((int)absFile.getId());

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
        || absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.ORPHAN)) {
      evidence.setDeleted(true);
    }

    long time = absFile.getAtime();
    if (time != 0) {
      evidence.setAccessDate(new Date(time * 1000));
    }
    time = absFile.getMtime();
    if (time != 0) {
      evidence.setModificationDate(new Date(time * 1000));
    }
    time = absFile.getCrtime();
    if (time != 0) {
      evidence.setCreationDate(new Date(time * 1000));
    }

    caseData.addEvidenceFile(evidence);

  }

  private void addEvidenceFile(Content content) throws Exception {

    if (listOnly) {
      caseData.incDiscoveredEvidences(1);
      return;
    }

    EvidenceFile evidence = new EvidenceFile();
    evidence.setLength(content.getSize());
    evidence.setSumVolume(false);
    
    evidence.setDataSource(dataSource);

    if (content.getName().isEmpty()) {
      if (content instanceof VolumeSystem) {
        evidence.setName(((VolumeSystem) content).getType().getName() + "_Partition_Table");

      } else if (content instanceof FileSystem) {
        String fsName = ((FileSystem) content).getFsType().name();
        fsName = fsName.replace("TSK_FS_TYPE_", "");
        evidence.setName(fsName);
      }

    } else {
      if (deviceName != null && content instanceof Image) {
        evidence.setName(deviceName);
      } else if (content instanceof Volume) {
        long lenGB = content.getSize() >> 30;
        String lenStr = lenGB + "GB";
        if (lenGB == 0) {
          lenStr = (content.getSize() >> 20) + "MB";
        }

        evidence.setName(content.getName() + " [" + ((Volume) content).getDescription() + "] (" + lenStr + ")");
      } else {
        evidence.setName(content.getName());
      }
    }

    // evidence.setType(new UnknownFileType(evidence.getExt()));
    String path = content.getUniquePath();
    if (deviceName != null) {
      if (path.indexOf('/', 1) == -1) {
        evidence.setPath("/" + deviceName);
      } else {
        setPath(evidence, path);
      }
    } else {
      evidence.setPath(path);
    }

    if (content instanceof Image) {
      evidence.setRoot(true);
      if (isISO9660) {
        evidence.setMediaType(MediaType.application("x-iso9660-image"));
      } else {
        evidence.setMediaType(MediaType.application("x-disk-image"));
      }
    } else {
      evidence.setIsDir(true);
    }

    evidence.setHasChildren(content.hasChildren());

    //evidence.setSleuthFile(content);
    evidence.setHash("");
    evidence.setSleuthId((int)content.getId());

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

    caseData.addEvidenceFile(evidence);

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
          if (!msg.isEmpty()) {
            if (msg.toLowerCase().contains("error")) {
              LOGGER.error("Erro do Sleuthkit ao decodificar imagem " + image);
              LOGGER.error(msg);
            } else {
              LOGGER.info(msg);
            }
          }
        }
        return;
      }
    }.start();
  }

}
