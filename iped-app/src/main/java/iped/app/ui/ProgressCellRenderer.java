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

import java.awt.Color;
import java.awt.Component;

import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;

public class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {

    private static final long serialVersionUID = 1L;

    private Color alternateColor, backgroundColor;

    public ProgressCellRenderer() {
        super(SwingConstants.HORIZONTAL);
        setBorderPainted(false);
        setMaximum(100);
        setStringPainted(true);
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
        int i = (int) Math.floor((Float) value);
        if (i == 1000) {
            setString("REF");
            this.setValue(100);
        } else {
            setString(null);
            this.setValue(i);
        }
        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else if (alternateColor != null && row % 2 != 0) {
            setBackground(alternateColor);
        } else {
            setBackground(backgroundColor);
        }
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        Color c = UIManager.getColor("Table.alternateRowColor");
        if (c != null)
            alternateColor = new Color(c.getRGB());
        c = UIManager.getColor("Table.background");
        if (c != null)
            backgroundColor = new Color(c.getRGB());
    }
}
