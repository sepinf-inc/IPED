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
package dpf.sp.gpinf.indexer.search;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.TreeViewModel.Node;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;
import dpf.sp.gpinf.indexer.util.Util;

public class ExportFileTree extends CancelableWorker {

  int baseDocId;
  boolean onlyChecked;
  File baseDir;

  int total, progress = 0;
  ProgressDialog progressDialog;

  HashMap<Integer, File> parentCache = new HashMap<Integer, File>();

  static Node root = (Node) App.get().tree.getModel().getRoot();

  public ExportFileTree(File baseDir, int baseDocId, boolean onlyChecked) {
    this.baseDir = baseDir;
    this.baseDocId = baseDocId;
    this.onlyChecked = onlyChecked;
  }

  private int[] getItemsToExport() {

    try {
      Document doc = App.get().appCase.reader.document(baseDocId);

      String id = doc.get(IndexItem.FTKID);
      if (id == null) {
        id = doc.get(IndexItem.ID);
      }

      String textQuery = IndexItem.PARENTIDs + ":" + id + " " + IndexItem.ID + ":" + id;
      String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
      textQuery = IndexItem.EVIDENCE_UUID + ":" + sourceUUID + " && (" + textQuery + ")";

      IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
      SearchResult result = task.pesquisar();

      if (onlyChecked) {
    	  result = App.get().appCase.marcadores.filtrarSelecionados(result, App.get().appCase);
      }

      return result.docs;

    } catch (Exception e) {
      return new int[0];
    }
  }

  private void exportItem(int docId) {
    exportItem(docId, false);
  }

  private File exportItem(int docId, boolean isParent) {

    File exportedItem = null;
    if (docId == baseDocId) {
      exportedItem = exportItem(docId, baseDir, isParent);
      parentCache.put(docId, exportedItem);
    } else {
      try {
        Document doc = App.get().appCase.reader.document(docId);

        String parentIdStr = doc.get(IndexItem.PARENTID);
        int parentId = Integer.parseInt(parentIdStr);
        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        
        String textQuery = IndexItem.ID + ":" + parentId;
        textQuery = IndexItem.EVIDENCE_UUID + ":" + sourceUUID + " && (" + textQuery + ")";
        
        IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
        task.setTreeQuery(true);
        SearchResult parent = task.pesquisar();
        if (parent.length == 0) {
          return null;
        }

        int parentDocId = parent.docs[0];
        File exportedParent = parentCache.get(parentDocId);
        if (exportedParent == null) {
          exportedParent = exportItem(parentDocId, true);
          parentCache.put(parentDocId, exportedParent);
        }

        exportedItem = exportItem(docId, exportedParent, isParent);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return exportedItem;

  }

  private File getNonExistingFile(File dst) {
    int num = 1;
    String name = dst.getName();
    while (dst.exists()) {
      dst = new File(dst.getParentFile(), Util.concat(name, num++));
    }
    return dst;
  }

  private File getExistingOrNewDir(File dst) {
    int num = 1;
    String name = dst.getName();
    while (dst.exists() && !dst.isDirectory()) {
      dst = new File(dst.getParentFile(), Util.concat(name, num++));
    }
    return dst;
  }

  private File exportItem(int docId, File subdir, boolean isParent) {

    if (subdir == null) {
      return null;
    }

    File dst = null;
    EvidenceFile item = null;
    try {
      item = App.get().appCase.getItemByLuceneID(docId);

      String dstName = Util.getValidFilename(item.getName());
      dst = new File(subdir, dstName);

      if (item.isDir() || isParent) {
        if (!dst.isDirectory()) {
          dst = getExistingOrNewDir(dst);
          Files.createDirectories(dst.toPath());
        }
      } else {
        try (InputStream in = item.getBufferedStream()) {
          dst = getNonExistingFile(dst);
          Files.copy(in, dst.toPath());
        }
      }

    } catch (Exception e1) {
      e1.printStackTrace();
      
    } finally {
      item.dispose();
    }

    if (!isParent) {
      progressDialog.setProgress(++progress);
      progressDialog.setNote("Copiados " + progress + " de " + total);
    }

    return dst;

  }

  @Override
  protected Boolean doInBackground() throws Exception {

    progressDialog = new ProgressDialog(null, this);

    int[] docIds = getItemsToExport();
    total = docIds.length;
    progressDialog.setMaximum(total);

    try {
      for (int docId : docIds) {
        exportItem(docId);
        if (progressDialog.isCanceled()) {
          break;
        }
      }

    } finally {
      progressDialog.close();
    }

    return null;
  }

  public static void salvarArquivo(int baseDocId, boolean onlyChecked) {
    if (baseDocId == root.docId) {
      JOptionPane.showMessageDialog(null, "Selecione outro nó base diferente de " + root.toString());
      return;
    }
    try {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setFileFilter(null);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File baseDir = fileChooser.getSelectedFile();
        (new ExportFileTree(baseDir, baseDocId, onlyChecked)).execute();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
