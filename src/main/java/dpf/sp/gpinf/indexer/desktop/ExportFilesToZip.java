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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;

import dpf.sp.gpinf.indexer.search.ItemId;
import gpinf.dev.data.EvidenceFile;

public class ExportFilesToZip extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {
	
  private static Logger LOGGER = LoggerFactory.getLogger(ExportFilesToZip.class);

  ArrayList<ItemId> uniqueIds;
  File file, subdir;
  ProgressMonitor progressMonitor;
  HashingOutputStream hos;
  volatile boolean error;

  public ExportFilesToZip(File file, ArrayList<ItemId> uniqueIds) {
    this.file = file;
    this.uniqueIds = uniqueIds;

    progressMonitor = new ProgressMonitor(App.get(), "", "", 0, uniqueIds.size()); //$NON-NLS-1$ //$NON-NLS-2$
    this.addPropertyChangeListener(this);
  }

  @Override
  protected Boolean doInBackground() {
    
    if(!file.getName().toLowerCase().endsWith(".zip")) //$NON-NLS-1$
    	file = new File(file.getAbsolutePath() + ".zip"); //$NON-NLS-1$
    
    LOGGER.info("Exporting files to " + file.getAbsolutePath()); //$NON-NLS-1$
    
    try {
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
    hos = new HashingOutputStream(Hashing.md5(), bos);
    ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(hos);
    
    byte[] buf = new byte[8 * 1024 * 1024];
    int subdir = 0;
    int progress = 0;
    
      for (ItemId item : uniqueIds) {
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
        
        fillZipDates(entry, e);
        
        if(e.getLength() != null)
        	entry.setSize(e.getLength());
        
        zaos.putArchiveEntry(entry);
        
        LOGGER.info("Exporting file " + e.getPath()); //$NON-NLS-1$
        
        try (InputStream in = e.getBufferedStream()){
            int len = 0;
            while((len = in.read(buf)) != -1 && !this.isCancelled())
                try {
                    zaos.write(buf, 0, len);
                }catch(IOException e0) {
                    e0.printStackTrace();
                    ExportFileTree.showErrorMessage(e0);
                    error = true;
                    return null;
                }
        }catch(IOException e0) {
            e0.printStackTrace();
        }
        
        zaos.closeArchiveEntry();

        this.firePropertyChange("progress", progress, ++progress); //$NON-NLS-1$

        if (this.isCancelled()) {
          break;
        }
      }
      zaos.close();
    
    } catch (IOException e) {
        e.printStackTrace();
        error = true;
        ExportFileTree.showErrorMessage(e);
    }

    return null;
  }
  
  public static void fillZipDates(ZipArchiveEntry entry, EvidenceFile item) {
      
      X5455_ExtendedTimestamp extendedDates = new X5455_ExtendedTimestamp();
      X000A_NTFS ntfsDates = new X000A_NTFS();
      
      if(item.getAccessDate() != null) {
          entry.setLastAccessTime(FileTime.fromMillis(item.getAccessDate().getTime()));
          //above do not work until compress-1.17, so set manually:
          extendedDates.setAccessJavaTime(item.getAccessDate());
          extendedDates.setFlags(X5455_ExtendedTimestamp.ACCESS_TIME_BIT);
          ntfsDates.setAccessJavaTime(item.getAccessDate());
      }
      
      if(item.getCreationDate() != null) {
          entry.setCreationTime(FileTime.fromMillis(item.getCreationDate().getTime()));
          //above do not work until compress-1.17, so set manually:
          extendedDates.setCreateJavaTime(item.getCreationDate());
          extendedDates.setFlags(X5455_ExtendedTimestamp.CREATE_TIME_BIT);
          ntfsDates.setCreateJavaTime(item.getCreationDate());
      }
      
      if(item.getModDate() != null) {
          entry.setTime(item.getModDate().getTime());
          entry.setLastModifiedTime(FileTime.fromMillis(item.getModDate().getTime()));
          extendedDates.setModifyJavaTime(item.getModDate());
          extendedDates.setFlags(X5455_ExtendedTimestamp.MODIFY_TIME_BIT);
          ntfsDates.setModifyJavaTime(item.getModDate());
      }
      
      entry.addExtraField(extendedDates);
      entry.addExtraField(ntfsDates);
  }
  
  @Override
  protected void done() {
      if(hos != null && !error) {
          String hash = hos.hash().toString().toUpperCase();
          LOGGER.info("MD5 of " + file.getAbsolutePath() + ": " + hash); //$NON-NLS-1$ //$NON-NLS-2$
          HashDialog dialog = new HashDialog(hash);
          dialog.setVisible(true);
      }
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
