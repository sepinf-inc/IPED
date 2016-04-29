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

import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JOptionPane;

public class AppListener implements ActionListener, MouseListener {

  volatile boolean clearSearchBox = false;

  public void updateFileListing() {

    App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
    App.get().hitsTable.scrollRectToVisible(new Rectangle());
    Rectangle a = App.get().resultsTable.getVisibleRect();
    a.setBounds(a.x, 0, a.width, a.height);
    App.get().resultsTable.scrollRectToVisible(a);
    App.get().gallery.scrollRectToVisible(new Rectangle());

    App.get().results = new SearchResult(0);
    App.get().getParams().lastSelectedDoc = -1;
    App.get().resultsModel.fireTableDataChanged();
    if (App.get().resultSortKeys == null || (App.get().resultsTable.getRowSorter() != null && !App.get().resultsTable.getRowSorter().getSortKeys().isEmpty())) {
      App.get().resultSortKeys = App.get().resultsTable.getRowSorter().getSortKeys();
    }
    App.get().resultsTable.setRowSorter(null);
    App.get().resultsTable.setRowSorter(new ResultTableRowSorter());
    App.get().tabbedHits.setTitleAt(0, "0 Ocorrências");
    App.get().status.setText(" ");

    App.get().compositeViewer.clear();

    App.get().subItemModel.results = new SearchResult(0);
    App.get().subItemModel.fireTableDataChanged();
    App.get().parentItemModel.results = new SearchResult(0);
    App.get().parentItemModel.fireTableDataChanged();
    while (App.get().tabbedHits.getTabCount() > 1) {
      App.get().tabbedHits.removeTabAt(1);
    }

    String texto = "";
    if (App.get().termo.getSelectedItem() != null) {
      texto = App.get().termo.getSelectedItem().toString();
      if (texto.equals(Marcadores.HISTORY_DIV) || texto.equals(App.SEARCH_TOOL_TIP)) {
        texto = "";
        clearSearchBox = true;
        App.get().termo.setSelectedItem("");
      }
      App.get().marcadores.addToTypedWordList(texto);

    }

    try {
      PesquisarIndice task = new PesquisarIndice(texto);
      task.execute();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void actionPerformed(ActionEvent evt) {

    if (!clearSearchBox && !App.get().filterManager.isUpdatingFilter() && (evt.getActionCommand().equals("comboBoxChanged"))) {

      if (App.get().filtro.getSelectedIndex() == 0) {
        App.get().filtro.setBackground(App.get().filterManager.defaultColor);
      } else {
        App.get().filtro.setBackground(App.get().alertColor);
      }

      updateFileListing();

    }

    if (evt.getSource() == App.get().ajuda) {
      FileProcessor exibirAjuda = new FileProcessor(-1, false);
      exibirAjuda.execute();
    }

    if (evt.getSource() == App.get().opcoes) {
      App.get().menu.show(App.get(), App.get().opcoes.getX(), App.get().opcoes.getHeight());
    }

    if (evt.getSource() == App.get().checkBox) {
      if (App.get().marcadores.selectedItens > 0) {
        int result = JOptionPane.showConfirmDialog(App.get(), "Deseja realmente desmarcar todos os itens?", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
          App.get().marcadores.selectedItens = 0;
          for (int i = 0; i < App.get().marcadores.selected.length; i++) {
            App.get().marcadores.selected[i] = false;
          }
        }
      } else {
        App.get().marcadores.selectedItens = App.get().totalItens;
        for (int i = 0; i < App.get().marcadores.selected.length; i++) {
          App.get().marcadores.selected[i] = true;
        }
      }

      App.get().gallery.getDefaultEditor(GalleryCellRenderer.class).stopCellEditing();
      App.get().marcadores.saveState();
      App.get().marcadores.atualizarGUI();
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
    if (termo != null && termo.equals(App.SEARCH_TOOL_TIP) && App.get().termo.isAncestorOf((Component) evt.getSource())) {

      clearSearchBox = true;
      App.get().termo.setSelectedItem("");
    }

  }

  @Override
  public void mouseReleased(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

}
