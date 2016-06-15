/*
 * Copyright 2012-2016, Wladimir Leite
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

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe base de tarefas de carving. 
 * Centraliza contador de itens carveados e outros métodos comuns.
 */
public abstract class BaseCarveTask extends AbstractTask {
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

  protected void addCarvedFile(EvidenceFile parentEvidence, long off, long len, String name, MediaType mediaType) throws Exception {
    EvidenceFile carvedEvidence = new EvidenceFile();
    carvedEvidence.setName(name);
    carvedEvidence.setPath(parentEvidence.getPath() + ">>" + name);
    len = Math.min(len, parentEvidence.getLength() - off);
    carvedEvidence.setLength(len);

    int parentId = parentEvidence.getId();
    carvedEvidence.setParentId(Integer.toString(parentId));
    carvedEvidence.addParentIds(parentEvidence.getParentIds());
    carvedEvidence.addParentId(parentId);

    carvedEvidence.setDeleted(parentEvidence.isDeleted());
    carvedEvidence.setCarved(true);
    if (mediaType != null) carvedEvidence.setMediaType(mediaType);

    long prevOff = parentEvidence.getFileOffset();
    carvedEvidence.setFileOffset(prevOff == -1 ? off : prevOff + off);

    if (parentEvidence.getSleuthFile() != null) {
      carvedEvidence.setSleuthFile(parentEvidence.getSleuthFile());
      carvedEvidence.setSleuthId(parentEvidence.getSleuthId());
      if (parentEvidence.hasTmpFile()) {
        carvedEvidence.setFile(parentEvidence.getTempFile());
        carvedEvidence.setTempStartOffset(off);
      }
    } else {
      carvedEvidence.setFile(parentEvidence.getFile());
      carvedEvidence.setExportedFile(parentEvidence.getExportedFile());
    }
    parentEvidence.setHasChildren(true);

    incItensCarved();

    // Caso o item pai seja um subitem a ser excluído pelo filtro de exportação, processa no worker atual
    if (ExportFileTask.hasCategoryToExtract() && parentEvidence.isSubItem() && !parentEvidence.isToExtract()) {
      caseData.incDiscoveredEvidences(1);
      worker.process(carvedEvidence);
    } else {
      worker.processNewItem(carvedEvidence);
    }
  }
}