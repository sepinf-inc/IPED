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

import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import iped3.search.LuceneSearchResult;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;

import org.apache.lucene.search.Query;

public class AppListener implements ActionListener, MouseListener {

    volatile boolean clearSearchBox = false;

    public void updateFileListing() {
        updateFileListing(null);
    }

    public void updateFileListing(Query query) {

        App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
        App.get().hitsTable.scrollRectToVisible(new Rectangle());
        Rectangle a = App.get().resultsTable.getVisibleRect();
        a.setBounds(a.x, 0, a.width, a.height);
        App.get().resultsTable.scrollRectToVisible(a);
        App.get().gallery.scrollRectToVisible(new Rectangle());

        App.get().ipedResult = new MultiSearchResult();
        App.get().getParams().lastSelectedDoc = -1;
        App.get().resultsModel.fireTableDataChanged();
        if (App.get().resultSortKeys == null || (App.get().resultsTable.getRowSorter() != null
                && !App.get().resultsTable.getRowSorter().getSortKeys().isEmpty())) {
            App.get().resultSortKeys = App.get().resultsTable.getRowSorter().getSortKeys();
        }
        App.get().resultsTable.getRowSorter().setSortKeys(null);
        App.get().hitsDock.setTitleText(Messages.getString("AppListener.NoHits")); //$NON-NLS-1$
        App.get().subitemDock.setTitleText(Messages.getString("SubitemTableModel.Subitens")); //$NON-NLS-1$
        App.get().duplicateDock.setTitleText(Messages.getString("DuplicatesTableModel.Duplicates")); //$NON-NLS-1$
        App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount")); //$NON-NLS-1$
        App.get().status.setText(" "); //$NON-NLS-1$

        App.get().compositeViewer.clear();

        App.get().subItemModel.results = new LuceneSearchResult(0);
        App.get().subItemModel.fireTableDataChanged();
        App.get().duplicatesModel.results = new LuceneSearchResult(0);
        App.get().duplicatesModel.fireTableDataChanged();
        App.get().parentItemModel.results = new LuceneSearchResult(0);
        App.get().parentItemModel.fireTableDataChanged();

        String texto = ""; //$NON-NLS-1$
        if (App.get().termo.getSelectedItem() != null) {
            texto = App.get().termo.getSelectedItem().toString();
            if (texto.equals(MarcadoresController.HISTORY_DIV) || texto.equals(App.SEARCH_TOOL_TIP)) {
                texto = ""; //$NON-NLS-1$
                clearSearchBox = true;
                App.get().termo.setSelectedItem(""); //$NON-NLS-1$
            }
            texto = texto.trim();
            MarcadoresController.get().addToRecentSearches(texto);
        }

        if (!texto.isEmpty())
            App.get().termo.setBorder(BorderFactory.createLineBorder(App.get().alertColor, 2, true));
        else
            App.get().termo.setBorder(null);

        try {
            PesquisarIndice task;
            if (query == null)
                task = new PesquisarIndice(texto);
            else
                task = new PesquisarIndice(query);

            task.applyUIQueryFilters();
            task.execute();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {

        if (!clearSearchBox && (evt.getActionCommand().equals("comboBoxChanged")) && //$NON-NLS-1$
                !App.get().filterManager.isUpdatingFilter() && !MarcadoresController.get().updatingHistory) {

            int filterIndex = App.get().filtro.getSelectedIndex();
            if (filterIndex == 0 || filterIndex == -1) {
                App.get().filtro.setBackground(App.get().filterManager.defaultColor);
            } else {
                App.get().filtro.setBackground(App.get().alertColor);
            }

            updateFileListing();

        }

        if (evt.getSource() == App.get().filterDuplicates) {
            if (App.get().filterDuplicates.getForeground() == App.get().alertColor)
                App.get().filterDuplicates.setForeground(App.get().topPanel.getBackground());
            else
                App.get().filterDuplicates.setForeground(App.get().alertColor);

            updateFileListing();
        }

        if (evt.getSource() == App.get().ajuda) {
            FileProcessor exibirAjuda = new FileProcessor(-1, false);
            exibirAjuda.execute();
        }

        if (evt.getSource() == App.get().opcoes) {
            App.get().getContextMenu().show(App.get(), App.get().opcoes.getX(), App.get().opcoes.getHeight());
        }

        if (evt.getSource() == App.get().checkBox) {
            if (App.get().appCase.getMultiMarcadores().getTotalSelected() > 0) {
                int result = JOptionPane.showConfirmDialog(App.get(), Messages.getString("AppListener.UncheckAll"), //$NON-NLS-1$
                        Messages.getString("AppListener.UncheckAll.Title"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
                if (result == JOptionPane.YES_OPTION) {
                    App.get().appCase.getMultiMarcadores().clearSelected();
                }
            } else {
                App.get().appCase.getMultiMarcadores().selectAll();
            }

            App.get().gallery.getDefaultEditor(GalleryCellRenderer.class).stopCellEditing();
            App.get().appCase.getMultiMarcadores().saveState();
            MarcadoresController.get().atualizarGUI();
        }

        if (evt.getSource() == App.get().atualizar) {
            InicializarBusca init = new InicializarBusca(App.get().getSearchParams(), App.get().getProcessingManager(),
                    true);
            init.execute();
        }

        if (evt.getSource() == App.get().exportToZip) {
            App.get().getContextMenu().menuListener.exportFileTree(true, true);
        }

        clearSearchBox = false;

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

        ViewerControl.getInstance().releaseLibreOfficeFocus();

        Object termo = App.get().termo.getSelectedItem();
        if (termo != null && termo.equals(App.SEARCH_TOOL_TIP)
                && App.get().termo.isAncestorOf((Component) evt.getSource())) {
            clearSearchBox = true;
            App.get().termo.setSelectedItem(""); //$NON-NLS-1$
        }

    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

}
