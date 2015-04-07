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

import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.Util;

public class GalleryListener implements ListSelectionListener, MouseListener, KeyListener {

	private GalleryCellEditor cellEditor;

	@Override
	public void valueChanged(ListSelectionEvent e) {

		if (App.get().resultTab.getSelectedIndex() == 1 && !e.getValueIsAdjusting()) {
			int[] rows = App.get().gallery.getSelectedRows();
			int[] cols = App.get().gallery.getSelectedColumns();
			App.get().resultsTable.clearSelection();
			App.get().resultsTable.getSelectionModel().setValueIsAdjusting(true);
			int colCount = App.get().galleryModel.colCount;
			int length = App.get().results.length;
			for (int row : rows)
				for (int col : cols) {
					int idx = row * colCount + col;
					if (idx < length)
						App.get().resultsTable.getSelectionModel().addSelectionInterval(idx, idx);
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
			App.get().menu.show((Component)evt.getSource(), evt.getX(), evt.getY());

		}

	}

	@Override
	public void mouseReleased(MouseEvent evt) {

		if (evt.getClickCount() == 2) {

			new Thread() {
				public void run() {
					int docId = App.get().results.docs[App.get().resultsTable.convertRowIndexToModel(App.get().resultsTable.getSelectionModel().getLeadSelectionIndex())];
					File file = null;
					try {
						Document doc = App.get().searcher.doc(docId);

						String export = doc.get(IndexItem.EXPORT);
						if (export != null && !export.isEmpty()) {
							file = Util.getRelativeFile(App.get().codePath + "/../..", export);
							file = Util.getReadOnlyFile(file, doc);

						} else
							file = Util.extractSleuthFile(App.get().sleuthCase, doc);

						if (file != null)
							Desktop.getDesktop().open(file.getCanonicalFile());

					} catch (Exception e) {
						// e.printStackTrace();
						try {
							if (System.getProperty("os.name").startsWith("Windows"))
								Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\"" });
							else
								Runtime.getRuntime().exec(new String[] { "xdg-open", file.toURI().toURL().toString() });
							
						} catch (Exception e2) {
							e2.printStackTrace();
							CopiarArquivos.salvarArquivo(docId);
						}
					}
				}
			}.start();

		} else if (evt.isPopupTrigger()) {
			App.get().menu.show((Component)evt.getSource(), evt.getX(), evt.getY());

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
			if (firstRow != -1 && (Boolean) App.get().resultsTable.getValueAt(firstRow, col))
				value = false;

			App.get().marcadores.multiSetting = true;
			for (Integer row : App.get().resultsTable.getSelectedRows())
				App.get().resultsTable.setValueAt(value, row, col);
			App.get().marcadores.multiSetting = false;
			App.get().marcadores.saveState();
			App.get().marcadores.atualizarGUI();

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
