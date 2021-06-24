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
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import iped3.search.LuceneSearchResult;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class ReferencesTableModel extends AbstractTableModel
        implements MouseListener, ListSelectionListener, SearchResultTableModel {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private LuceneSearchResult results = new LuceneSearchResult(0);
    private int selectedIndex = -1;
    private String nameToScroll;

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

        if (nameToScroll != null) {
            App.get().getViewerController().getHtmlLinkViewer().setElementNameToScroll(nameToScroll);
        }

        FileProcessor parsingTask = new FileProcessor(results.getLuceneIds()[selectedIndex], false);
        parsingTask.execute();

    }

    public void listReferencingItems(Document doc) {

        String md5 = doc.get(HashTask.HASH.MD5.toString());
        String sha1 = doc.get(HashTask.HASH.SHA1.toString());
        String sha256 = doc.get(HashTask.HASH.SHA256.toString());
        String edonkey = doc.get(HashTask.HASH.EDONKEY.toString());
        String hashes = Arrays.asList(md5, sha1, sha256, edonkey).stream().filter(a -> a != null)
                .collect(Collectors.joining(" "));
        String textQuery = ExtraProperties.LINKED_ITEMS + ":(" + hashes + ") ";
        textQuery += ExtraProperties.SHARED_HASHES + ":(" + hashes + ")";

        try {
            IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
            results = task.luceneSearch();

            final int length = results.getLength();

            if (length > 0) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().referencesDock.setTitleText(length + Messages.getString("ReferencesTab.Title"));
                    }
                });
                nameToScroll = doc.get(BasicProps.HASH);
            } else {
                nameToScroll = null;
            }

        } catch (Exception e) {
            results = new LuceneSearchResult(0);
            nameToScroll = null;
            e.printStackTrace();
        }

        fireTableDataChanged();

    }

    @Override
    public MultiSearchResult getSearchResult() {
        return MultiSearchResult.get(App.get().appCase, results);
    }

}
