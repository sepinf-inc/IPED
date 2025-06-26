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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.TreeViewModel.Node;
import iped.app.ui.columns.ColumnsManager;
import iped.app.ui.columns.ColumnsManagerUI;
import iped.app.ui.columns.ColumnsSelectUI;
import iped.app.ui.utils.UiIconSize;
import iped.app.ui.utils.UiScale;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.properties.ExtraProperties;
import iped.utils.SpinnerDialog;
import iped.viewers.api.AbstractViewer;

public class MenuListener implements ActionListener {

    private static Logger LOGGER = LoggerFactory.getLogger(MenuListener.class);

    static String CSV = ".csv"; //$NON-NLS-1$
    static JFileChooser fileChooser, fileChooserImportKeywords;
    static JCheckBox checkBookMarkList;
    static JCheckBox checkBookMarkWords;

    FileFilter defaultFilter, csvFilter = new Filtro();
    MenuClass menu;

    public MenuListener(MenuClass menu) {
        this.menu = menu;
    }

    private void setupFileChooser() {
        if (fileChooser != null)
            return;
        fileChooser = new JFileChooser();
        defaultFilter = fileChooser.getFileFilter();
        File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
        fileChooser.setCurrentDirectory(moduleDir.getParentFile());

        File dirDadosExportados = new File(Messages.getString("ExportToZIP.DefaultPath"));
        if (dirDadosExportados.exists()) {
            fileChooser.setCurrentDirectory(dirDadosExportados);
        }
    }

    private void setupColumnsSelector() {
        ColumnsSelectUI columnsSelector = ColumnsSelectUI.getInstance();
        columnsSelector.setVisible();
    }

    private void setupFileChooserImportKeywords() {
        if (fileChooserImportKeywords != null)
            return;

        checkBookMarkList = new JCheckBox(Messages.getString("MenuListener.CheckAddKeywordsAsSingleBookmark"));
        checkBookMarkWords = new JCheckBox(Messages.getString("MenuListener.CheckAddKeywordsAsMultipleBookmarks"));

        fileChooserImportKeywords = new JFileChooser();
        defaultFilter = fileChooserImportKeywords.getFileFilter();
        File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
        fileChooserImportKeywords.setCurrentDirectory(moduleDir.getParentFile());

        File dirDadosExportados = new File(Messages.getString("ExportToZIP.DefaultPath"));
        if (dirDadosExportados.exists()) {
            fileChooserImportKeywords.setCurrentDirectory(dirDadosExportados);
        }

        fileChooserImportKeywords.setFileFilter(defaultFilter);
        fileChooserImportKeywords.setFileSelectionMode(JFileChooser.FILES_ONLY);

        JComponent panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        checkBookMarkList.setSelected(false);
        panel.add(checkBookMarkList, BorderLayout.NORTH);
        checkBookMarkWords.setSelected(false);
        checkBookMarkWords.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        panel.add(checkBookMarkWords, BorderLayout.SOUTH);

        int width = (int) fileChooserImportKeywords.getPreferredSize().getWidth();
        int height = (int) fileChooserImportKeywords.getPreferredSize().getHeight();
        height += (int) panel.getPreferredSize().getHeight();

        fileChooserImportKeywords.setPreferredSize(new Dimension(width, height));

        fileChooserImportKeywords.setAccessory(panel);
        JComponent center = null;
        BorderLayout layout = (BorderLayout) fileChooserImportKeywords.getLayout();
        for (Component child : fileChooserImportKeywords.getComponents()) {
            if (BorderLayout.CENTER == layout.getConstraints(child)) {
                center = (JComponent) child;
            }
        }
        if (center != null)
            center.add(panel, BorderLayout.SOUTH);
    }

    private class Filtro extends FileFilter {

        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            if (f.getName().endsWith(CSV)) {
                return true;
            }

            return false;
        }

        @Override
        public String getDescription() {
            return "Comma Separated Values (" + CSV + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        LOGGER.debug("MenuListener Aktion Event Performed " + e.toString() + " " + e.getSource());
        if (e.getSource() == menu.toggleTimelineView) {
            App.get().timelineListener.toggleTimelineTableView();

        } else if (e.getSource() == menu.changeLayout) {
            App.get().toggleHorizontalVerticalLayout();

        } else if (e.getSource() == menu.defaultLayout) {
            App.get().adjustLayout(true);

        } else if (e.getSource() == menu.savePanelsLayout) {
            App.get().savePanelLayout();

        } else if (e.getSource() == menu.loadPanelsLayout) {
            App.get().loadPanelLayout();

        } else if (e.getSource() == menu.checkHighlighted) {
            App.get().resultTableListener.itemSelection(true);

        } else if (e.getSource() == menu.uncheckHighlighted) {
            App.get().resultTableListener.itemSelection(false);

        } else if (e.getSource() == menu.checkHighlightedAndSubItems) {
            App.get().resultTableListener.itemSelectionAndSubItems(true);

        } else if (e.getSource() == menu.uncheckHighlightedAndSubItems) {
            App.get().resultTableListener.itemSelectionAndSubItems(false);

        } else if (e.getSource() == menu.checkHighlightedAndParent) {
            App.get().resultTableListener.itemSelectionAndParent(true);

        } else if (e.getSource() == menu.uncheckHighlightedAndParent) {
            App.get().resultTableListener.itemSelectionAndParent(false);

        } else if (e.getSource() == menu.checkHighlightedAndReferences) {
            App.get().resultTableListener.itemSelectionAndReferences(true);

        } else if (e.getSource() == menu.uncheckHighlightedAndReferences) {
            App.get().resultTableListener.itemSelectionAndReferences(false);

        } else if (e.getSource() == menu.checkHighlightedAndReferencedBy) {
            App.get().resultTableListener.itemSelectionAndReferencedBy(true);

        } else if (e.getSource() == menu.uncheckHighlightedAndReferencedBy) {
            App.get().resultTableListener.itemSelectionAndReferencedBy(false);
        }

        if (e.getSource() == menu.readHighlighted) {
            BookmarksController.get().setMultiSetting(true);
            int col = App.get().resultsTable.convertColumnIndexToView(2);
            for (Integer row : App.get().resultsTable.getSelectedRows()) {
                App.get().resultsTable.setValueAt(true, row, col);
            }
            BookmarksController.get().setMultiSetting(false);
            App.get().appCase.getMultiBookmarks().saveState();
            BookmarksController.get().updateUISelection();

        } else if (e.getSource() == menu.unreadHighlighted) {
            BookmarksController.get().setMultiSetting(true);
            int col = App.get().resultsTable.convertColumnIndexToView(2);
            for (Integer row : App.get().resultsTable.getSelectedRows()) {
                App.get().resultsTable.setValueAt(false, row, col);
            }
            BookmarksController.get().setMultiSetting(false);
            App.get().appCase.getMultiBookmarks().saveState();
            BookmarksController.get().updateUISelection();

        } else if (e.getSource() == menu.exportHighlighted) {
            setupFileChooser();
            fileChooser.setFileFilter(defaultFilter);
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File dir = fileChooser.getSelectedFile();
                ArrayList<IItemId> selectedIds = new ArrayList<IItemId>();
                for (int row : App.get().resultsTable.getSelectedRows()) {
                    IItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
                    selectedIds.add(item);
                }
                (new CopyFiles(dir, selectedIds)).execute();
            }

        } else if (e.getSource() == menu.copyHighlighted) {
            ArrayList<Integer> selectedIds = new ArrayList<Integer>();
            for (int row : App.get().resultsTable.getSelectedRows()) {
                IItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
                int luceneId = App.get().appCase.getLuceneId(item);
                selectedIds.add(luceneId);
            }
            setupColumnsSelector();
            if (ColumnsSelectUI.getOkButtonClicked()) {
                setupFileChooser();
                fileChooser.setFileFilter(csvFilter);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    if (!file.getName().endsWith(CSV)) {
                        file = new File(file.getAbsolutePath() + CSV);
                    }
                    ArrayList<String> loadedSelectedFields = ColumnsSelectUI.loadSavedFields();
                    (new CopyProperties(file, selectedIds, loadedSelectedFields)).execute();
                }
            }

        } else if (e.getSource() == menu.copyChecked) {
            ArrayList<Integer> uniqueSelectedIds = new ArrayList<Integer>();
            App.get().appCase.getLuceneIdStream().forEach(docId -> {
                IItemId item = App.get().appCase.getItemId(docId);
                if (App.get().appCase.getMultiBookmarks().isChecked(item)) {
                    uniqueSelectedIds.add(docId);
                }
            });
            setupColumnsSelector();
            if (ColumnsSelectUI.getOkButtonClicked()) {
                setupFileChooser();
                fileChooser.setFileFilter(csvFilter);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    if (!file.getName().endsWith(CSV)) {
                        file = new File(file.getAbsolutePath() + CSV);
                    }
                    ArrayList<String> loadedSelectedFields = ColumnsSelectUI.loadSavedFields();
                    (new CopyProperties(file, uniqueSelectedIds, loadedSelectedFields)).execute();
                }
            }
        } else if (e.getSource() == menu.exportChecked) {
            ArrayList<IItemId> uniqueSelectedIds = new ArrayList<IItemId>();
            for (IPEDSource source : App.get().appCase.getAtomicSources()) {
                for (int id = 0; id <= source.getLastId(); id++) {
                    if (source.getBookmarks().isChecked(id)) {
                        uniqueSelectedIds.add(new ItemId(source.getSourceId(), id));
                    }
                }
            }
            setupFileChooser();
            fileChooser.setFileFilter(defaultFilter);
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File dir = fileChooser.getSelectedFile();
                (new CopyFiles(dir, uniqueSelectedIds)).execute();
            }

        } else if (e.getSource() == menu.exportCheckedToZip) {
            ArrayList<ItemId> uniqueSelectedIds = new ArrayList<ItemId>();
            for (IPEDSource source : App.get().appCase.getAtomicSources()) {
                for (int id = 0; id <= source.getLastId(); id++) {
                    if (source.getBookmarks().isChecked(id)) {
                        uniqueSelectedIds.add(new ItemId(source.getSourceId(), id));
                    }
                }
            }
            setupFileChooser();
            fileChooser.setFileFilter(defaultFilter);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setSelectedFile(new File(Messages.getString("ExportToZIP.DefaultName")));
            if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                (new ExportFilesToZip(file, uniqueSelectedIds)).execute();
            }

        } else if (e.getSource() == menu.importKeywords) {
            setupFileChooserImportKeywords();
            fileChooserImportKeywords.setFileFilter(defaultFilter);
            fileChooserImportKeywords.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fileChooserImportKeywords.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooserImportKeywords.getSelectedFile();
                new KeywordListImporter(file, checkBookMarkList.isSelected(), checkBookMarkWords.isSelected()).execute();
            }

        } else if (e.getSource() == menu.exportTree || e.getSource() == menu.exportTreeChecked || e.getSource() == menu.exportCheckedTreeToZip) {

            boolean onlyChecked = e.getSource() != menu.exportTree;
            boolean toZip = e.getSource() == menu.exportCheckedTreeToZip;
            exportFileTree(onlyChecked, toZip);

        } else if (e.getSource() == menu.clearSearchHistory) {
            App.get().appCase.getMultiBookmarks().clearTypedWords();
            App.get().appCase.getMultiBookmarks().saveState();
            BookmarksController.get().updateUIHistory();

        } else if (e.getSource() == menu.loadBookmarks) {
            BookmarksController.get().askAndLoadState();

        } else if (e.getSource() == menu.saveBookmarks) {
            BookmarksController.get().askAndSaveState();

        } else if (e.getSource() == menu.previewScreenshot) {
            AbstractViewer viewer = App.get().getViewerController().getMultiViewer().getCurrentViewer();
            viewer.copyScreen();

        } else if (e.getSource() == menu.changeGalleryColCount) {

            SpinnerDialog dialog = new SpinnerDialog(App.get(), Messages.getString("MenuListener.Gallery"), Messages.getString("MenuListener.Cols"), App.get().galleryModel.getColumnCount(), 1, 40);
            dialog.addChangeListener(new SpinnerListener());
            dialog.setVisible(true);

        } else if (e.getSource() == menu.manageBookmarks) {

            BookmarksManager.setVisible();

        } else if (e.getSource() == menu.manageColumns) {

            ColumnsManagerUI.getInstance().setVisible();

        } else if (e.getSource() == menu.pinFirstColumns) {

            int pinned = ColumnsManagerUI.getInstance().getPinnedColumns();
            String msg = Messages.getString("MenuListener.PinFirstCols");
            SpinnerDialog dialog = new SpinnerDialog(App.get(), msg, msg + ":", pinned, 2, 12);
            dialog.setVisible(true);
            ColumnsManagerUI.getInstance().setPinnedColumns(dialog.getSelectedValue());

        } else if (e.getSource() == menu.manageFilters) {

            App.get().filterManager.setVisible(true);

        } else if (e.getSource() == menu.navigateToParent) {

            int selIdx = App.get().resultsTable.getSelectedRow();
            if (selIdx != -1) {
                IItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx));
                int docId = App.get().appCase.getLuceneId(item);
                App.get().treeListener.navigateToParent(docId);
            }

        } else if (e.getSource() == menu.exportTerms) {
            new ExportIndexedTerms(App.get().appCase.getLeafReader()).export();

        } else if (e.getSource() == menu.similarImagesCurrent) {
            SimilarImagesFilterActions.searchSimilarImages(false);

        } else if (e.getSource() == menu.similarImagesExternal) {
            SimilarImagesFilterActions.searchSimilarImages(true);

        } else if (e.getSource() == menu.similarFacesCurrent) {
            SimilarFacesFilterActions.searchSimilarFaces(false);

        } else if (e.getSource() == menu.similarFacesExternal) {
            SimilarFacesFilterActions.searchSimilarFaces(true);

        } else if (e.getSource() == menu.similarDocs) {
            int selIdx = App.get().resultsTable.getSelectedRow();
            if (selIdx != -1) {
                int percent = Integer.parseInt(JOptionPane.showInputDialog(Messages.getString("MenuListener.SimilarityLabel"), 70)); //$NON-NLS-1$

                IItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx));
                App.get().similarDocumentFilterer.setPercent(percent);

                App.get().similarDocumentFilterer.setItem(item, App.get().appCase.getItemByItemId(item));

                App.get().appletListener.updateFileListing();
            }

        } else if (e.getSource() == menu.openViewfile) {
            int selIdx = App.get().resultsTable.getSelectedRow();
            IItemId itemId = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx));
            IItem item = App.get().appCase.getItemByItemId(itemId);
            LOGGER.info("Externally Opening preview of " + item.getPath()); //$NON-NLS-1$
            ExternalFileOpen.open(item.getViewFile());

        } else if (e.getSource() == menu.createReport) {
            new ReportDialog().setVisible();

        } else if (e.getSource() == menu.lastColLayout) {
            ColumnsManagerUI.getInstance().resetToLastLayout();

        } else if (e.getSource() == menu.saveColLayout) {
            ColumnsManager.getInstance().saveColumnsState();

        } else if (e.getSource() == menu.resetColLayout) {
            ColumnsManagerUI.getInstance().resetToDefaultLayout();
        } else if (e.getSource() == menu.addToGraph) {
            int[] rows = App.get().resultsTable.getSelectedRows();
            List<ItemId> items = new ArrayList<>(rows.length);
            for (int selIdx : rows) {
                ItemId itemId = (ItemId) App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx));
                items.add(itemId);
            }
            App.get().appGraphAnalytics.addEvidenceFilesToGraph(items);
        } else if (e.getSource() == menu.navigateToParentChat) {
            int selIdx = App.get().resultsTable.getSelectedRow();
            IItemId itemId = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx));
            IIPEDSource atomicSource = App.get().appCase.getAtomicSourceBySourceId(itemId.getSourceId());
            IItem item = App.get().appCase.getItemByItemId(itemId);
            int chatId = atomicSource.getParentId(itemId.getId());
            if (chatId != -1) {
                String position = item.getMetadata().get(ExtraProperties.PARENT_VIEW_POSITION);
                // TODO change viewer api to pass this
                App.get().getViewerController().getHtmlLinkViewer().setElementIDToScroll(position);
                ItemId chatItemId = new ItemId(itemId.getSourceId(), chatId);
                int luceneId = App.get().appCase.getLuceneId(chatItemId);
                new FileProcessor(luceneId, false).execute();
            } else {
                JOptionPane.showMessageDialog(App.get(), Messages.getString("MenuListener.ChatNotFound")); //$NON-NLS-1$
            }
        } else if (e.getSource() == menu.uiZoom) {
            String value = JOptionPane.showInputDialog(App.get(), Messages.getString("MenuListener.UiScaleDialog").replace("{}", UiScale.AUTO), UiScale.loadUserSetting());
            double factor = 0;
            try {
                factor = Double.parseDouble(value);
            } catch (NumberFormatException ignore) {
            }
            if (UiScale.AUTO.equals(value) || factor > 0) {
                UiScale.saveUserSetting(value);
            }
        } else if (e.getSource() == menu.catIconSize) {
            JPanel panel = new JPanel(new GridLayout(2, 3, 5, 5));
            panel.add(new JLabel(Messages.getString("CategoryTreeModel.RootName")));
            panel.add(new JLabel(Messages.getString("App.Gallery")));
            panel.add(new JLabel(Messages.getString("MenuListener.TableAndOthers")));
            int[] sizes = UiIconSize.loadUserSetting();
            JSpinner[] spinners = new JSpinner[3];
            for (int i = 0; i < 3; i++) {
                int idx = i;
                SpinnerNumberModel sModel = new SpinnerNumberModel(sizes[idx], 16, 32, 2);
                JSpinner spinner = spinners[idx] = new JSpinner(sModel);
                panel.add(spinner);
                spinner.setEditor(new JSpinner.DefaultEditor(spinner));
                spinner.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        int size = (int) spinner.getValue();
                        if (idx == 0) {
                            IconManager.setCategoryIconSize(size);
                            App.get().categoryTree.updateUI();
                        } else if (idx == 1) {
                            IconManager.setGalleryIconSize(size);
                            App.get().gallery.updateUI();
                        } else if (idx == 2) {
                            IconManager.setIconSize(size);
                            App.get().updateIconContainersUI(size, true);
                        }
                        sizes[idx] = size;
                        UiIconSize.saveUserSetting(sizes);
                    }
                });
            }
            JOptionPane.showMessageDialog(App.get(), panel, menu.catIconSize.getText(), JOptionPane.QUESTION_MESSAGE);
        }
    }

    public void exportFileTree(boolean onlyChecked, boolean toZip) {
        int baseDocId = -1;
        if (menu.isTreeMenu) {
            TreePath[] paths = App.get().tree.getSelectionPaths();
            if (paths == null || paths.length != 1) {
                JOptionPane.showMessageDialog(null, Messages.getString("MenuListener.ExportTree.Warn")); //$NON-NLS-1$
                return;
            }
            Node treeNode = (Node) paths[0].getLastPathComponent();
            baseDocId = treeNode.docId;
        }
        ExportFileTree.saveFile(baseDocId, onlyChecked, toZip);
    }

    private static class SpinnerListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent evt) {
            int cnt = (Integer) ((JSpinner) evt.getSource()).getValue();
            App.get().setGalleryColCount(cnt);
        }

    }

}