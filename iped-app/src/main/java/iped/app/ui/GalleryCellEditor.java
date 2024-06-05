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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellEditor;

import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IMultiBookmarks;
import iped.engine.util.Util;

public class GalleryCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {

    private static final long serialVersionUID = 1L;

    int row, col;
    JPanel top = new JPanel(), panel = new JPanel();
    // JLayeredPane panel = new JLayeredPane();
    JLabel label = new JLabel(), cLabel = new JLabel();
    JCheckBox check = new JCheckBox();
    Border selBorder;
    Color selColor;
    Color background;
    Color warningColor;

    public GalleryCellEditor() {
        super();
        panel.setLayout(new BorderLayout());
        top.setLayout(new BorderLayout());
        top.add(check, BorderLayout.LINE_START);
        top.add(cLabel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(label, BorderLayout.CENTER);

        label.setHorizontalAlignment(JLabel.CENTER);
        check.addActionListener(this);

        updateUI();
    }

    public void updateUI() {
        selColor = UIManager.getColor("Gallery.cellSelected");
        if (selColor == null)
            selColor = new Color(180, 200, 230);

        background = UIManager.getColor("Gallery.background");
        if (background == null)
            background = new Color(240, 240, 242);

        Color selBorderColor = UIManager.getColor("Gallery.cellSelectBorder");
        if (selBorderColor == null)
            selBorderColor = new Color(20, 50, 80);
        selBorder = BorderFactory.createLineBorder(selBorderColor, 1);

        warningColor = UIManager.getColor("Gallery.warning");
        if (warningColor == null)
            warningColor = Color.red;
    }

    @Override
    public Object getCellEditorValue() {
        return new JPanel();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {

        table.putClientProperty("terminateEditOnFocusLost", true); //$NON-NLS-1$

        GalleryValue cellValue = (GalleryValue) value;
        if (cellValue.id == null) {
            JPanel panel = new JPanel();
            panel.setBackground(background);
            return panel;
        }

        IMultiBookmarks bookmarks = App.get().appCase.getMultiBookmarks();
        check.setSelected(bookmarks.isChecked(cellValue.id));
        cLabel.setText(cellValue.name);
        String itemBookmarksStr = Util.concatStrings(bookmarks.getBookmarkList(cellValue.id));
        cLabel.setToolTipText(itemBookmarksStr.isEmpty() ? null : itemBookmarksStr);
        cLabel.setIcon(BookmarkIcon.getIcon(bookmarks, itemBookmarksStr));

        GalleryCellRenderer.adjustGalleryCellContent(cellValue, label, warningColor, table);

        panel.setBackground(selColor);
        top.setBackground(selColor);
        panel.setBorder(selBorder);
        this.row = row;
        this.col = col;

        return panel;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {

        if (evt.getSource() == check) {
            int idx = row * App.get().getGalleryColCount() + col;
            App.get().resultsTable.setValueAt(check.isSelected(), idx, 1);
        }

        this.stopCellEditing();

    }

}
