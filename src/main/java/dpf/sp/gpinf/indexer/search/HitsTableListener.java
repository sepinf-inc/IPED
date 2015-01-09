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

import java.awt.Font;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class HitsTableListener implements ListSelectionListener {

	private int lastSelectedRow = -1;
	private JTextPane resizer = new JTextPane();
	boolean autoSelect = false;

	public HitsTableListener(Font font) {
		resizer.setFont(font);
		resizer.setBorder(BorderFactory.createEmptyBorder());
		resizer.setContentType("text/html");
	}

	private int getWidth(String html) {
		resizer.setText(html);
		return resizer.getPreferredSize().width;
	}

	// TODO melhorar scroll horizontal
	@Override
	public void valueChanged(ListSelectionEvent evt) {

		try {
			int row = App.get().hitsTable.getSelectedRow();
			if (row != -1 && row != lastSelectedRow) {

				long hitOff = App.get().textViewer.textParser.hits.get(row);
				int[] hit = App.get().textViewer.textParser.sortedHits.get(hitOff);
				int hitLen = hit[0];

				int startViewRow = hit[1];
				long viewRowOff = App.get().textViewer.textParser.viewRows.get(startViewRow);
				if (startViewRow == App.MAX_LINES)
					startViewRow += (int) (hitOff - viewRowOff) / App.MAX_LINE_SIZE;

				int endViewRow = hit[2];
				viewRowOff = App.get().textViewer.textParser.viewRows.get(endViewRow);
				if (endViewRow == App.MAX_LINES)
					endViewRow += (int) (hitOff + hitLen - viewRowOff) / App.MAX_LINE_SIZE;

				int viewRow = startViewRow;
				do {
					String line = App.get().textViewer.textViewerModel.getValueAt(viewRow, 0).toString();
					int index1, index2, x = 0, width = 0;
					if ((index1 = line.indexOf(App.HIGHLIGHT_START_TAG)) != -1) {
						String text = line.substring(0, index1);
						x = getWidth(text) - 100;
						index2 = line.indexOf(App.HIGHLIGHT_END_TAG);
						text = line.substring(index1, index2 - App.HIGHLIGHT_START_TAG.length());
						width = App.get().getFontMetrics(App.get().textViewer.textTable.getFont()).stringWidth(text) + 150;
						Rectangle rect = App.get().textViewer.textTable.getCellRect(viewRow, 0, true);
						rect.setBounds(x, rect.y, width, rect.height);
						App.get().textViewer.textTable.scrollRectToVisible(rect);
					}

				} while (++viewRow <= endViewRow);

				if (App.get().textViewer.textParser.firstHitAutoSelected)
					App.get().compositeViewer.setSelectedIndex(0);

			}
			lastSelectedRow = row;
		} catch (Exception e) {
			// e.printStackTrace();
		}

	}
}
