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
package dpf.sp.gpinf.indexer.desktop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.search.ItemId;
import gpinf.dev.data.EvidenceFile;

public class ExportFilesToZip extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {
	
  private static Logger LOGGER = LoggerFactory.getLogger(ExportFilesToZip.class);

  ArrayList<ItemId> uniqueIds;
  File file, subdir;
  ProgressMonitor progressMonitor;

  public ExportFilesToZip(File file, ArrayList<ItemId> uniqueIds) {
    this.file = file;
    this.uniqueIds = uniqueIds;

    progressMonitor = new ProgressMonitor(App.get(), "", "", 0, uniqueIds.size()); //$NON-NLS-1$ //$NON-NLS-2$
    this.addPropertyChangeListener(this);
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    
    if(!file.getName().toLowerCase().endsWith(".zip")) //$NON-NLS-1$
    	file = new File(file.getAbsolutePath() + ".zip"); //$NON-NLS-1$
    
    LOGGER.info("Exporting files to " + file.getAbsolutePath()); //$NON-NLS-1$
    
    ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(file);
    byte[] buf = new byte[8 * 1024 * 1024];
    int subdir = 0;
    int progress = 0;
    
    for (ItemId item : uniqueIds) {
      try {
    	if (progress % 1000 == 0){
    	  subdir++;
    	  ZipArchiveEntry entry = new ZipArchiveEntry(subdir + "/"); //$NON-NLS-1$
    	  zaos.putArchiveEntry(entry);
    	  zaos.closeArchiveEntry();
    	}
    	
    	EvidenceFile e = App.get().appCase.getItemByItemId(item);
        String dstName = e.getName();
        //dstName += "." + doc.get(IndexItem.TYPE);
        
        ZipArchiveEntry entry = new ZipArchiveEntry(subdir + "/" + dstName); //$NON-NLS-1$
        
        if(e.getModDate() != null)
            entry.setTime(e.getModDate().getTime());
        
        if(e.getLength() != null)
        	entry.setSize(e.getLength());
        
        zaos.putArchiveEntry(entry);
        
        LOGGER.info("Exporting file " + e.getPath()); //$NON-NLS-1$
        
        try (InputStream in = e.getBufferedStream()){
            int len = 0;
            while((len = in.read(buf)) != -1 && !this.isCancelled())
                zaos.write(buf, 0, len);
        }finally{
            zaos.closeArchiveEntry();
        }

      } catch (Exception e1) {
        e1.printStackTrace();
      }

      this.firePropertyChange("progress", progress, ++progress); //$NON-NLS-1$

      if (this.isCancelled()) {
        break;
      }
    }
    zaos.close();

    return null;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
      int progress = (Integer) evt.getNewValue();
      progressMonitor.setProgress(progress);
      progressMonitor.setNote(Messages.getString("ExportFilesToZip.Copying") + progress + Messages.getString("ExportFilesToZip.from") + uniqueIds.size()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    if (progressMonitor.isCanceled()) {
      this.cancel(false);
    }

  }

}
