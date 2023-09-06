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
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.apache.lucene.document.Document;

import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.task.index.IndexItem;
import iped.localization.LocalizedProperties;

public class TableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int idx = table.convertRowIndexToModel(row);
        int col = table.convertColumnIndexToModel(column);
        TableModel model = table.getModel();
        String colName = model.getColumnName(col);
        Icon icon = null;
        String toopTip = null;
        if (model instanceof SearchResultTableModel && colName.equalsIgnoreCase(BookmarkIcon.columnName)) {
            setText("");
            if (value != null) {
                String str = (String) value;
                if (!str.isEmpty()) {
                    toopTip = str;
                    String sep = " | ";
                    IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
                    if (str.indexOf(sep) < 0) {
                        icon = BookmarkIcon.getIcon(multiBookmarks.getBookmarkColor(str));
                    } else {
                        String[] bookmarks = str.split(sep);
                        Color[] colors = new Color[bookmarks.length];
                        for (int i = 0; i < bookmarks.length; i++) {
                            colors[i] = multiBookmarks.getBookmarkColor(bookmarks[i]);
                        }
                        // TODO: Handle multiple bookmarks
                        icon = BookmarkIcon.getIcon(colors[0]);
                    }
                }
            }

        } else if (model instanceof SearchResultTableModel) {
            if (colName.equalsIgnoreCase(IndexItem.NAME)
                    || colName.equalsIgnoreCase(LocalizedProperties.getLocalizedField(IndexItem.NAME))
                    || (model instanceof DuplicatesTableModel && (colName.equalsIgnoreCase(IndexItem.PATH)
                            || colName.equalsIgnoreCase(LocalizedProperties.getLocalizedField(IndexItem.PATH))))) {
                try {
                    IItemId item = ((SearchResultTableModel) model).getSearchResult().getItem(idx);
                    int docId = App.get().appCase.getLuceneId(item);
                    Document doc = App.get().appCase.getSearcher().doc(docId);
                    if (Boolean.valueOf(doc.get(IndexItem.ISDIR))) {
                        icon = IconManager.getFolderIcon();
                    } else {
                        String type = doc.get(IndexItem.TYPE);
                        String contentType = doc.get(IndexItem.CONTENTTYPE);
                        icon = Boolean.valueOf(doc.get(IndexItem.ISROOT))
                                ? IconManager.getFileIcon(contentType, type, IconManager.getDiskIcon())
                                : IconManager.getFileIcon(contentType, type);
                    }
                } catch (IOException e) {
                }
            }
        }

        setIcon(icon);
        setToolTipText(toopTip);

        return this;
    }
}
