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
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.VolumeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.SleuthEvidenceFile;

public class SleuthkitReader extends DataSourceReader{

	private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitReader.class);
	
	public static String DB_NAME = "sleuth.db";
	private Long firstId , lastId;
	//private static Object lock = new Object();
	private static ConcurrentHashMap<File, Object[]> idRangeMap = new ConcurrentHashMap<File, Object[]>();
	private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();
	private ArrayList<Integer> parentIds = new ArrayList<Integer>();
	
	public static MediaType UNALLOCATED_MIMETYPE = CarveTask.UNALLOCATED_MIMETYPE;

	private AddImageProcess addImage;
	private String deviceName;
	
	//Referência estática para a JVM não finalizar o objeto que será usado futuramente
	//via referência interna ao JNI para acessar os itens do caso
	static SleuthkitCase sleuthCase;

	public SleuthkitReader(CaseData caseData, File output, boolean listOnly) {
		super(caseData, output, listOnly);
	}

	public boolean isSupported(File report) {
		String name = report.getName().toLowerCase();
		return 	name.endsWith(".000") ||
				name.endsWith(".001") || 
				name.endsWith(".e01") || 
				name.endsWith(".aff") || 
				name.endsWith(".l01") ||
				name.endsWith(".dd") || 
				name.endsWith(".iso") ||
				isPhysicalDrive(report) ||
				name.equals(DB_NAME);
	}
	
	public static boolean isPhysicalDrive(File file){
		return file.getName().toLowerCase().contains("physicaldrive") ||
			   file.getAbsolutePath().toLowerCase().contains("/dev/");
	}
	
	public String currentDirectory(){
		if(addImage != null)
			return addImage.currentDirectory();
		else
			return null;
	}

	public int read(File file) throws Exception {

		String[] imgPath = { file.getAbsolutePath() };
		

		String dbPath = output.getParent() + File.separator + DB_NAME;

		if (!listOnly) {
			
			synchronized (idRangeMap) {
				if((idRangeMap.get(file)) == null)
					idRangeMap.wait();
				
				firstId = (Long)idRangeMap.get(file)[0];
				lastId = (Long)idRangeMap.get(file)[1];
			}

		} else {

			if(sleuthCase == null)
				if (new File(dbPath).exists())
					sleuthCase = SleuthkitCase.openCase(dbPath);
	
				else {
					IndexFiles.getInstance().firePropertyChange("mensagem", "", "Criando " + dbPath);
					LOGGER.info("Criando {}", dbPath);
					sleuthCase = SleuthkitCase.newCase(dbPath);
					LOGGER.info("{} criado", dbPath);
				}

			if (!file.getName().equals(DB_NAME)) {

				firstId = sleuthCase.getLastObjectId() + 1;
				try {
					sleuthCase.acquireExclusiveLock();
					
					IndexFiles.getInstance().firePropertyChange("mensagem", "", "Aguarde, decodificando imagem " + imgPath[0]);
					LOGGER.info("Decodificando imagem {}", imgPath[0]);

					TimeZone timezone = TimeZone.getDefault();
					boolean processUnallocSpace = Configuration.addUnallocated;
					boolean noFatFsOrphans = !Configuration.addFatOrphans;

					addImage = sleuthCase.makeAddImageProcess(timezone.toString(), processUnallocSpace, noFatFsOrphans);
					addImage.run(imgPath);

				} catch (Exception e) {
					LOGGER.error("Erro do Sleuthkit ao adicionar imagem " + imgPath[0], e);
					//e.printStackTrace();

				} finally {
					firstId = addImage.commit();
					LOGGER.info("Imagem {} decodificada.", imgPath[0]);
					sleuthCase.releaseExclusiveLock();
				}
				

			} else
				firstId = 0L;
			
			lastId = sleuthCase.getLastObjectId();

			synchronized (idRangeMap) {
				Object[] ids = {firstId, lastId};
				idRangeMap.put(file, ids);
				idRangeMap.notify();
			}
			
		}

		java.util.logging.Logger.getLogger("org.sleuthkit").setLevel(java.util.logging.Level.SEVERE);
		
		deviceName = getEvidenceName(file);
		
		/* ordena os itens pelo primeiro setor utilizado, na tentativa de ler os itens
		 * na ordem em que aparecem fisicamente no HD, evitando seeks na imagem
		*/
		/*class ItemStart implements Comparable<ItemStart>{
		    int id;
		    long start = 0;
		    
            @Override
            public int compareTo(ItemStart o) {
                return start < o.start ? -1 : start > o.start ? 1 : 0; 
            }
		}
		
		ArrayList<ItemStart> items = new ArrayList<ItemStart>();
		for (long k = firstId; k <= lastId; k++) {
		    ItemStart item = new ItemStart();
		    item.id = (int)k;
		    List<TskFileRange> rangeList = sleuthCase.getFileRanges(k);
		    if(rangeList != null && !rangeList.isEmpty()){
		    	int i = 0;
		    	long start = rangeList.get(i).getByteStart();
		    	do{
		    		item.start = start;
		    	}while(++i < rangeList.size() && (start = rangeList.get(i).getByteStart()) < item.start);
		    }
		    items.add(item);
		}
		ItemStart[] itemArray = items.toArray(new ItemStart[0]);
		items = null;
		Arrays.sort(itemArray);
		
		for (ItemStart item : itemArray) {
		    long k = item.id;
		    Content content = sleuthCase.getContentById(k);
			if(content != null)
				addContent(content, null);
		}
		*/
		
		if(firstId != 0)
			recurseIntoContent(sleuthCase.getImageById(firstId), null);
		else
			for(Content child : sleuthCase.getImages())
				recurseIntoContent(child, null);
		/*
		for (long k = firstId; k <= lastId; k++) {
			Content content = sleuthCase.getContentById(k);
			if(content != null)
				addContent(content);
		}*/
		
		return 0;
		
	}
	
	private void recurseIntoContent(Content content, Content parent) throws Exception{
		
		addContent(content, parent);
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
	}
		
	private void addContent(Content content, Content parent) throws Exception{

			AbstractFile absFile = null;
			if(content instanceof AbstractFile)
				absFile = (AbstractFile)content;
			
			if(content != null && absFile == null)
				addEvidenceFile(content);
			
			if(Configuration.addUnallocated && absFile != null && absFile.getType().compareTo(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) == 0){
				
				long fragSize = Configuration.unallocatedFragSize;
				
				int fragNum = 0;
				for(long offset = 0; offset < absFile.getSize(); offset += fragSize){
					long len = offset + fragSize < absFile.getSize() ? fragSize : absFile.getSize() - offset;
					SleuthEvidenceFile frag = new SleuthEvidenceFile();
					String sufix = "";
					if(absFile.getSize() > fragSize){
						sufix = "-Frag" + fragNum++;
						frag.setFileOffset(offset);
					}
					frag.setName(absFile.getName() + sufix);
					frag.setLength(len);
					
					setPath(frag, absFile.getUniquePath() + sufix);
					
					frag.setMediaType(UNALLOCATED_MIMETYPE);
		            addEvidenceFile(absFile, frag, true, parent);
				}
				
				return;
				
			}
			
			if (absFile == null || absFile.getName().equals("$BadClus:$Bad"))
				return;
			
			addEvidenceFile(absFile, parent);
			
	}
	
	private void setPath(SleuthEvidenceFile evidence, String path){
		if(deviceName != null)
			path = path.replaceFirst("img_.+?\\/" , deviceName + "/");
		evidence.setPath(path);
	}
	
	
	private void addEvidenceFile(AbstractFile absFile, Content parent) throws Exception{
		addEvidenceFile(absFile, null, false, parent);
	}
	
	private void addEvidenceFile(AbstractFile absFile, SleuthEvidenceFile evidence, boolean unalloc, Content parent) throws Exception{
		
		if(absFile.isDir() && (absFile.getName().equals(".") || absFile.getName().equals("..")))
			return;
		
		if(Configuration.minOrphanSizeToIgnore != -1 && absFile.getUniquePath().contains("/$OrphanFiles/") && 
				absFile.getSize() >= Configuration.minOrphanSizeToIgnore)
			return;
		
		if(evidence == null){
			evidence = new SleuthEvidenceFile();
			evidence.setLength(absFile.getSize());
		}
		
		if (listOnly) {
			caseData.incDiscoveredEvidences(1);
			caseData.incDiscoveredVolume(evidence.getLength());
			return;
		}
		
		if(evidence.getName() == null){
			if(absFile.isRoot() && absFile.getName().isEmpty())
				evidence.setName("/");
			else
				evidence.setName(absFile.getName());
			
			setPath(evidence, absFile.getUniquePath());
		}
		
		if(absFile.isDir()){
			evidence.setIsDir(true);
			evidence.setCategory(SetCategoryTask.FOLDER_CATEGORY);
		}
		
		evidence.setHasChildren(absFile.hasChildren());
		evidence.setSleuthFile(absFile);
		evidence.setSleuthId(Long.toString(absFile.getId()));
		
		int sleuthId = (int) (absFile.getId() - firstId);
		while(sleuthId >= sleuthIdToId.size())
			sleuthIdToId.add(0);
		sleuthIdToId.set(sleuthId, evidence.getId());
		
		Integer parentId = sleuthIdToId.get((int)(parent.getId() - firstId));
		evidence.setParentId(parentId.toString());
		
		while(evidence.getId() >= parentIds.size())
			parentIds.add(-1);
		parentIds.set(evidence.getId(), parentId);
		
		do{
			evidence.addParentId(parentId);
		}while((parentId = parentIds.get(parentId)) != -1);
		
		if (unalloc || absFile.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC) || 
				absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.UNALLOC) || 
				absFile.isMetaFlagSet(TSK_FS_META_FLAG_ENUM.ORPHAN))
			evidence.setDeleted(true);

		long time = absFile.getAtime();
		if (time != 0)
			evidence.setAccessDate(new Date(time * 1000));
		time = absFile.getMtime();
		if (time != 0)
			evidence.setModificationDate(new Date(time * 1000));
		time = absFile.getCrtime();
		if (time != 0)
			evidence.setCreationDate(new Date(time * 1000));

		caseData.addEvidenceFile(evidence);
		
	}
	
	private void addEvidenceFile(Content content) throws Exception{
		
		SleuthEvidenceFile evidence = new SleuthEvidenceFile();
		
		//Comentado pois tamanho da imagem e partições distorcem estimativa de progresso
		//evidence.setLength(content.getSize());
		
		if (listOnly) {
			caseData.incDiscoveredEvidences(1);
			//caseData.incDiscoveredVolume(evidence.getLength());
			return;
		}
		
		if(content.getName().isEmpty()){
			if(content instanceof VolumeSystem){ 
				evidence.setName(((VolumeSystem)content).getType().getName() + "_Partition_Table");
				
			}else if(content instanceof FileSystem){
				String fsName = ((FileSystem)content).getFsType().name();
				fsName = fsName.replace("TSK_FS_TYPE_", "");
				evidence.setName(fsName);
			}
				
		}else{
			if(deviceName != null && content instanceof Image)
				evidence.setName(deviceName);
			else
				evidence.setName(content.getName());
		}
			
		// evidence.setType(new UnknownFileType(evidence.getExt()));
		String path = content.getUniquePath();
		if(deviceName != null){
			if(path.indexOf('/', 1) == -1)
				evidence.setPath("/" + deviceName);
			else
				setPath(evidence, path);
		}else
			evidence.setPath(path);
		
		if(content instanceof Image)
			evidence.setRoot(true);
		else
			evidence.setIsDir(true);
		
		evidence.setHasChildren(content.hasChildren());
		
		//evidence.setSleuthFile(absFile);
		evidence.setSleuthId(Long.toString(content.getId()));
		
		int sleuthId = (int) (content.getId() - firstId);
		while(sleuthId >= sleuthIdToId.size())
			sleuthIdToId.add(0);
		sleuthIdToId.set(sleuthId, evidence.getId());
		
		Content parent = content.getParent();
		Integer parentId = -1;
		if(parent != null){	
			parentId = sleuthIdToId.get((int)(parent.getId() - firstId));
			evidence.setParentId(parentId.toString());
		}
		
		while(evidence.getId() >= parentIds.size())
			parentIds.add(-1);
		parentIds.set(evidence.getId(), parentId);
		
		while(parentId != -1){
			evidence.addParentId(parentId);
			parentId = parentIds.get(parentId);
		}
		
		caseData.addEvidenceFile(evidence);
		
	}
	

}
