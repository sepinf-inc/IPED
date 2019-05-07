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

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.Collator;
import java.util.List;

import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import dpf.sp.gpinf.indexer.search.ItemIdImpl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import iped3.ItemId;

public class ResultTableListener implements ListSelectionListener, MouseListener, KeyListener {

    public static boolean syncingSelectedItems = false;

    private long lastKeyTime = -1;
    private String lastKeyString = ""; //$NON-NLS-1$
    private Collator collator = Collator.getInstance();

    public ResultTableListener() {
        collator.setStrength(Collator.PRIMARY);
    }

    @Override
    public void valueChanged(ListSelectionEvent evt) {

        GerenciadorMarcadores.updateCounters();

        if (App.get().resultsTable.getSelectedRowCount() == 0 || evt.getValueIsAdjusting()) {
            return;
        }

        int resultTableLeadSelIdx = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();
        Rectangle a = App.get().resultsTable.getCellRect(resultTableLeadSelIdx, 0, false);
        Rectangle b = App.get().resultsTable.getVisibleRect();
        a.setBounds(b.x, a.y, a.width, a.height);
        App.get().resultsTable.scrollRectToVisible(a);

        if (!syncingSelectedItems) {
            syncingSelectedItems = true;
            App.get().gallery.getDefaultEditor(GalleryCellRenderer.class).stopCellEditing();
            int galleryRow = resultTableLeadSelIdx / App.get().galleryModel.colCount;
            int galleyCol = resultTableLeadSelIdx % App.get().galleryModel.colCount;
            App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(galleryRow, galleyCol, false));

            App.get().gallery.clearSelection();
            App.get().gallery.getSelectionModel().setValueIsAdjusting(true);
            int[] selRows = App.get().resultsTable.getSelectedRows();
            int start = 0;
            while (start < selRows.length) {
                int i = start + 1;
                while (i < selRows.length && selRows[i] - selRows[i - 1] == 1) {
                    i++;
                }
                App.get().gallery.setCellSelectionInterval(selRows[start], selRows[i - 1]);
                start = i;
            }
            App.get().gallery.getSelectionModel().setValueIsAdjusting(false);
            syncingSelectedItems = false;
        }

        processSelectedFile();

    }

    private synchronized void processSelectedFile() {

        // if(App.get().resultsTable.getSelectedRowCount() > 1)
        // return;
        int viewIndex = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();

        if (viewIndex != -1) {
            int modelIdx = App.get().resultsTable.convertRowIndexToModel(viewIndex);
            ItemId item = App.get().ipedResult.getItem(modelIdx);
            int docId = App.get().appCase.getLuceneId(item);
            if (docId != App.get().getParams().lastSelectedDoc) {

                App.get().hitsTable.scrollRectToVisible(new Rectangle());
                App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
                App.get().hitsDock.setTitleText(Messages.getString("AppListener.NoHits")); //$NON-NLS-1$
                App.get().subitemDock.setTitleText(Messages.getString("SubitemTableModel.Subitens")); //$NON-NLS-1$
                App.get().duplicateDock.setTitleText(Messages.getString("DuplicatesTableModel.Duplicates")); //$NON-NLS-1$
                App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount")); //$NON-NLS-1$

                FileProcessor parsingTask = new FileProcessor(docId, true);
                parsingTask.execute();
            }

        }

    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
            int modelIdx = App.get().resultsTable
                    .convertRowIndexToModel(App.get().resultsTable.getSelectionModel().getLeadSelectionIndex());
            ItemId item = App.get().ipedResult.getItem(modelIdx);
            int docId = App.get().appCase.getLuceneId(item);
            ExternalFileOpen.open(docId);

        } else if (evt.isPopupTrigger()) {
            App.get().menu.show((Component) evt.getSource(), evt.getX(), evt.getY());

        } else {
            processSelectedFile();

        }

    }

    @Override
    public void mouseClicked(MouseEvent e) {
        ViewerControl.getInstance().releaseLibreOfficeFocus();
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            App.get().menu.show((Component) evt.getSource(), evt.getX(), evt.getY());

        }
    }

    @Override
    public void keyPressed(KeyEvent evt) {
    }

    int keyBefore = -1;

    @Override
    public void keyReleased(KeyEvent evt) {
        if (App.get().resultsTable.getSelectedRow() == -1) {
            return;
        }

        if ((keyBefore == KeyEvent.VK_CONTROL && evt.getKeyCode() == KeyEvent.VK_C)
                || (keyBefore == KeyEvent.VK_C && evt.getKeyCode() == KeyEvent.VK_CONTROL)) {

            int selCol = App.get().resultsTable.getSelectedColumn();
            if (selCol < 0) {
                return;
            }
            String value = getCell(App.get().resultsTable, App.get().resultsTable.getSelectedRow(), selCol);
            value = value.replace("<html><nobr>", "").replace(App.get().getParams().HIGHLIGHT_START_TAG, "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .replace(App.get().getParams().HIGHLIGHT_END_TAG, ""); //$NON-NLS-1$
            StringSelection selection = new StringSelection(value);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);

        } else if (evt.getKeyCode() == KeyEvent.VK_SPACE) {
            int col = App.get().resultsTable.convertColumnIndexToView(1);
            int firstRow = App.get().resultsTable.getSelectedRow();
            boolean value = true;
            if (firstRow != -1 && (Boolean) App.get().resultsTable.getValueAt(firstRow, col)) {
                value = false;
            }

            MarcadoresController.get().setMultiSetting(true);
            App.get().resultsTable.setUpdateSelectionOnSort(false);
            int[] selectedRows = App.get().resultsTable.getSelectedRows();
            for (int i = 0; i < selectedRows.length; i++) {
                if (i == selectedRows.length - 1) {
                    MarcadoresController.get().setMultiSetting(false);
                    App.get().resultsTable.setUpdateSelectionOnSort(true);
                }
                App.get().resultsTable.setValueAt(value, selectedRows[i], col);
            }

        }

        keyBefore = evt.getKeyCode();

    }

    @Override
    public void keyTyped(KeyEvent evt) {
        char c = evt.getKeyChar();
        if (c == ' ' || (evt.getModifiers() & (InputEvent.CTRL_MASK | InputEvent.ALT_MASK)) != 0) {
            return;
        }

        JTable table = App.get().resultsTable;
        List<? extends SortKey> sortKeys = table.getRowSorter().getSortKeys();
        if (sortKeys.isEmpty()) {
            return;
        }
        int sortCol = sortKeys.get(0).getColumn();
        int viewCol = table.convertColumnIndexToView(sortCol);

        // Provisoriamente as colunas estão fixas
        // (colunas onde a comparação de String não funcionaria, porque ordenação é
        // numérica ou de Data)
        if (!((RowComparator) ((TableRowSorter) table.getRowSorter()).getComparator(sortCol)).isStringComparator()) {
            return;
        }

        long t = System.currentTimeMillis();
        if (t - lastKeyTime > 500) {
            lastKeyString = ""; //$NON-NLS-1$
        }
        lastKeyTime = t;
        if (lastKeyString.length() != 1 || lastKeyString.charAt(0) != c) {
            lastKeyString += c;
        }
        int initialRow = table.getSelectedRow();
        if (initialRow < 0) {
            initialRow = table.getRowCount() - 1;
        }

        int currRow = initialRow;
        int foundRow = findRow(table, currRow + 1, table.getRowCount() - 1, viewCol, lastKeyString);
        if (foundRow < 0) {
            foundRow = findRow(table, 0, currRow - 1, viewCol, lastKeyString);
        }
        if (foundRow >= 0) {
            table.setRowSelectionInterval(foundRow, foundRow);
            table.scrollRectToVisible(table.getCellRect(foundRow, viewCol, true));
        }
    }

    private int findRow(JTable table, int from, int to, int col, String search) {
        while (from < to) {
            int mid = (from + to) >> 1;
            int cmp = compare(getCell(table, mid, col), search);
            if (cmp > 0) {
                to = mid - 1;
            } else if (cmp < 0) {
                from = mid + 1;
            } else {
                to = mid;
            }
        }
        if (from == to) {
            if (compare(getCell(table, from, col), search) == 0) {
                return from;
            }
        }
        return -1;
    }

    private int compare(String a, String b) {
        if (a.length() > b.length()) {
            a = a.substring(0, b.length());
        }
        return collator.compare(a, b);
    }

    private String getCell(JTable table, int row, int col) {
        String cell = table.getValueAt(row, col).toString();
        return cell.replace("<html><nobr>", "").replace(App.get().getParams().HIGHLIGHT_START_TAG, "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .replace(App.get().getParams().HIGHLIGHT_END_TAG, ""); //$NON-NLS-1$
    }

}
