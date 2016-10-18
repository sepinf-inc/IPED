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
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import dpf.sp.gpinf.indexer.search.ItemId;

public class GalleryListener implements ListSelectionListener, MouseListener, KeyListener {

  private GalleryCellEditor cellEditor;

  @Override
  public void valueChanged(ListSelectionEvent e) {

    if (App.get().resultTab.getSelectedIndex() == 1 && !e.getValueIsAdjusting()) {
      App.get().resultsTable.clearSelection();
      App.get().resultsTable.getSelectionModel().setValueIsAdjusting(true);
      int[] selRows = App.get().gallery.getSelectedCells();
      int start = 0;
      while (start < selRows.length) {
        int i = start + 1;
        while (i < selRows.length && selRows[i] - selRows[i - 1] == 1) {
          i++;
        }
        App.get().resultsTable.addRowSelectionInterval(selRows[start], selRows[i - 1]);
        start = i;
      }
      App.get().resultsTable.getSelectionModel().setValueIsAdjusting(false);
    }

  }

  @Override
  public void mouseClicked(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseEntered(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseExited(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mousePressed(MouseEvent evt) {
    if (evt.isPopupTrigger()) {
      App.get().menu.show((Component) evt.getSource(), evt.getX(), evt.getY());

    }

  }

  @Override
  public void mouseReleased(MouseEvent evt) {

    if (evt.getClickCount() == 2) {
    	int modelIdx = App.get().resultsTable.convertRowIndexToModel(App.get().resultsTable.getSelectionModel().getLeadSelectionIndex());
    	ItemId item = App.get().ipedResult.getItem(modelIdx);
        int docId = App.get().appCase.getLuceneId(item);
    	ExternalFileOpen.open(docId);

    } else if (evt.isPopupTrigger()) {
      App.get().menu.show((Component) evt.getSource(), evt.getX(), evt.getY());

    }

  }

  private boolean shiftDown = false, ctrlDown = false;

  @Override
  public void keyPressed(KeyEvent evt) {

    if (evt.getKeyCode() == KeyEvent.VK_SPACE) {
      cellEditor.stopCellEditing();
      int col = App.get().resultsTable.convertColumnIndexToView(1);
      int firstRow = App.get().resultsTable.getSelectedRow();
      boolean value = true;
      if (firstRow != -1 && (Boolean) App.get().resultsTable.getValueAt(firstRow, col)) {
        value = false;
      }

      MarcadoresController.get().setMultiSetting(true);
      int[] selectedRows = App.get().resultsTable.getSelectedRows();
      for (int i = 0; i < selectedRows.length; i++) {
    	  if(i == selectedRows.length - 1)
    		  MarcadoresController.get().setMultiSetting(false);
    	  App.get().resultsTable.setValueAt(value, selectedRows[i], col);
      }

    } else if (evt.getKeyCode() == KeyEvent.SHIFT_DOWN_MASK) {
      shiftDown = true;

    } else if (evt.getKeyCode() == KeyEvent.CTRL_DOWN_MASK) {
      ctrlDown = true;
    }

  }

  @Override
  public void keyReleased(KeyEvent evt) {

    if (evt.getKeyCode() == KeyEvent.SHIFT_DOWN_MASK) {
      shiftDown = false;

    } else if (evt.getKeyCode() == KeyEvent.CTRL_DOWN_MASK) {
      ctrlDown = false;
    }

  }

  @Override
  public void keyTyped(KeyEvent arg0) {
    // TODO Auto-generated method stub

  }

  public void setCellEditor(GalleryCellEditor cellEditor) {
    this.cellEditor = cellEditor;
  }

  public GalleryCellEditor getCellEditor() {
    return cellEditor;
  }
}
