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
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.text.Collator;
import java.util.List;

import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControlImpl;
import dpf.sp.gpinf.indexer.util.Util;

public class ResultTableListener implements ListSelectionListener, MouseListener, KeyListener {
    private long lastKeyTime = -1;
    private String lastKeyString = "";
    private Collator collator = Collator.getInstance();
    
    public ResultTableListener() {
        collator.setStrength(Collator.PRIMARY);
    }
    
	@Override
	public void valueChanged(ListSelectionEvent evt) {
		
		GerenciadorMarcadores.updateCounters();
		
		if (App.get().resultsTable.getSelectedRowCount() == 0 || evt.getValueIsAdjusting())
			return;

		int resultTableLeadSelIdx = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();
		Rectangle a = App.get().resultsTable.getCellRect(resultTableLeadSelIdx, 0, false);
		Rectangle b = App.get().resultsTable.getVisibleRect();
		a.setBounds(b.x, a.y, a.width, a.height);
		App.get().resultsTable.scrollRectToVisible(a);

		if (App.get().resultTab.getSelectedIndex() == 0) {
			App.get().gallery.getDefaultEditor(GalleryCellRenderer.class).stopCellEditing();
			int galleryRow = resultTableLeadSelIdx / App.get().galleryModel.colCount;
			int galleyCol = resultTableLeadSelIdx % App.get().galleryModel.colCount;
			App.get().gallery.scrollRectToVisible(App.get().gallery.getCellRect(galleryRow, galleyCol, false));
			
			App.get().gallery.getSelectionModel().setValueIsAdjusting(true);
			App.get().gallery.clearSelection();
			int[] selRows = App.get().resultsTable.getSelectedRows();
			int start = 0;
			while(start < selRows.length){
			    int i = start + 1;
			    while(i < selRows.length && selRows[i] - selRows[i - 1] == 1)
	                i++;
			    App.get().gallery.setCellSelectionInterval(selRows[start], selRows[i - 1]);
			    start = i;
			}
			App.get().gallery.getSelectionModel().setValueIsAdjusting(true);
		}

		processSelectedFile();

	}

	private synchronized void processSelectedFile() {

		// if(App.get().resultsTable.getSelectedRowCount() > 1)
		// return;

		int viewIndex = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();
		
		if(viewIndex != -1){
			int selectedDoc = App.get().resultsTable.convertRowIndexToModel(viewIndex);
                        Object obj1 = App.get().results;
                        Object obj2 = App.get().lastSelectedDoc;
                        Object obj3 = App.get().hitsTable;
                        Object obj4 = App.get().getTextViewer();
                        Object obj5 = App.get().getTextViewer().textTable;
                        Object obj6 = App.get().tabbedHits;
			if (App.get().results.docs[selectedDoc] != App.get().lastSelectedDoc) {

				App.get().hitsTable.scrollRectToVisible(new Rectangle());
				App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
				App.get().tabbedHits.setTitleAt(0, "0 Ocorrências");
				while (App.get().tabbedHits.getTabCount() > 1)
					App.get().tabbedHits.removeTabAt(1);

				FileProcessor parsingTask = new FileProcessor(App.get().results.docs[selectedDoc], true);
				parsingTask.execute();
			}

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

		} else {
			processSelectedFile();

		}

	}

	@Override
	public void mouseClicked(MouseEvent e) {
            ViewerControlImpl.getInstance().releaseLibreOfficeFocus();
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
			App.get().menu.show((Component)evt.getSource(), evt.getX(), evt.getY());

		}
	}

	@Override
	public void keyPressed(KeyEvent evt) {
	}

	int keyBefore = -1;

	@Override
	public void keyReleased(KeyEvent evt) {
		if (App.get().resultsTable.getSelectedRow() == -1)
			return;

		if ((keyBefore == KeyEvent.VK_CONTROL && evt.getKeyCode() == KeyEvent.VK_C) || (keyBefore == KeyEvent.VK_C && evt.getKeyCode() == KeyEvent.VK_CONTROL)) {
			
            int selCol = App.get().resultsTable.getSelectedColumn();
            if (selCol < 0) return;
            String value = getCell(App.get().resultsTable, App.get().resultsTable.getSelectedRow(), selCol);
			value = value.replace("<html><nobr>", "").replace(App.get().getParams().HIGHLIGHT_START_TAG, "").replace(App.get().getParams().HIGHLIGHT_END_TAG, "");
			StringSelection selection = new StringSelection(value);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, selection);
			

		} else if (evt.getKeyCode() == KeyEvent.VK_SPACE) {
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

		}

		keyBefore = evt.getKeyCode();

	}

	@Override
	public void keyTyped(KeyEvent evt) {
	    char c = evt.getKeyChar();
        if (c == ' ' || (evt.getModifiers() & (InputEvent.CTRL_MASK | InputEvent.ALT_MASK)) != 0) return;

        JTable table = App.get().resultsTable;
        List<? extends SortKey> sortKeys = table.getRowSorter().getSortKeys();
        if (sortKeys.isEmpty()) return;
        int sortCol = sortKeys.get(0).getColumn();
        int viewCol = table.convertColumnIndexToView(sortCol);

        //Provisoriamente as colunas estão fixas 
        //(colunas onde a comparação de String não funcionaria, porque ordenação é numérica ou de Data)
        if (!((RowComparator)((TableRowSorter)table.getRowSorter()).getComparator(sortCol)).isStringComparator())
            return;

        long t = System.currentTimeMillis();
        if (t - lastKeyTime > 500) lastKeyString = "";
        lastKeyTime = t;
        if (lastKeyString.length() != 1 || lastKeyString.charAt(0) != c) lastKeyString += c;
        int initialRow = table.getSelectedRow();
        if (initialRow < 0) initialRow = table.getRowCount() - 1;

        int currRow = initialRow;
        int foundRow = findRow(table, currRow + 1, table.getRowCount() - 1, viewCol, lastKeyString);
        if (foundRow < 0) foundRow = findRow(table, 0, currRow - 1, viewCol, lastKeyString);
        if (foundRow >= 0) {
            table.setRowSelectionInterval(foundRow, foundRow);
            table.scrollRectToVisible(table.getCellRect(foundRow, viewCol, true));
        }
    }

    private int findRow(JTable table, int from, int to, int col, String search) {
        while (from < to) {
            int mid = (from + to) >> 1;
            int cmp = compare(getCell(table, mid, col), search);
            if (cmp > 0) to = mid - 1;
            else if (cmp < 0) from = mid + 1;
            else to = mid;
        }
        if (from == to) {
            if (compare(getCell(table, from, col), search) == 0) return from;
        }
        return -1;
    }

    private int compare(String a, String b) {
        if (a.length() > b.length()) a = a.substring(0, b.length());
        return collator.compare(a, b);
    }

    private String getCell(JTable table, int row, int col) {
        String cell = table.getValueAt(row, col).toString();
        return cell.replace("<html><nobr>", "").replace(App.get().getParams().HIGHLIGHT_START_TAG, "").replace(App.get().getParams().HIGHLIGHT_END_TAG, "");
    }

}
