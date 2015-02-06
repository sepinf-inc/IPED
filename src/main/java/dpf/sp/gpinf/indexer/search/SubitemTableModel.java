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

import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.IOUtil;

public class SubitemTableModel extends AbstractTableModel implements MouseListener, ListSelectionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// public ScoreDoc[] results = new ScoreDoc[0];
	SearchResult results = new SearchResult(0);
	int selectedIndex = -1;

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public int getRowCount() {
		return results.length;
	}

	@Override
	public Object getValueAt(int row, int col) {
		if (col == 0)
			return row + 1;
		else {
			try {
				Document doc = App.get().searcher.doc(results.docs[row]);
				return doc.get(IndexItem.NAME);
			} catch (Exception e) {
				// e.printStackTrace();
			}
			return "";
		}
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
	}

	@Override
	public void mouseReleased(MouseEvent evt) {
		if (evt.getClickCount() == 2 && selectedIndex != -1) {

			new Thread() {
				public void run() {
					int docId = results.docs[selectedIndex];
					File file = null;
					try {
						Document doc = App.get().searcher.doc(docId);

						String export = doc.get(IndexItem.EXPORT);
						if (export != null && !export.isEmpty()) {
							file = IOUtil.getRelativeFile(App.get().codePath + "/../..", export);
							file = IOUtil.getReadOnlyFile(file, doc);
						} else
							file = IOUtil.extractSleuthFile(App.get().sleuthCase, doc);

						if (file != null)
							Desktop.getDesktop().open(file.getCanonicalFile());

					} catch (Exception e) {
						try {
							// Windows Only
							Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\"" });
						} catch (Exception e2) {
							try {
								// Linux Only
								Runtime.getRuntime().exec(new String[] { "xdg-open", file.toURI().toURL().toString() });
							} catch (Exception e3) {
								CopiarArquivos.salvarArquivo(docId);
							}
						}
					}
				}
			}.start();

		}

	}

	@Override
	public void valueChanged(ListSelectionEvent evt) {
		ListSelectionModel lsm = (ListSelectionModel) evt.getSource();

		if (lsm.getMinSelectionIndex() == -1 || selectedIndex == lsm.getMinSelectionIndex()) {
			selectedIndex = lsm.getMinSelectionIndex();
			return;
		}

		selectedIndex = lsm.getMinSelectionIndex();
		App.get().textViewer.textTable.scrollRectToVisible(new Rectangle());

		FileProcessor parsingTask = new FileProcessor(results.docs[selectedIndex], false);
		parsingTask.execute();

		App.get().parentItemModel.fireTableDataChanged();
	}

	public void listSubItens(Document doc) {

		String parentId = doc.get(IndexItem.FTKID);
		if (parentId == null)
			parentId = doc.get(IndexItem.ID);
		
		String textQuery = IndexItem.PARENTID + ":" + parentId;
		
		String parentSleuthId = doc.get(IndexItem.SLEUTHID);
		if(parentSleuthId != null)
			textQuery += " " + IndexItem.PARENTSLEUTHID + ":" + parentSleuthId;

		try {
			PesquisarIndice task = new PesquisarIndice(PesquisarIndice.getQuery(textQuery));

			results = task.pesquisar();
			final int sumSubitens = results.length;

			if (sumSubitens > 0)
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().tabbedHits.addTab(sumSubitens + " Anexos/Subitens", App.get().subItemScroll);
					}
				});

		} catch (Exception e) {
			results = new SearchResult(0);
			e.printStackTrace();
		}

		fireTableDataChanged();

	}

}
