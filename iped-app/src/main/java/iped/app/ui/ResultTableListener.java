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
package iped.app.ui;

import java.awt.Component;
import java.awt.Dialog;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.popups.FieldValuePopupMenu;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.viewers.ATextViewer;
import iped.viewers.components.HitsTableModel;
import iped.viewers.util.ProgressDialog;

public class ResultTableListener implements ListSelectionListener, MouseListener, KeyListener {

    public static boolean syncingSelectedItems = false;
    private static volatile int lastTableDoc = -1;
    private static Logger logger = LoggerFactory.getLogger(ResultTableListener.class);

    private long lastKeyTime = -1;
    private String lastKeyString = ""; //$NON-NLS-1$
    private Collator collator = Collator.getInstance();

    private Executor executor = Executors.newSingleThreadExecutor();

    public ResultTableListener() {
        collator.setStrength(Collator.PRIMARY);
    }

    @Override
    public void valueChanged(ListSelectionEvent evt) {

        if (evt.getValueIsAdjusting()) {
            return;
        }

        BookmarksManager.updateCounters();

        if (App.get().resultsTable.getSelectedRowCount() == 0) {
            App.get().setEnableGalleryFaceSearchButton(false);
            App.get().setEnableGallerySimSearchButton(false);
            App.get().setLastSelectedDoc(-1);
            lastTableDoc = -1;
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
            int galleryRow = resultTableLeadSelIdx / App.get().getGalleryColCount();
            int galleyCol = resultTableLeadSelIdx % App.get().getGalleryColCount();
            App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(galleryRow, galleyCol, false));

            App.get().gallery.getSelectionModel().setValueIsAdjusting(true);
            App.get().gallery.clearSelection();
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

        processSelectedFile(false);

    }

    private synchronized void processSelectedFile(boolean isMouseEvent) {

        int viewIndex = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();

        if (viewIndex != -1) {
            int modelIdx = App.get().resultsTable.convertRowIndexToModel(viewIndex);
            IItemId item = App.get().ipedResult.getItem(modelIdx);
            int docId = App.get().appCase.getLuceneId(item);
            int lastAppDoc = App.get().getLastSelectedDoc();

            if (docId != lastAppDoc && (!isMouseEvent || docId == lastTableDoc)) {
                App.get().hitsTable.scrollRectToVisible(new Rectangle());
                App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
                App.get().hitsDock.setTitleText(Messages.getString("AppListener.NoHits")); //$NON-NLS-1$
                App.get().subitemDock.setTitleText(Messages.getString("SubitemTableModel.Subitens")); //$NON-NLS-1$
                App.get().duplicateDock.setTitleText(Messages.getString("DuplicatesTableModel.Duplicates")); //$NON-NLS-1$
                App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount")); //$NON-NLS-1$
                App.get().referencesDock.setTitleText(Messages.getString("ReferencesTab.Title")); //$NON-NLS-1$
                App.get().referencedByDock.setTitleText(Messages.getString("ReferencedByTab.Title")); //$NON-NLS-1$

                lastTableDoc = docId;
                FileProcessor parsingTask = new FileProcessor(docId, true);
                parsingTask.execute();
            }

        }

    }

    @Override
    public void mouseReleased(MouseEvent evt) {

        IItemId itemId = getSelectedItemId();
        if (evt.getClickCount() == 2) {
            int docId = App.get().appCase.getLuceneId(itemId);
            ExternalFileOpen.open(docId);

        } else if (evt.isPopupTrigger()) {
            showContextMenu(itemId, evt);

        } else {
            // should be triggered when user clicks on an already selected file on table but
            // the viewing file is another one clicked on another tab (e.g. subitem tab)
            processSelectedFile(true);

        }

    }

    private IItemId getSelectedItemId(int row) {
        int viewIndex = App.get().resultsTable.getSelectedRow();
        if (viewIndex != -1) {
            int modelIdx = App.get().resultsTable.convertRowIndexToModel(viewIndex);
            return App.get().ipedResult.getItem(modelIdx);
        }
        return null;
    }

    public IItemId getSelectedItemId() {
        return getSelectedItemId(App.get().resultsTable.getSelectedRow());
    }

    private void showContextMenu(IItemId itemId, MouseEvent evt) {
        IItem item = itemId == null ? null : App.get().appCase.getItemByItemId(itemId);

        if (evt.getButton() == MouseEvent.BUTTON3 && (evt.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK) {
            JTable resultsTable = App.get().resultsTable;
            int colIndex = resultsTable.columnAtPoint(evt.getPoint());
            int rowIndex = resultsTable.rowAtPoint(evt.getPoint());

            IItemId id = getSelectedItemId(rowIndex);

            String value = (String) resultsTable.getValueAt(rowIndex, colIndex);
            colIndex = resultsTable.convertColumnIndexToModel(colIndex);
            String field = ((ResultTableModel) resultsTable.getModel()).getColumnFieldName(colIndex);

            (new FieldValuePopupMenu(id, field, value)).show((Component) evt.getSource(), evt.getX(), evt.getY());
            return;
        }

        new MenuClass(item).show((Component) evt.getSource(), evt.getX(), evt.getY());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        // needed for Linux
        if (evt.isPopupTrigger()) {
            IItemId itemId = getSelectedItemId();
            showContextMenu(itemId, evt);
        }
    }

    @Override
    public void keyReleased(KeyEvent evt) {
    }

    @Override
    public void keyPressed(KeyEvent evt) {
        if (evt.isConsumed())
            return;
        if (App.get().resultsTable.getSelectedRow() == -1)
            return;

        if (evt.getKeyCode() == KeyEvent.VK_C && ((evt.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            int selCol = App.get().resultsTable.getSelectedColumn();
            if (selCol < 0)
                return;
            String value = getCell(App.get().resultsTable, App.get().resultsTable.getSelectedRow(), selCol);
            StringSelection selection = new StringSelection(value);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_SPACE) {
            itemSelectionToggle();
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_R && ((evt.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            itemSelectionAndSubItems(true);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_R && ((evt.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
            itemSelectionAndSubItems(false);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_P && ((evt.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            itemSelectionAndParent(true);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_P && ((evt.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
            itemSelectionAndParent(false);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_D && ((evt.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            itemSelectionAndReferencedBy(true);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_D && ((evt.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
            itemSelectionAndReferencedBy(false);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_F && ((evt.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            itemSelectionAndReferences(true);
            evt.consume();
        } else if (evt.getKeyCode() == KeyEvent.VK_F && ((evt.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
            itemSelectionAndReferences(false);
            evt.consume();
        } else
            BookmarksManager.get().keyPressed(evt);

    }

    private ProgressDialog createProgressDialog() {
        ProgressDialog d = new ProgressDialog(App.get(), null, false, 200, Dialog.ModalityType.APPLICATION_MODAL);
        d.setNote(Messages.getString("App.Wait")); //$NON-NLS-1$
        return d;
    }

    public void itemSelectionToggle() {

        int col = App.get().resultsTable.convertColumnIndexToView(1);
        int firstRow = App.get().resultsTable.getSelectedRow();
        boolean value = true;
        if (firstRow != -1 && (Boolean) App.get().resultsTable.getValueAt(firstRow, col)) {
            value = false;
        }

        itemSelection(value);
    }

    public void itemSelection(boolean value) {

        int col = App.get().resultsTable.convertColumnIndexToView(1);

        BookmarksController.get().setMultiSetting(true);
        App.get().resultsTable.setUpdateSelectionOnSort(false);
        int[] selectedRows = App.get().resultsTable.getSelectedRows();
        for (int i = 0; i < selectedRows.length; i++) {
            if (i == selectedRows.length - 1) {
                BookmarksController.get().setMultiSetting(false);
                App.get().resultsTable.setUpdateSelectionOnSort(true);
            }
            App.get().resultsTable.setValueAt(value, selectedRows[i], col);
        }
        BookmarksController.get().updateUI();
        App.get().subItemTable.repaint();
    }

    public void itemSelectionAndResultsByQuery(boolean value, BaseTableModel tableModel) {
        ProgressDialog dialog = createProgressDialog();
        executor.execute(() -> {
            try {
                int col = App.get().resultsTable.convertColumnIndexToView(1);
                BookmarksController.get().setMultiSetting(true);
                App.get().resultsTable.setUpdateSelectionOnSort(false);
                int[] selectedRows = App.get().resultsTable.getSelectedRows();
                dialog.setMaximum(selectedRows.length);
                for (int i = 0; i < selectedRows.length; i++) {

                    // item selection
                    if (i == selectedRows.length - 1) {
                        BookmarksController.get().setMultiSetting(false);
                        App.get().resultsTable.setUpdateSelectionOnSort(true);
                    }
                    App.get().resultsTable.setValueAt(value, selectedRows[i], col);

                    // query results selection
                    int selectedIndex = App.get().resultsTable.convertRowIndexToModel(selectedRows[i]);
                    IItemId selectedItemId = App.get().ipedResult.getItem(selectedIndex);
                    final int selectedDocId = App.get().appCase.getLuceneId(selectedItemId);
                    Document selectedDoc = App.get().appCase.getSearcher().doc(selectedDocId);
                    Query query = tableModel.createQuery(selectedDoc);
                    if (query != null) {
                        IPEDSearcher task = new IPEDSearcher(App.get().appCase, query);
                        task.setRewritequery(false);
                        MultiSearchResult result = task.multiSearch();
                        if (result.getLength() > 0) {
                            logger.debug("Found {} results of sourceId {} id {}", result.getLength(), selectedItemId.getSourceId(), selectedItemId.getId());
                            for (IItemId subItem : result.getIterator()) {
                                App.get().appCase.getMultiBookmarks().setChecked((Boolean) value, subItem);
                            }
                        }
                    }

                    dialog.setProgress(i);
                    if (dialog.isCanceled()) {
                        return;
                    }
                }
            } catch (Exception e) {
                logger.error("Error selecting item and its query results", e);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    dialog.close();
                    BookmarksController.get().updateUI();
                    App.get().subItemTable.repaint();
                });
            }
        });
    }

    public void itemSelectionAndSubItems(boolean value) {
        itemSelectionAndResultsByQuery(value, App.get().subItemModel);
    }

    public void itemSelectionAndParent(boolean value) {
        itemSelectionAndResultsByQuery(value, App.get().parentItemModel);
    }

    public void itemSelectionAndReferences(boolean value) {
        itemSelectionAndResultsByQuery(value, App.get().referencesModel);
    }

    public void itemSelectionAndReferencedBy(boolean value) {
        itemSelectionAndResultsByQuery(value, App.get().referencedByModel);
    }

    /**
     * Add a simple "type-to-find" feature to the table. It works on the currently
     * sorted column, if it uses a String comparator, so it will not work on numeric
     * and date columns.
     */
    @Override
    public void keyTyped(KeyEvent evt) {
        char c = evt.getKeyChar();
        if (c == ' ' || (evt.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0 || c == KeyEvent.VK_TAB || c == KeyEvent.VK_ESCAPE || c == KeyEvent.VK_BACK_SPACE
                || c == KeyEvent.VK_DELETE)
            return;

        JTable table = App.get().resultsTable;
        List<? extends SortKey> sortKeys = table.getRowSorter().getSortKeys();
        if (sortKeys.isEmpty())
            return;

        int sortCol = sortKeys.get(0).getColumn();
        int viewCol = table.convertColumnIndexToView(sortCol);

        // Only works on String columns
        if (!((RowComparator) ((ResultTableRowSorter) table.getRowSorter()).getComparator(sortCol)).isStringComparator())
            return;

        if (BookmarksManager.get().hasSingleKeyShortcut())
            return;

        long t = System.currentTimeMillis();
        if (t - lastKeyTime > 500)
            lastKeyString = ""; //$NON-NLS-1$

        lastKeyTime = t;
        if (lastKeyString.length() != 1 || lastKeyString.charAt(0) != c)
            lastKeyString += c;

        int initialRow = table.getSelectedRow();
        if (initialRow < 0)
            initialRow = table.getRowCount() - 1;

        int currRow = initialRow;
        int foundRow = findRow(table, currRow + 1, table.getRowCount() - 1, viewCol, lastKeyString);
        if (foundRow < 0)
            foundRow = findRow(table, 0, currRow - 1, viewCol, lastKeyString);
        if (foundRow >= 0) {
            table.setRowSelectionInterval(foundRow, foundRow);
            table.scrollRectToVisible(table.getCellRect(foundRow, viewCol, true));
        }
        evt.consume();
    }

    private int findRow(JTable table, int from, int to, int col, String search) {
        while (from < to) {
            int mid = (from + to) >> 1;
            int cmp = compare(getCell(table, mid, col), search);
            if (cmp > 0)
                to = mid - 1;
            else if (cmp < 0)
                from = mid + 1;
            else
                to = mid;
        }
        if (from == to && compare(getCell(table, from, col), search) == 0)
            return from;
        return -1;
    }

    private int compare(String a, String b) {
        if (a.isEmpty() && !b.isEmpty())
            return -1;
        if (a.length() > b.length())
            a = a.substring(0, b.length());
        return collator.compare(a, b);
    }

    private String getCell(JTable table, int row, int col) {
        String cell = table.getValueAt(row, col).toString();
        if (App.get().getFontStartTag() != null)
            cell = cell.replace(App.get().getFontStartTag(), "");
        return cell.replace(HitsTableModel.htmlStartTag, "").replace(HitsTableModel.htmlEndTag, "").replace(ATextViewer.HIGHLIGHT_START_TAG, "").replace(ATextViewer.HIGHLIGHT_END_TAG, "");
    }

}