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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IMultiBookmarks;
import iped.engine.util.Util;

public class GalleryCellRenderer implements TableCellRenderer {

    private final JPanel top = new JPanel(), panel = new JPanel();
    private final GalleryThumbLabel label = new GalleryThumbLabel();
    private final JLabel cLabel = new JLabel();
    private final JCheckBox check = new JCheckBox();
    private Border selBorder;
    private Border border;
    private Color selColor;
    private Color color;
    private Color background;

    public GalleryCellRenderer() {
        super();
        panel.setLayout(new BorderLayout());
        top.setLayout(new BorderLayout());
        top.add(check, BorderLayout.LINE_START);
        top.add(cLabel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(label, BorderLayout.CENTER);
        label.setHorizontalAlignment(JLabel.CENTER);

        updateUI();
    }

    public void updateUI() {
        selColor = UIManager.getColor("Gallery.cellSelected");
        if (selColor == null)
            selColor = new Color(180, 200, 230);

        color = UIManager.getColor("Gallery.cellBackground");
        if (color == null)
            color = Color.white;

        background = UIManager.getColor("Gallery.background");
        if (background == null)
            background = new Color(240, 240, 242);

        Color borderColor = UIManager.getColor("Gallery.cellBorder");
        if (borderColor == null)
            borderColor = new Color(200, 200, 202);
        border = BorderFactory.createLineBorder(borderColor, 1);

        Color selBorderColor = UIManager.getColor("Gallery.cellSelectBorder");
        if (selBorderColor == null)
            selBorderColor = new Color(20, 50, 80);
        selBorder = BorderFactory.createLineBorder(selBorderColor, 1);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int col) {

        GalleryValue cellValue = (GalleryValue) value;
        if (cellValue == null || cellValue.id == null) {
            JPanel panel = new JPanel();
            panel.setBackground(background);
            return panel;
        }

        IMultiBookmarks bookmarks = App.get().appCase.getMultiBookmarks();
        check.setSelected(bookmarks.isChecked(cellValue.id));
        cLabel.setText(cellValue.name);
        String itemBookmarksStr = Util.concatStrings(bookmarks.getBookmarkList(cellValue.id), true);
        cLabel.setToolTipText(itemBookmarksStr.isEmpty() ? null : itemBookmarksStr);
        cLabel.setIcon(BookmarkIcon.getIcon(bookmarks, itemBookmarksStr));

        label.setValue(cellValue);

        Color c = null;
        if (isSelected) {
            c = selColor;
            panel.setBorder(selBorder);
        } else {
            c = color;
            panel.setBorder(border);
        }
        panel.setBackground(c);
        top.setBackground(c);

        return panel;
    }
}

class GalleryThumbLabel extends JLabel {
    private static final long serialVersionUID = 1L;

    // Limit how much images can be enlarged (usually thumbs are down sized, but
    // small images may be enlarged).
    private static final double maxEnlargeFactor = 2;

    private static final String unsupportedIconText = "<html><center>"
            + Messages.getString("UnsupportedIcon.Unavailable") + "</center></html>";

    private Color warningColor;

    private GalleryValue value;

    private static final RenderingHints renderingHints;
    static {
        Map<Key, Object> hints = new HashMap<Key, Object>();
        hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    public void updateUI() {
        super.updateUI();
        warningColor = UIManager.getColor("Gallery.warning");
        if (warningColor == null)
            warningColor = Color.red;
    }

    public void setValue(GalleryValue value) {
        this.value = value;
        if (value.icon == null && value.image == null) {
            setForeground(null);
            setText("...");
            setIcon(null);
        } else if (value.icon != null && value.unsupportedType) {
            setForeground(warningColor);
            setText(unsupportedIconText);
            setIcon(value.icon);
        } else {
            setText(null);
            if (value.image != null) {
                setIcon(null);
            } else {
                setIcon(value.icon);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (value != null && value.image != null) {
            int labelW = getWidth();
            int labelH = getHeight();
            int w = value.image.getWidth();
            int h = value.image.getHeight();
            if (w * labelH < labelW * h) {
                if (h * maxEnlargeFactor < labelH) {
                    h *= maxEnlargeFactor;
                    w *= maxEnlargeFactor;
                } else {
                    w = w * labelH / h;
                    h = labelH;
                }
            } else {
                if (w * maxEnlargeFactor < labelW) {
                    h *= maxEnlargeFactor;
                    w *= maxEnlargeFactor;
                } else {
                    h = h * labelW / w;
                    w = labelW;
                }
            }
            Graphics2D g2 = (Graphics2D) g;
            RenderingHints oldHints = g2.getRenderingHints();
            g2.setRenderingHints(renderingHints);
            g2.drawImage(value.image, (labelW - w) / 2, (labelH - h) / 2, w, h, null);
            g2.setRenderingHints(oldHints);
        } else {
            super.paintComponent(g);
        }
    }
}
