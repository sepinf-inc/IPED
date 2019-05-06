package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskData.TSK_FS_TYPE_ENUM;

import iped3.Item;
import iped3.sleuthkit.SleuthKitItem;

public class IgnoreHardLinkTask extends AbstractTask {

  public static final String IGNORE_HARDLINK_ATTR = "ignoredHardLink"; //$NON-NLS-1$

  private static Map<Long, Map<HardLink, Object>> fileSystemOrigMap = new HashMap<Long, Map<HardLink, Object>>();
  private static Map<Long, Map<HardLink, Object>> fileSystemSlackMap = new HashMap<Long, Map<HardLink, Object>>();
  
  private static Object lock = new Object();
  private boolean taskEnabled = false;

  @Override
  public void init(Properties confParams, File confDir) throws Exception {

    String value = confParams.getProperty("ignoreHardLinks"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      taskEnabled = Boolean.valueOf(value);
    }

  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
  }
  
  @Override
  protected void process(Item evidence) throws Exception {

    if (!taskEnabled || evidence.getLength() == null || evidence.getLength() == 0 || evidence.isCarved()) {
      return;
    }
    
    if(!(evidence instanceof SleuthKitItem)) {
    	return;    	
    }

    SleuthKitItem sevidence =  ((SleuthKitItem)evidence);
    Content content = sevidence.getSleuthFile();
    if (content != null && content instanceof FsContent) {
      FsContent fsContent = (FsContent) content;
      TSK_FS_TYPE_ENUM fsType = fsContent.getFileSystem().getFsType(); 
      if(!fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS) && 
         !fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS_DETECT))
          return;
      long fsId = fsContent.getFileSystemId();
      long metaAddr = fsContent.getMetaAddr();
      HardLink hardLink;
      //Testa se é AlternateDataStream ou ResourceFork
      if (!evidence.getName().contains(":")) { //$NON-NLS-1$
        hardLink = new HardLink(metaAddr);
      } else {
        hardLink = new DetailedHardLink(metaAddr, evidence.getLength(), evidence.getName());
      }
      
      Map<Long, Map<HardLink, Object>> fileSystemMap = fileSystemOrigMap;
      if(fsContent instanceof SlackFile)
          fileSystemMap = fileSystemSlackMap;

      boolean ignore = false;

      synchronized (lock) {
        Map<HardLink, Object> hardLinkMap = fileSystemMap.get(fsId);
        if (hardLinkMap == null) {
          hardLinkMap = new HashMap<HardLink, Object>();
          fileSystemMap.put(fsId, hardLinkMap);
        }

        Object id = hardLinkMap.get(hardLink);
        //test if it is not the same item from other processing queue
        if (id != null) {
        	if(!id.equals(sevidence.getSleuthId()))
                ignore = true;
        } else {
            hardLinkMap.put(hardLink, sevidence.getSleuthId());
        }
      }

      if (ignore) {
    	sevidence.setSleuthFile(null);
        evidence.setExtraAttribute(IGNORE_HARDLINK_ATTR, "true"); //$NON-NLS-1$
      }

    }

  }

  private class HardLink {

    long metaAddr;

    public HardLink(long meta) {
      metaAddr = meta;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof DetailedHardLink) {
        return false;
      }
      return metaAddr == ((HardLink) o).metaAddr;
    }

    @Override
    public int hashCode() {
      return (int) metaAddr;
    }
  }

  //usa 2x mais RAM do que HardLink
  private class DetailedHardLink extends HardLink {

    String name;
    long size;

    public DetailedHardLink(long meta, long size, String name) {
      super(meta);
      this.size = size;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DetailedHardLink)) {
        return false;
      }
      DetailedHardLink dhl = (DetailedHardLink) o;
      return metaAddr == dhl.metaAddr && size == dhl.size && name.equals(dhl.name);
    }
  }

}
