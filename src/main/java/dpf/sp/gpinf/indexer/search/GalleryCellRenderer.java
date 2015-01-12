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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

import dpf.sp.gpinf.indexer.util.ImageUtil;

public class GalleryCellRenderer implements TableCellRenderer {

	JPanel top = new JPanel(), panel = new JPanel();
	// JLayeredPane panel = new JLayeredPane();
	JLabel label = new JLabel(), cLabel = new JLabel();
	JCheckBox check = new JCheckBox();
	Border selBorder = BorderFactory.createLineBorder(new Color(50, 50, 100), 1, false);
	Border border = BorderFactory.createLineBorder(new Color(200, 200, 200), 1, false);
	public static int labelH;

	public GalleryCellRenderer() {
		super();
		panel.setLayout(new BorderLayout());
		top.setLayout(new BorderLayout());
		top.add(check, BorderLayout.LINE_START);
		top.add(cLabel, BorderLayout.CENTER);
		panel.add(top, BorderLayout.NORTH);
		panel.add(label, BorderLayout.CENTER);

		// Layered Code
		/*
		 * panel.add(label, new Integer(0)); panel.add(check, new Integer(1));
		 * label.setBounds(0,0, 100,100); check.setBounds(0, 0, 20, 20);
		 */
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {

		GalleryValue cellValue = (GalleryValue) value;
		if (cellValue.docId == -1)
			return new JPanel();

		check.setSelected(App.get().marcadores.selected[cellValue.docId]);
		cLabel.setText(cellValue.name);

		if (cellValue.icon == null && cellValue.image == null) {
			label.setText("...");
			label.setIcon(null);
		} else {
			label.setText(null);
			if (cellValue.image != null) {
				int labelW = table.getWidth() / table.getColumnCount() - 2;
				labelH = label.getHeight();
				BufferedImage image = cellValue.image;
				int w = Math.min(cellValue.originalW, labelW);
				int h = Math.min(cellValue.originalH, labelH);
				image = ImageUtil.resizeImage(image, w, h);

				label.setIcon(new ImageIcon(image));
			} else
				label.setIcon(cellValue.icon);
		}

		if (isSelected) {
			panel.setBackground(new Color(180, 200, 230));
			panel.setBorder(selBorder);
			top.setBackground(new Color(180, 200, 230));
		} else {
			panel.setBackground(Color.WHITE);
			panel.setBorder(border);
			top.setBackground(Color.WHITE);
		}

		return panel;
	}

}
