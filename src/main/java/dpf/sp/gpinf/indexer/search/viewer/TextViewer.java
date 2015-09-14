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
package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.TextParser;
import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.LuceneSimpleHTMLEncoder;

public class TextViewer extends AbstractViewer implements KeyListener, MouseListener {

	public static Font font = new Font("Courier New", Font.PLAIN, 11);

	public JTable textTable;
	public TextViewerModel textViewerModel;
	private JScrollPane viewerScroll;
	public TextParser textParser;
	private TemporaryResources tmp;

	public TextViewer() {
		super(new GridLayout());
		textViewerModel = new TextViewerModel();
		textTable = new JTable(textViewerModel);
		textTable.setFont(font);
		// textTable.getColumnModel().getColumn(0).setCellRenderer(new
		// ViewerCellRenderer());
		viewerScroll = new JScrollPane(textTable);
		textTable.setFillsViewportHeight(true);
		textTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		textTable.getColumnModel().getColumn(0).setPreferredWidth(2000);
		textTable.setShowGrid(false);
		textTable.setBackground(Color.WHITE);
		textTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
		textTable.addKeyListener(this);
		textTable.addMouseListener(this);
		this.getPanel().add(viewerScroll);
	}

	@Override
	public String getName() {
		return "Texto";
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return true;
	}

	@Override
	public void init() {
		tmp = new TemporaryResources();

	}

	@Override
	public void dispose() {
		try {
			tmp.dispose();
		} catch (TikaException e) {
			e.printStackTrace();
		}

	}
	
	@Override
	public void loadFile(StreamSource content, Set<String> highlightTerms) {
		if (content == null && textParser != null) {
			textParser.cancel(false);

			textParser.sortedHits = new TreeMap<Long, int[]>();
			textParser.hits = new ArrayList<Long>();
			textParser.viewRows = new ArrayList<Long>();

			App.get().hitsModel.fireTableDataChanged();
			textViewerModel.fireTableDataChanged();
			
		}
	}

	@Override
	public void loadFile(StreamSource content, String contentType, Set<String> highlightTerms) {

		if(content == null)
			loadFile(content, null);
		
		else{
			textParser = new TextParser(content, contentType, tmp);
			textParser.execute();
		}

	}

	public class TextViewerModel extends AbstractTableModel {

		private static final long serialVersionUID = 1L;

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public int getRowCount() {
			if (textParser != null)
				try {
					int lines = textParser.viewRows.size() - 1;
					if (lines == App.MAX_LINES)
						lines = App.MAX_LINES + (int) ((TextParser.parsedFile.size() - textParser.viewRows.get(App.MAX_LINES)) / App.MAX_LINE_SIZE) + 1;
					return lines;

				} catch (Exception e) {
				}
			return 0;
		}

		@Override
		public String getColumnName(int col) {
			return "";
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return false;
		}

		@Override
		public Object getValueAt(int row, int col) {
			try {
				long off = 0, len;
				if (row < App.MAX_LINES) {
					off = textParser.viewRows.get(row);
					len = textParser.viewRows.get(row + 1) - off;
				} else {
					off = textParser.viewRows.get(App.MAX_LINES) + (long) (row - App.MAX_LINES) * App.MAX_LINE_SIZE;
					len = App.MAX_LINE_SIZE;

					// Tratamento para não dividir hits destacados
					// Desloca início da linha para final de fragmento com hit
					Long hitOff = textParser.sortedHits.floorKey(off);
					if (hitOff != null) {
						int[] hit = textParser.sortedHits.get(hitOff);
						if (hitOff < off && hitOff + hit[0] > off) {
							len -= (hitOff + hit[0] - off);
							if (len < 0)
								len = 0;
							off = hitOff + hit[0];
						}
					}
					// estende linha até final do fragmento com hit
					hitOff = textParser.sortedHits.floorKey(off + len);
					if (hitOff != null) {
						int[] hit = textParser.sortedHits.get(hitOff);
						if (hitOff < off + len && hitOff + hit[0] > off + len)
							len = hitOff + hit[0] - off;
					}

					if (off + len > TextParser.parsedFile.size())
						len = TextParser.parsedFile.size() - off;
				}

				ByteBuffer data = ByteBuffer.allocate((int) len);
				int nread;
				do {
					nread = TextParser.parsedFile.read(data, off);
					off += nread;
				} while (nread != -1 && data.hasRemaining());

				data.flip();
				String line = (new String(data.array(), "windows-1252")).replaceAll("\n", " ").replaceAll("\r", " ");/*
																													 * .
																													 * replaceAll
																													 * (
																													 * "\t"
																													 * ,
																													 * "&#09;"
																													 * )
																													 * .
																													 * replaceAll
																													 * (
																													 * "  "
																													 * ,
																													 * "&nbsp;&nbsp; "
																													 * )
																													 */
				return "<html><pre>" + line + "</pre></html>";

			} catch (Exception e) {
				// e.printStackTrace();
				return "";
			}

		}

	}

	int keyBefore = -1;

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent evt) {
		if (textTable.getSelectedRow() == -1)
			return;

		if ((keyBefore == KeyEvent.VK_CONTROL && evt.getKeyCode() == KeyEvent.VK_C) || (keyBefore == KeyEvent.VK_C && evt.getKeyCode() == KeyEvent.VK_CONTROL)) {
			StringBuilder copy = new StringBuilder();
			for (Integer row : textTable.getSelectedRows()) {
				String value = textViewerModel.getValueAt(row, 0).toString();
				value = value.replaceAll("<html><pre>", "").replaceAll("</pre></html>", "");
				value = value.replaceAll(App.HIGHLIGHT_START_TAG, "").replaceAll(App.HIGHLIGHT_END_TAG, "");
				value = LuceneSimpleHTMLEncoder.htmlDecode(value);
				copy.append(value + "\r\n");
			}
			StringSelection stringSelection = new StringSelection(copy.toString());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, stringSelection);
		}
		keyBefore = evt.getKeyCode();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void scrollToNextHit(boolean forward) {

		currentHit = App.get().hitsTable.getSelectedRow();
		totalHits = textParser.hits.size();
		if (forward) {
			if (currentHit < totalHits - 1)
				App.get().hitsTable.setRowSelectionInterval(currentHit + 1, currentHit + 1);

		} else {
			if (currentHit > 0)
				App.get().hitsTable.setRowSelectionInterval(currentHit - 1, currentHit - 1);

		}
		App.get().hitsTable.scrollRectToVisible(App.get().hitsTable.getCellRect(App.get().hitsTable.getSelectionModel().getLeadSelectionIndex(), 0, false));

	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		CompositeViewerHelper.releaseLOFocus();

	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

}
