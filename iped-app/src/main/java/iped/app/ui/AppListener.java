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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.Future;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;

import org.apache.lucene.search.Query;

import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;

public class AppListener implements ActionListener, MouseListener {

    private String searchText = ""; //$NON-NLS-1$
    boolean clearAllFilters = false;
    private boolean updateSearchBox = false;

    public void updateFileListing() {
        updateFileListing(null);
    }

    public Future<MultiSearchResult> futureUpdateFileListing() {
        return updateFileListing(null);
    }

    public Future<MultiSearchResult> updateFileListing(Query query) {

        App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
        App.get().hitsTable.scrollRectToVisible(new Rectangle());
        Rectangle a = App.get().resultsTable.getVisibleRect();
        a.setBounds(a.x, 0, a.width, a.height);
        App.get().resultsTable.scrollRectToVisible(a);
        App.get().gallery.scrollRectToVisible(new Rectangle());
        App.get().setEnableGallerySimSearchButton(false);
        App.get().setEnableGalleryFaceSearchButton(false);
        App.get().ipedResult = new MultiSearchResult();
        App.get().setLastSelectedDoc(-1);
        if (App.get().resultSortKeys == null || (App.get().resultsTable.getRowSorter() != null && !App.get().resultsTable.getRowSorter().getSortKeys().isEmpty())) {
            App.get().resultSortKeys = App.get().resultsTable.getRowSorter().getSortKeys();
        }
        App.get().resultsTable.getRowSorter().setSortKeys(null);
        App.get().hitsDock.setTitleText(Messages.getString("AppListener.NoHits")); //$NON-NLS-1$
        App.get().subitemDock.setTitleText(Messages.getString("SubitemTableModel.Subitens")); //$NON-NLS-1$
        App.get().duplicateDock.setTitleText(Messages.getString("DuplicatesTableModel.Duplicates")); //$NON-NLS-1$
        App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount")); //$NON-NLS-1$
        App.get().referencesDock.setTitleText(Messages.getString("ReferencesTab.Title")); //$NON-NLS-1$
        App.get().referencedByDock.setTitleText(Messages.getString("ReferencedByTab.Title")); //$NON-NLS-1$
        App.get().status.setText(" "); //$NON-NLS-1$

        App.get().getViewerController().clear();

        App.get().subItemModel.results = new LuceneSearchResult(0);
        App.get().subItemModel.fireTableDataChanged();
        App.get().duplicatesModel.results = new LuceneSearchResult(0);
        App.get().duplicatesModel.fireTableDataChanged();
        App.get().parentItemModel.results = new LuceneSearchResult(0);
        App.get().parentItemModel.fireTableDataChanged();
        App.get().referencesModel.clear();
        App.get().referencedByModel.clear();

        App.get().getFilterManager().notifyFilterChange();

        try {
            UICaseSearcherFilter task;
            if (query == null)
                task = new UICaseSearcherFilter(searchText);
            else
                task = new UICaseSearcherFilter(query);

            task.applyUIQueryFilters();
            task.execute();

            return task;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {

        boolean updateFileList = false;

        if (evt.getSource() == App.get().filterComboBox && !App.get().filterManager.isUpdatingFilter()) {

            int filterIndex = App.get().filterComboBox.getSelectedIndex();
            if (filterIndex == 0 || filterIndex == -1) {
                App.get().filterComboBox.setBackground(App.get().filterManager.defaultColor);
            } else {
                App.get().filterComboBox.setBackground(App.get().alertColor);
            }

            updateFileList = true;
        }

        if (evt.getSource() == App.get().queryComboBox && !updateSearchBox && evt.getActionCommand().equals("comboBoxChanged") && !BookmarksController.get().isUpdatingHistory()) {
            if (App.get().queryComboBox.getSelectedItem() != null) {
                searchText = App.get().queryComboBox.getSelectedItem().toString();
                updateSearchBox = true;
                if (searchText.equals(BookmarksController.HISTORY_DIV) || searchText.equals(App.SEARCH_TOOL_TIP)) {
                    searchText = ""; //$NON-NLS-1$
                    App.get().queryComboBox.setSelectedItem(""); //$NON-NLS-1$
                } else {
                    searchText = searchText.trim();
                    BookmarksController.get().addToRecentSearches(searchText);
                    App.get().queryComboBox.setSelectedItem(searchText);
                }
            }

            if (!searchText.isEmpty())
                App.get().queryComboBox.setBorder(BorderFactory.createLineBorder(App.get().alertColor, 2, true));
            else
                App.get().queryComboBox.setBorder(null);

            updateFileList = true;
        }

        if (evt.getSource() == App.get().filterDuplicates) {
            if (!App.get().filterDuplicates.isSelected())
                App.get().filterDuplicates.setForeground(App.get().topPanel.getBackground());
            else
                App.get().filterDuplicates.setForeground(App.get().alertColor);

            updateFileList = true;
        }

        if (!clearAllFilters && updateFileList) {
            updateFileListing();
        }

        if (evt.getSource() == App.get().helpButton) {
            FileProcessor exibirAjuda = new FileProcessor(-1, false);
            exibirAjuda.execute();
        }

        if (evt.getSource() == App.get().optionsButton) {
            App.get().getContextMenu().show(App.get(), App.get().optionsButton.getX(), App.get().optionsButton.getHeight());
        }

        if (evt.getSource() == App.get().checkBox) {
            if (App.get().appCase.getMultiBookmarks().getTotalChecked() > 0) {
                int result = JOptionPane.showConfirmDialog(App.get(), Messages.getString("AppListener.UncheckAll"), //$NON-NLS-1$
                        Messages.getString("AppListener.UncheckAll.Title"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
                if (result == JOptionPane.YES_OPTION) {
                    App.get().appCase.getMultiBookmarks().clearChecked();
                    App.get().sendCheckAllToResultSetViewers(false);
                }
            } else {
                App.get().appCase.getMultiBookmarks().checkAll();
                App.get().sendCheckAllToResultSetViewers(true);
            }

            App.get().gallery.getDefaultEditor(GalleryCellRenderer.class).stopCellEditing();
            App.get().appCase.getMultiBookmarks().saveState();
            BookmarksController.get().updateUI();
        }

        if (evt.getSource() == App.get().updateCaseData) {
            UICaseDataLoader init = new UICaseDataLoader(App.get().getProcessingManager(), true);
            init.execute();
        }

        if (evt.getSource() == App.get().exportToZip) {
            App.get().getContextMenu().menuListener.exportFileTree(true, true);
        }

        updateSearchBox = false;

    }

    @Override
    public void mouseClicked(MouseEvent arg0) {

    }

    @Override
    public void mouseEntered(MouseEvent arg0) {

    }

    @Override
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mousePressed(MouseEvent evt) {

        Object termo = App.get().queryComboBox.getSelectedItem();
        if (termo != null && termo.equals(App.SEARCH_TOOL_TIP) && App.get().queryComboBox.isAncestorOf((Component) evt.getSource())) {
            updateSearchBox = true;
            App.get().queryComboBox.setSelectedItem(""); //$NON-NLS-1$
        }

    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

}
