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

import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.Util;

public class ExportFilesToZip extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {

  ArrayList<Integer> uniqueIds;
  File file, subdir;
  ProgressMonitor progressMonitor;

  public ExportFilesToZip(File file, ArrayList<Integer> uniqueIds) {
    this.file = file;
    this.uniqueIds = uniqueIds;

    progressMonitor = new ProgressMonitor(App.get(), "", "", 0, uniqueIds.size());
    this.addPropertyChangeListener(this);
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    
    if(!file.getName().toLowerCase().endsWith(".zip"))
    	file = new File(file.getAbsolutePath() + ".zip");
    
    ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(file);
    byte[] buf = new byte[8 * 1024 * 1024];
    int subdir = 0;
    int progress = 0;
    
    for (Integer docId : uniqueIds) {
      try {
    	if (progress % 1000 == 0){
    	  subdir++;
    	  ZipArchiveEntry entry = new ZipArchiveEntry(subdir + "/");
    	  zaos.putArchiveEntry(entry);
    	  zaos.closeArchiveEntry();
    	}
        Document doc = App.get().appCase.getSearcher().doc(docId);
        String dstName = doc.get(IndexItem.NAME);
        //dstName += "." + doc.get(IndexItem.TYPE);

        InputStream in = App.get().appCase.getItemByLuceneID(docId).getBufferedStream();

        ZipArchiveEntry entry = new ZipArchiveEntry(subdir + "/" + dstName);
        
        String lenStr = doc.get(IndexItem.LENGTH);
        if(lenStr != null && !lenStr.isEmpty())
        	entry.setSize(Long.parseLong(lenStr));
        
        zaos.putArchiveEntry(entry);
        int len = 0;
        while((len = in.read(buf)) != -1)
        	zaos.write(buf, 0, len);
        zaos.closeArchiveEntry();
        in.close();

      } catch (Exception e1) {
        e1.printStackTrace();
      }

      this.firePropertyChange("progress", progress, ++progress);

      if (this.isCancelled()) {
        break;
      }
    }
    zaos.close();

    return null;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("progress" == evt.getPropertyName()) {
      int progress = (Integer) evt.getNewValue();
      progressMonitor.setProgress(progress);
      progressMonitor.setNote("Copiando " + progress + " de " + uniqueIds.size());
    }
    if (progressMonitor.isCanceled()) {
      this.cancel(true);
    }

  }

}
