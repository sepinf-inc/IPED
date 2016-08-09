/*
 * Copyright 2012-2016, Wladimir Leite, Luis Filipe Nassif
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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe base de tarefas de carving. 
 * Centraliza contador de itens carveados e outros métodos comuns.
 */
public abstract class BaseCarveTask extends AbstractTask {
	
  protected static MediaType mtPageFile = MediaType.application("x-pagefile");
  protected static MediaType mtVolumeShadow = MediaType.application("x-volume-shadow");
  protected static MediaType mtDiskImage = MediaType.application("x-disk-image");
  protected static MediaType mtVmdk = MediaType.application("x-vmdk");
  protected static MediaType mtVhd = MediaType.application("x-vhd");
  protected static MediaType mtVdi = MediaType.application("x-vdi");
  protected static MediaType mtUnknown = MediaType.application("octet-stream");

  public static final String FILE_FRAGMENT = "fileFragment";

  protected static HashSet<MediaType> TYPES_TO_PROCESS;
  protected static HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
  protected static HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
  
  private static int itensCarved;
  
  private Set<Long> kffCarvedOffsets;
  private EvidenceFile prevEvidence;

  protected static final Map<EvidenceFile, Set<Long>> kffCarved = new HashMap<EvidenceFile, Set<Long>>();   

  public BaseCarveTask(Worker worker) {
    super(worker);
  }

  private final synchronized static void incItensCarved() {
    itensCarved++;
  }

  public final synchronized static int getItensCarved() {
    return itensCarved;
  }
  
  protected void addFragmentFile(EvidenceFile parentEvidence, long off, long len, int fragNum){
      String name = parentEvidence.getName() + "_" + fragNum;
	  EvidenceFile fragFile = getOffsetFile(parentEvidence, off, len, name, parentEvidence.getMediaType());
	  fragFile.setExtension(parentEvidence.getExt());
	  fragFile.setAccessDate(parentEvidence.getAccessDate());
	  fragFile.setCreationDate(parentEvidence.getCreationDate());
	  fragFile.setModificationDate(parentEvidence.getModDate());
	  fragFile.setExtraAttribute(FILE_FRAGMENT, true);
	  addOffsetFile(fragFile, parentEvidence);
  }
  
  protected EvidenceFile addCarvedFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType){
    EvidenceFile carvedEvidence = createCarvedFile(parentEvidence, off, len, name, mediaType);
	  if (carvedEvidence != null) addOffsetFile(carvedEvidence, parentEvidence);
	  return carvedEvidence;
  }

  protected EvidenceFile createCarvedFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType){
    if (!parentEvidence.equals(prevEvidence)) {
      synchronized (kffCarved) {
        kffCarvedOffsets = kffCarved.get(parentEvidence);
      }
      prevEvidence = parentEvidence;
    }
    if (kffCarvedOffsets != null && kffCarvedOffsets.contains(off)) {
      return null;
    }

    EvidenceFile carvedEvidence = getOffsetFile(parentEvidence, off, len, name, mediaType);
    carvedEvidence.setCarved(true);
    incItensCarved();
    return carvedEvidence;
  }

  protected EvidenceFile getOffsetFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType){
	EvidenceFile offsetFile = new EvidenceFile();
    offsetFile.setName(name);
    offsetFile.setPath(parentEvidence.getPath() + ">>" + name);
    len = Math.min(len, parentEvidence.getLength() - off);
    offsetFile.setLength(len);
    offsetFile.setSumVolume(false);
    offsetFile.setParent(parentEvidence);

    offsetFile.setDeleted(parentEvidence.isDeleted());
    
    if (mediaType != null) offsetFile.setMediaType(mediaType);

    long prevOff = parentEvidence.getFileOffset();
    offsetFile.setFileOffset(prevOff == -1 ? off : prevOff + off);

    if (parentEvidence.getSleuthFile() != null) {
      offsetFile.setSleuthFile(parentEvidence.getSleuthFile());
      offsetFile.setSleuthId(parentEvidence.getSleuthId());
      if (parentEvidence.hasTmpFile()) {
        try {
			offsetFile.setFile(parentEvidence.getTempFile());
			offsetFile.setTempStartOffset(off);
		} catch (IOException e) {
			//ignore
		}
      }
    } else {
      offsetFile.setFile(parentEvidence.getFile());
      offsetFile.setExportedFile(parentEvidence.getExportedFile());
    }
    parentEvidence.setHasChildren(true);
    
    return offsetFile;
  }
  
  protected void addOffsetFile(EvidenceFile offsetFile, EvidenceFile parentEvidence){
	// Caso o item pai seja um subitem a ser excluído pelo filtro de exportação, processa no worker atual
	    if (ExportFileTask.hasCategoryToExtract() && parentEvidence.isSubItem() && !parentEvidence.isToExtract()) {
	      caseData.incDiscoveredEvidences(1);
	      worker.process(offsetFile);
	    } else {
	      worker.processNewItem(offsetFile);
	    }
  }
  
  protected boolean isToProcess(EvidenceFile evidence) {
    if (evidence.isCarved() || evidence.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) != null
        || (TYPES_TO_PROCESS != null && !TYPES_TO_PROCESS.contains(evidence.getMediaType()))) {
      return false;
    }
    return true;
  }
}