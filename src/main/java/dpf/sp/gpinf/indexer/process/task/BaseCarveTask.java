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

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe base de tarefas de carving. 
 * Centraliza contador de itens carveados e outros métodos comuns.
 */
public abstract class BaseCarveTask extends AbstractTask {
	
  public static final String FILE_FRAGMENT = "fileFragment";
	
  private static int itensCarved;

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
  
  protected void addCarvedFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType){
	  EvidenceFile carvedEvidence = getOffsetFile(parentEvidence, off, len, name, mediaType);
	  carvedEvidence.setCarved(true);
  	  incItensCarved();
	  addOffsetFile(carvedEvidence, parentEvidence);
  }

  protected EvidenceFile getOffsetFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType){
	EvidenceFile offsetFile = new EvidenceFile();
    offsetFile.setName(name);
    offsetFile.setPath(parentEvidence.getPath() + ">>" + name);
    len = Math.min(len, parentEvidence.getLength() - off);
    offsetFile.setLength(len);

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
  
  private void addOffsetFile(EvidenceFile offsetFile, EvidenceFile parentEvidence){
	// Caso o item pai seja um subitem a ser excluído pelo filtro de exportação, processa no worker atual
	    if (ExportFileTask.hasCategoryToExtract() && parentEvidence.isSubItem() && !parentEvidence.isToExtract()) {
	      caseData.incDiscoveredEvidences(1);
	      worker.process(offsetFile);
	    } else {
	      worker.processNewItem(offsetFile);
	    }
  }
}