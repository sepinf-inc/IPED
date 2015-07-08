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

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_FLAG_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;

public class SleuthkitProcessor {

	public static String DB_NAME = "sleuth.db";
	private Long firstId , lastId;
	//private static Object lock = new Object();
	private static ConcurrentHashMap<File, Object[]> idRangeMap = new ConcurrentHashMap<File, Object[]>();
	private ArrayList<Integer> sleuthIdToId = new ArrayList<Integer>();
	
	public static MediaType UNALLOCATED_MIMETYPE = CarveTask.UNALLOCATED_MIMETYPE;

	private CaseData caseData;
	private boolean listOnly;
	private File output;
	private AddImageProcess addImage;
	private String deviceName;
	
	//Referência estática para a JVM não finalizar o objeto que será usado futuramente
	//via referência interna ao JNI para acessar os itens do caso
	static SleuthkitCase sleuthCase;

	public SleuthkitProcessor(CaseData caseData, File output, boolean listOnly) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.output = output;
	}

	public static boolean isSupported(File report) {
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

	public void process(File file) throws Exception {

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
					System.out.println(new Date() + "\t[INFO]\t" + "Criando " + dbPath);
					sleuthCase = SleuthkitCase.newCase(dbPath);
					System.out.println(new Date() + "\t[INFO]\t" + dbPath + " criado.");
				}

			if (!file.getName().equals(DB_NAME)) {

				firstId = sleuthCase.getLastObjectId() + 1;
				try {
					sleuthCase.acquireExclusiveLock();
					
					IndexFiles.getInstance().firePropertyChange("mensagem", "", "Aguarde, adicionando imagem " + imgPath[0]);
					System.out.println(new Date() + "\t[INFO]\t" + "Adicionando imagem " + imgPath[0]);

					TimeZone timezone = TimeZone.getDefault();
					boolean processUnallocSpace = Configuration.addUnallocated;
					boolean noFatFsOrphans = !Configuration.addFatOrphans;

					addImage = sleuthCase.makeAddImageProcess(timezone.toString(), processUnallocSpace, noFatFsOrphans);
					addImage.run(imgPath);

				} catch (Exception e) {
					System.out.println(new Date() + "\t[ALERTA]\t" + "Erro do Sleuthkit ao processar imagem " + imgPath[0]);
					e.printStackTrace();

				} finally {
					addImage.commit();
					System.out.println(new Date() + "\t[INFO]\t" + "Imagem " + imgPath[0] + " adicionada.");
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

		Logger.getLogger("org.sleuthkit").setLevel(Level.SEVERE);
		
		CmdLineArgs cmdArgs = ((CmdLineArgs)caseData.getCaseObject(CmdLineArgs.class.getName()));
		List<String> dnames = cmdArgs.getCmdArgs().get("-dname");
		List<String> sources = cmdArgs.getCmdArgs().get("-d");
		if(dnames != null)
			for(int i = 0; i < sources.size(); i++)
				if(sources.get(i).endsWith(file.getName())){
					deviceName = dnames.get(i);
					break;
				}

		for (long k = firstId; k <= lastId; k++) {

			AbstractFile absFile = null;
			Content content = null;
			
			try{
				absFile = sleuthCase.getAbstractFileById(k);
				if(absFile == null)
					content = sleuthCase.getContentById(k);
				
			}catch(TskCoreException e){
				//Tenta continuar após erro de banco corrompido
				continue;
			}
			
			if(content != null)
				addEvidenceFile(content);
			
			if(Configuration.addUnallocated && absFile != null && absFile.getType().compareTo(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) == 0){
				
				long fragSize = Configuration.unallocatedFragSize;
				
				int fragNum = 0;
				for(long offset = 0; offset < absFile.getSize(); offset += fragSize){
					long len = offset + fragSize < absFile.getSize() ? fragSize : absFile.getSize() - offset;
					EvidenceFile frag = new EvidenceFile();
					String sufix = "";
					if(absFile.getSize() > fragSize){
						sufix = "-Frag" + fragNum++;
						frag.setFileOffset(offset);
					}
					frag.setName(absFile.getName() + sufix);
					frag.setLength(len);
					
					setPath(frag, absFile.getUniquePath() + sufix);
					
					frag.setMediaType(UNALLOCATED_MIMETYPE);
		            addEvidenceFile(absFile, frag, true);
				}
				
				continue;
				
			}
			
			if (absFile == null || absFile.getName().equals("$BadClus:$Bad"))
				continue;
			
			addEvidenceFile(absFile);
			
		}
		
		
	}
	
	private void setPath(EvidenceFile evidence, String path){
		if(deviceName != null)
			path = path.replaceFirst("img_.+?\\/" , deviceName + "/");
		evidence.setPath(path);
	}
	
	
	private void addEvidenceFile(AbstractFile absFile) throws Exception{
		addEvidenceFile(absFile, null, false);
	}
	
	private void addEvidenceFile(AbstractFile absFile, EvidenceFile evidence, boolean unalloc) throws Exception{
		
		if(absFile.isDir() && (absFile.getName().equals(".") || absFile.getName().equals("..")))
			return;
		
		if(evidence == null){
			evidence = new EvidenceFile();
			if(absFile.isRoot() && absFile.getName().isEmpty())
				evidence.setName("/");
			else
				evidence.setName(absFile.getName());
			
			evidence.setLength(absFile.getSize());
			// evidence.setType(new UnknownFileType(evidence.getExt()));
			setPath(evidence, absFile.getUniquePath());
		}
		
		if (listOnly) {
			caseData.incDiscoveredEvidences(1);
			caseData.incDiscoveredVolume(evidence.getLength());
			return;
		}
		
		if(absFile.isDir()){
			evidence.setIsDir(true);
			evidence.setCategory(SetCategoryTask.FOLDER_CATEGORY);
		}
		
		evidence.setHasChildren(absFile.hasChildren());
		Content parent = absFile.getParent();
		
		evidence.setSleuthFile(absFile);
		evidence.setSleuthId(Long.toString(absFile.getId()));
		
		int sleuthId = (int) (absFile.getId() - firstId);
		while(sleuthId >= sleuthIdToId.size())
			sleuthIdToId.add(0);
		sleuthIdToId.set(sleuthId, evidence.getId());
		
		evidence.setParentId(sleuthIdToId.get((int)(parent.getId() - firstId)).toString());
		do{
			evidence.addParentId(sleuthIdToId.get((int)(parent.getId() - firstId)));
		}while((parent = parent.getParent()) != null);
		
		
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
		
		EvidenceFile evidence = new EvidenceFile();
		
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
		if(parent != null){	
			evidence.setParentId(sleuthIdToId.get((int)(parent.getId() - firstId)).toString());
			do{
				evidence.addParentId(sleuthIdToId.get((int)(parent.getId() - firstId)));
			}while((parent = parent.getParent()) != null);
			
		}
		
		caseData.addEvidenceFile(evidence);
		
	}
	

}
