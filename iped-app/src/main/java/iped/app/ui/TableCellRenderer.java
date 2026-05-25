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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.apache.lucene.document.Document;

import iped.app.ui.bookmarks.BookmarkCellRenderer;
import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.task.index.IndexItem;
import iped.localization.LocalizedProperties;

public class TableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;
    private boolean customPaint;
    private BookmarkCellRenderer bookmarkCellRenderer;
    private String colName;
    private int colNumber;
    private JTable parentTable;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        parentTable = table;
        int idx = table.convertRowIndexToModel(row);
        int col = table.convertColumnIndexToModel(column);
        colNumber = col;
        TableModel model = table.getModel();
        colName = model.getColumnName(col);

        Icon icon = null;
        String toopTip = null;
        customPaint = false;

        if (model instanceof SearchResultTableModel) {
            if (colName.equalsIgnoreCase(ResultTableModel.BOOKMARK_COL)) {
                String str = (String) value;
                if (!str.isEmpty()) {
                    IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
                    if (bookmarkCellRenderer == null) {
                        bookmarkCellRenderer = new BookmarkCellRenderer();
                    }
                    bookmarkCellRenderer.setBookmarks(multiBookmarks, str);
                    customPaint = true;
                }

            } else if (colName.equalsIgnoreCase(BookmarkIcon.columnName)) {
                setText("");
                if (value != null) {
                    String str = (String) value;
                    if (!str.isEmpty()) {
                        toopTip = str;
                        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
                        icon = BookmarkIcon.getIcon(multiBookmarks, str);
                    }
                }

            } else if (colName.equalsIgnoreCase(IndexItem.NAME) || colName.equalsIgnoreCase(LocalizedProperties.getLocalizedField(IndexItem.NAME))
                    || (model instanceof DuplicatesTableModel && (colName.equalsIgnoreCase(IndexItem.PATH) || colName.equalsIgnoreCase(LocalizedProperties.getLocalizedField(IndexItem.PATH))))) {
                try {
                    IItemId item = ((SearchResultTableModel) model).getSearchResult().getItem(idx);
                    int docId = App.get().appCase.getLuceneId(item);
                    Document doc = App.get().appCase.getSearcher().doc(docId);
                    if (Boolean.valueOf(doc.get(IndexItem.ISDIR))) {
                        icon = IconManager.getFolderIcon();
                    } else {
                        String type = doc.get(IndexItem.TYPE);
                        String contentType = doc.get(IndexItem.CONTENTTYPE);
                        icon = Boolean.valueOf(doc.get(IndexItem.ISROOT)) ? IconManager.getFileIcon(contentType, type, IconManager.getDiskIcon()) : IconManager.getFileIcon(contentType, type);
                    }
                } catch (IOException e) {
                }
            }
        }

        setIcon(icon);
        setToolTipText(toopTip);

        return this;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (colName.equalsIgnoreCase(ResultTableModel.BOOKMARK_COL) && bookmarkCellRenderer != null) {
            int actualWidth = 0;
            if (parentTable != null) {
                actualWidth = parentTable.getColumnModel().getColumn(colNumber).getWidth();
            }

            // Find which specific bookmark the mouse is over
            String hoveredBookmark = bookmarkCellRenderer.getBookmarkAt(event.getX(), actualWidth);            
            if (hoveredBookmark != null) {
                return "<html>" + BookmarkTree.displayPath(hoveredBookmark) + "</html>";
            }

            // If mouse is not over any specific bookmark, show all
            String[] bookmarksNames = bookmarkCellRenderer.getBookmarkNames();
            if (bookmarksNames == null || bookmarksNames.length == 0) {
                return null;
            }
            
            // Bookmarks tooltip
            StringBuilder toolTip = new StringBuilder("<html>");
            for (String bookmarkName : bookmarksNames) {
                toolTip.append(BookmarkTree.displayPath(bookmarkName));
                toolTip.append("<br>");
            }
            toolTip.append("</html>");

            return toolTip.toString();
        }

        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (customPaint) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            g2.setBackground(getBackground());
            g2.clearRect(0, 0, w, h);
            g2.setFont(getFont());
            bookmarkCellRenderer.paint(g2, w, h);
        } else {
            super.paintComponent(g);
        }
    }
}
