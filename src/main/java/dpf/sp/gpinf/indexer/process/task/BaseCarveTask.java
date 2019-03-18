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

import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.process.Worker.ProcessTime;
import gpinf.dev.data.ItemImpl;
import iped3.Item;
import iped3.sleuthkit.SleuthKitItem;

/**
 * Classe base de tarefas de carving. 
 * Centraliza contador de itens carveados e outros métodos comuns.
 */
public abstract class BaseCarveTask extends AbstractTask {
	
  protected static MediaType mtPageFile = MediaType.application("x-pagefile"); //$NON-NLS-1$
  protected static MediaType mtVolumeShadow = MediaType.application("x-volume-shadow"); //$NON-NLS-1$
  protected static MediaType mtDiskImage = MediaType.application("x-disk-image"); //$NON-NLS-1$
  protected static MediaType mtVmdk = MediaType.application("x-vmdk"); //$NON-NLS-1$
  protected static MediaType mtVhd = MediaType.application("x-vhd"); //$NON-NLS-1$
  protected static MediaType mtVdi = MediaType.application("x-vdi"); //$NON-NLS-1$
  protected static MediaType mtUnknown = MediaType.application("octet-stream"); //$NON-NLS-1$

  public static final String FILE_FRAGMENT = "fileFragment"; //$NON-NLS-1$

  protected static HashSet<MediaType> TYPES_TO_PROCESS;
  protected static HashSet<String> TYPES_TO_NOT_PROCESS = new HashSet<String>();
  protected static HashSet<MediaType> TYPES_TO_CARVE = new HashSet<MediaType>();
  
  private static int itensCarved;
  
  private Set<Long> kffCarvedOffsets;
  private Item prevEvidence;

  protected static final Map<Item, Set<Long>> kffCarved = new HashMap<Item, Set<Long>>();   

  private final synchronized static void incItensCarved() {
    itensCarved++;
  }

  public final synchronized static int getItensCarved() {
    return itensCarved;
  }
  
  protected void addFragmentFile(Item parentEvidence, long off, long len, int fragNum){
      String name = parentEvidence.getName() + "_" + fragNum; //$NON-NLS-1$
	  Item fragFile = getOffsetFile(parentEvidence, off, len, name, parentEvidence.getMediaType());
	  fragFile.setExtension(parentEvidence.getExt());
	  fragFile.setAccessDate(parentEvidence.getAccessDate());
	  if(parentEvidence.getExtraAttribute(SleuthkitReader.IN_FAT_FS) != null)
		  fragFile.setExtraAttribute(SleuthkitReader.IN_FAT_FS, true);
	  fragFile.setCreationDate(parentEvidence.getCreationDate());
	  fragFile.setModificationDate(parentEvidence.getModDate());
	  fragFile.setRecordDate(parentEvidence.getRecordDate());
	  fragFile.setExtraAttribute(FILE_FRAGMENT, true);
	  addOffsetFile(fragFile, parentEvidence);
  }
  
  protected Item addCarvedFile(Item parentEvidence, long off, long len, String name, MediaType mediaType){
      Item carvedEvidence = createCarvedFile(parentEvidence, off, len, name, mediaType);
	  if (carvedEvidence != null) addOffsetFile(carvedEvidence, parentEvidence);
	  return carvedEvidence;
  }

  protected Item createCarvedFile(Item parentEvidence, long off, long len, String name, MediaType mediaType){
    if (!parentEvidence.equals(prevEvidence)) {
      synchronized (kffCarved) {
        kffCarvedOffsets = kffCarved.get(parentEvidence);
      }
      prevEvidence = parentEvidence;
    }
    if (kffCarvedOffsets != null && kffCarvedOffsets.contains(off)) {
      return null;
    }

    Item carvedEvidence = getOffsetFile(parentEvidence, off, len, name, mediaType);
    carvedEvidence.setCarved(true);
    incItensCarved();
    return carvedEvidence;
  }

  protected Item getOffsetFile(Item parentEvidence, long off, long len, String name, MediaType mediaType){
    ItemImpl offsetFile = new ItemImpl();
    offsetFile.setName(name);
    offsetFile.setPath(parentEvidence.getPath() + ">>" + name); //$NON-NLS-1$
    len = Math.min(len, parentEvidence.getLength() - off);
    offsetFile.setLength(len);
    offsetFile.setSumVolume(false);
    offsetFile.setParent(parentEvidence);

    offsetFile.setDeleted(parentEvidence.isDeleted());
    
    if (mediaType != null) offsetFile.setMediaType(mediaType);

    long prevOff = parentEvidence.getFileOffset();
    offsetFile.setFileOffset(prevOff == -1 ? off : prevOff + off);
    
    if (parentEvidence instanceof SleuthKitItem && ((SleuthKitItem)parentEvidence).getSleuthFile() != null) {
      offsetFile.setSleuthFile(((SleuthKitItem)parentEvidence).getSleuthFile());
      offsetFile.setSleuthId(((SleuthKitItem)parentEvidence).getSleuthId());
      
    } else {
      offsetFile.setFile(parentEvidence.getFile());
      offsetFile.setExportedFile(parentEvidence.getExportedFile());
    }
    //optimization to not create more temp files
    if (parentEvidence.hasTmpFile()) {
        try {
            offsetFile.setFile(parentEvidence.getTempFile());
            offsetFile.setTempStartOffset(off);
        } catch (IOException e) {
            //ignore
        }
    }
    parentEvidence.setHasChildren(true);
    
    return offsetFile;
  }
  
  protected void addOffsetFile(Item offsetFile, Item parentEvidence){
	// Caso o item pai seja um subitem a ser excluído pelo filtro de exportação, processa no worker atual
    boolean processNow = parentEvidence.isSubItem() && !parentEvidence.isToAddToCase();
    ProcessTime time = processNow ? ProcessTime.NOW : ProcessTime.AUTO;
	worker.processNewItem(offsetFile, time);
  }
  
  protected boolean isToProcess(Item evidence) {
    if (evidence.isCarved() || evidence.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) != null
        || (TYPES_TO_PROCESS != null && !TYPES_TO_PROCESS.contains(evidence.getMediaType()))) {
      return false;
    }
    return true;
  }
}