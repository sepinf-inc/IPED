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

import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

public class ParentTableModel extends AbstractTableModel implements MouseListener, ListSelectionListener, SearchResultTableModel {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  // public ScoreDoc[] results = new ScoreDoc[0];
  SearchResult results = new SearchResult(0);
  int selectedIndex = -1;

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public int getRowCount() {
    return results.length;
  }
  
  @Override
  public String getColumnName(int col) {
    if (col == 2)
      return IndexItem.NAME;
    
    return "";
  }
  
  @Override
  public boolean isCellEditable(int row, int col) {
    if (col == 1) {
      return true;
    } else {
      return false;
    }
  }
  
  @Override
  public Class<?> getColumnClass(int c) {
    if (c == 1) {
      return Boolean.class;
    } else {
      return String.class;
    }
  }
  
  @Override
  public void setValueAt(Object value, int row, int col) {
    App.get().appCase.getMarcadores().setValueAtId(value, App.get().appCase.getIds()[results.docs[row]], col, true);
  }

  @Override
  public Object getValueAt(int row, int col) {
    if (col == 0) {
      return row + 1;
      
    }else if (col == 1) {
        return App.get().appCase.getMarcadores().selected[App.get().appCase.getIds()[results.docs[row]]];
      
    } else {
      try {
        Document doc = App.get().appCase.getSearcher().doc(results.docs[row]);
        return doc.get(IndexItem.NAME);
      } catch (Exception e) {
        // e.printStackTrace();
      }
      return "";
    }
  }
  
  @Override
	public SearchResult getSearchResult() {
		return results;
	}

  @Override
  public void mouseClicked(MouseEvent arg0) {
  }

  @Override
  public void mouseEntered(MouseEvent arg0) {
  }

  @Override
  public void mouseExited(MouseEvent arg0) {
  }

  @Override
  public void mousePressed(MouseEvent arg0) {
  }

  @Override
  public void mouseReleased(MouseEvent evt) {
    if (evt.getClickCount() == 2 && selectedIndex != -1) {

      new Thread() {
        public void run() {
          int docId = results.docs[selectedIndex];
          File file = null;
          try {
        	  EvidenceFile item = App.get().appCase.getItemByLuceneID(docId);
              file = item.getTempFile();
              file.setReadOnly();

            if (file != null) {
              Desktop.getDesktop().open(file.getCanonicalFile());
            }

          } catch (Exception e) {
            try {
              // Windows Only
              Runtime.getRuntime().exec(new String[]{"rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\""});
            } catch (Exception e2) {
              try {
                // Linux Only
                Runtime.getRuntime().exec(new String[]{"xdg-open", file.toURI().toURL().toString()});
              } catch (Exception e3) {
                CopiarArquivos.salvarArquivo(docId);
              }
            }
          }
        }
      }.start();

    }

  }

  @Override
  public void valueChanged(ListSelectionEvent evt) {
    ListSelectionModel lsm = (ListSelectionModel) evt.getSource();

    if (lsm.getMinSelectionIndex() == -1 || selectedIndex == lsm.getMinSelectionIndex()) {
      selectedIndex = lsm.getMinSelectionIndex();
      return;
    }

    selectedIndex = lsm.getMinSelectionIndex();
    App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());

    FileProcessor parsingTask = new FileProcessor(results.docs[selectedIndex], false);
    parsingTask.execute();

    App.get().subItemModel.fireTableDataChanged();
  }

  Thread thread;

  public void listParents(final Document doc) {

    String textQuery = null;
    String parentId = doc.get(IndexItem.PARENTID);
    if (parentId != null) {
      textQuery = IndexItem.ID + ":" + parentId;
    }

    String ftkId = doc.get(IndexItem.FTKID);
    if (ftkId != null) {
      textQuery = IndexItem.FTKID + ":" + parentId;
    }
    
    String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
    textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID;

    results = new SearchResult(0);

    if (textQuery != null) {
      try {
		IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
        results = task.pesquisar();

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (results.length > 0) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          App.get().tabbedHits.addTab("1 Item de Origem", App.get().getParams().parentItemScroll);
        }
      });
    }

    fireTableDataChanged();

  }

}
