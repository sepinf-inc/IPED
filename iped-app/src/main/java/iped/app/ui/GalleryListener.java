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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GalleryListener implements ListSelectionListener, MouseListener, KeyListener {

    private GalleryCellEditor cellEditor;
    private static Logger logger = LoggerFactory.getLogger(ResultTableListener.class);

    @Override
    public void valueChanged(ListSelectionEvent e) {

        if (!ResultTableListener.syncingSelectedItems && !e.getValueIsAdjusting()) {
            ResultTableListener.syncingSelectedItems = true;
            App.get().resultsTable.getSelectionModel().setValueIsAdjusting(true);
            App.get().resultsTable.clearSelection();
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
            int leadIdx = App.get().gallery.getLeadSelectionIndex();
            if (leadIdx != -1) {
                App.get().resultsTable.addRowSelectionInterval(leadIdx, leadIdx);
            }
            App.get().resultsTable.getSelectionModel().setValueIsAdjusting(false);
            ResultTableListener.syncingSelectedItems = false;
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

    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        for (MouseListener ml : App.get().resultsTable.getListeners(MouseListener.class)) {
            ml.mouseReleased(evt);
        }
    }

    @Override
    public void keyPressed(KeyEvent evt) {
        for (KeyListener kl : App.get().resultsTable.getListeners(KeyListener.class)) {
            kl.keyPressed(evt);
        }
    }

    @Override
    public void keyReleased(KeyEvent evt) {
        cellEditor.stopCellEditing();
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
