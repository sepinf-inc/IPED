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
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;

public class CopiarArquivos extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {

  ArrayList<Integer> uniqueIds;
  File dir, subdir;
  ProgressMonitor progressMonitor;

  public CopiarArquivos(File dir, ArrayList<Integer> uniqueIds) {
    this.dir = dir;
    this.subdir = dir;
    this.uniqueIds = uniqueIds;

    progressMonitor = new ProgressMonitor(App.get(), "", "", 0, uniqueIds.size());
    this.addPropertyChangeListener(this);
  }

  private String addExtension(String srcName, String dstName) {
    int srcExtIndex;
    if ((srcExtIndex = srcName.lastIndexOf('.')) > -1) {
      String srcExt = srcName.substring(srcExtIndex);
      if (!dstName.endsWith(srcExt)) {
        if (srcName.endsWith(".[AD]" + srcExt)) {
          srcExt = ".[AD]" + srcExt;
        }
        dstName += srcExt;
      }
    }
    return dstName;
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    int progress = 0, subdirCount = 1;
    for (Integer docId : uniqueIds) {
      try {
        if (progress % 1000 == 0 && progress > 0) {
          do {
            subdir = new File(dir, Integer.toString(subdirCount++));
          } while (!subdir.mkdir());
        }

        Document doc = App.get().appCase.getSearcher().doc(docId);
        String dstName = Util.getValidFilename(doc.get(IndexItem.NAME));
        String export = doc.get(IndexItem.EXPORT);

        InputStream in;
        if (export != null && !export.isEmpty()) {
          File src = Util.getRelativeFile(App.get().codePath + "/../..", export);
          if (doc.get(IndexItem.OFFSET) == null) {
            dstName = addExtension(src.getName(), dstName);
          }
          in = Util.getStream(src, doc);
        } else {
          in = Util.getSleuthStream(App.get().appCase.getSleuthCase(), doc);
        }

        File dst = new File(subdir, dstName);
        int num = 1;
        while (dst.exists()) {
          dst = new File(subdir, Util.concat(dstName, num++));
        }

        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst));

        IOUtil.copiaArquivo(in, out);

        in.close();
        out.close();

      } catch (Exception e1) {
        e1.printStackTrace();
      }

      this.firePropertyChange("progress", progress, ++progress);

      if (this.isCancelled()) {
        break;
      }
    }

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

  public static void salvarArquivo(int docId) {
    try {
      ArrayList<Integer> uniqueDoc = new ArrayList<Integer>();
      uniqueDoc.add(docId);
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setFileFilter(null);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File dir = fileChooser.getSelectedFile();
        (new CopiarArquivos(dir, uniqueDoc)).execute();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
