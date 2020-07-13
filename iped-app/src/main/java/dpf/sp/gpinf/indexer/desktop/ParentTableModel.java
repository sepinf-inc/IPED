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

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import iped3.search.IMultiSearchResult;
import iped3.search.LuceneSearchResult;

public class ParentTableModel extends AbstractTableModel
        implements MouseListener, ListSelectionListener, SearchResultTableModel {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    // public ScoreDoc[] results = new ScoreDoc[0];
    LuceneSearchResult results = new LuceneSearchResult(0);
    int selectedIndex = -1;

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public int getRowCount() {
        return results.getLength();
    }

    @Override
    public String getColumnName(int col) {
        if (col == 2)
            return IndexItem.NAME;

        return ""; //$NON-NLS-1$
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
        App.get().appCase.getMultiMarcadores().setSelected((Boolean) value,
                App.get().appCase.getItemId(results.getLuceneIds()[row]));
        MarcadoresController.get().atualizarGUI();
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (col == 0) {
            return row + 1;

        } else if (col == 1) {
            return App.get().appCase.getMultiMarcadores()
                    .isSelected(App.get().appCase.getItemId(results.getLuceneIds()[row]));

        } else {
            try {
                Document doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[row]);
                return doc.get(IndexItem.NAME);
            } catch (Exception e) {
                // e.printStackTrace();
            }
            return ""; //$NON-NLS-1$
        }
    }

    @Override
    public IMultiSearchResult getSearchResult() {
        return (IMultiSearchResult) MultiSearchResult.get(App.get().appCase, results);
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
            int docId = results.getLuceneIds()[selectedIndex];
            ExternalFileOpen.open(docId);
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

        FileProcessor parsingTask = new FileProcessor(results.getLuceneIds()[selectedIndex], false);
        parsingTask.execute();

        App.get().subItemModel.fireTableDataChanged();
    }

    Thread thread;

    public void listParents(final Document doc) {

        String textQuery = null;
        String parentId = doc.get(IndexItem.PARENTID);
        if (parentId != null) {
            textQuery = IndexItem.ID + ":" + parentId; //$NON-NLS-1$
        }

        String ftkId = doc.get(IndexItem.FTKID);
        if (ftkId != null) {
            textQuery = IndexItem.FTKID + ":" + parentId; //$NON-NLS-1$
        }

        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID; //$NON-NLS-1$ //$NON-NLS-2$

        results = new LuceneSearchResult(0);

        if (textQuery != null) {
            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
                results = task.luceneSearch();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (results.getLength() > 0) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount")); //$NON-NLS-1$
                }
            });
        }

        fireTableDataChanged();

    }

}
