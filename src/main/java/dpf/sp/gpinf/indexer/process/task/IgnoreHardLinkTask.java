package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskData.TSK_FS_TYPE_ENUM;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

public class IgnoreHardLinkTask extends AbstractTask{
	
	private static Map<Long, Map<HardLink, Object>> fileSystemMap = new HashMap<Long, Map<HardLink, Object>>();
	private static Object lock = new Object();
	private boolean taskEnabled = false;

	public IgnoreHardLinkTask(Worker worker) {
		super(worker);
	}

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
		
		String value = confParams.getProperty("ignoreHardLinks");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			taskEnabled = Boolean.valueOf(value);
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void process(EvidenceFile evidence) throws Exception {
		
		if(!taskEnabled || evidence.getLength() == null || evidence.getLength() == 0 || evidence.isCarved())
			return;
		
		Content content = evidence.getSleuthFile(); 
		if(content != null && content instanceof FsContent){
			FsContent fsContent = (FsContent)content;
			/*TSK_FS_TYPE_ENUM fsType = fsContent.getFileSystem().getFsType(); 
			if(!fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS) && 
			   !fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS_DETECT))
				return;
			*/
			long fsId = fsContent.getFileSystemId();
			long metaAddr = fsContent.getMetaAddr();
			HardLink hardLink;
			//Testa se é AlternateDataStream ou ResourceFork
			if(!evidence.getName().contains(":"))
				hardLink = new HardLink(metaAddr);
			else
				hardLink = new DetailedHardLink(metaAddr, evidence.getLength(), evidence.getName());
			
			HashValue hash = evidence.getHashValue();
			Object value = null;
			boolean ignore = false;
			
			synchronized(lock){
				Map<HardLink, Object> hardLinkMap = fileSystemMap.get(fsId);
				if(hardLinkMap == null){
					hardLinkMap = new HashMap<HardLink, Object>();
					fileSystemMap.put(fsId, hardLinkMap);
				}
				
				if(hardLinkMap.containsKey(hardLink)){
					value = hardLinkMap.get(hardLink);
					if(((Integer)evidence.getId()).equals(value)){
						hardLinkMap.put(hardLink, hash);
						lock.notifyAll();
					}else{
						ignore = true;
						while(value instanceof Integer){
							lock.wait();
							value = hardLinkMap.get(hardLink);
						}
					}
				}else{
					hardLinkMap.put(hardLink, evidence.getId());
					//evidence.setExtraAttribute("firstHardLink", "true");
				}
			}
			
			if(ignore){
				evidence.setSleuthFile(null);
				evidence.setExtraAttribute("ignoredHardLink", "true");
				if(value == null)
					evidence.setHash("");
				else
					evidence.setHash(((HashValue)value).toString());
			}
			
		}
	
	}
	
	
	private class HardLink{
		long metaAddr;
		
		public HardLink(long meta){
			metaAddr = meta;
		}
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof DetailedHardLink)
				return false;
			return metaAddr == ((HardLink)o).metaAddr;
		}

		@Override
		public int hashCode() {
			return (int)metaAddr;
		}
	}
	
	//usa 2x mais RAM do que HardLink
	private class DetailedHardLink extends HardLink{
		String name;
		long size;
		
		public DetailedHardLink(long meta, long size, String name){
			super(meta);
			this.size = size;
			this.name = name;
		}
		@Override
		public boolean equals(Object o) {
			if(!(o instanceof DetailedHardLink))
				return false;
			DetailedHardLink dhl = (DetailedHardLink)o;
			return metaAddr == dhl.metaAddr && size == dhl.size && name.equals(dhl.name);
		}
	}

}
