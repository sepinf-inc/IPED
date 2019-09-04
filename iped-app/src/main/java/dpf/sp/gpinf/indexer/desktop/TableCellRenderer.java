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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemId;
import iped3.IItemId;

public class TableCellRenderer extends DefaultTableCellRenderer {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private static Icon folderIcon = UIManager.getIcon("FileView.directoryIcon"); //$NON-NLS-1$
    private static Icon fileIcon = UIManager.getIcon("FileView.fileIcon"); //$NON-NLS-1$
    private static Icon diskIcon = UIManager.getIcon("FileView.hardDriveIcon"); //$NON-NLS-1$

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {

        DefaultTableCellRenderer result = (DefaultTableCellRenderer) super.getTableCellRendererComponent(table, value,
                isSelected, hasFocus, row, column);

        int idx = table.convertRowIndexToModel(row);
        int col = table.convertColumnIndexToModel(column);

        if (table.getModel().getColumnName(col).equalsIgnoreCase(IndexItem.NAME)) {
            try {
                IItemId item = ((SearchResultTableModel) table.getModel()).getSearchResult().getItem(idx);
                int docId = App.get().appCase.getLuceneId(item);
                Document doc = App.get().appCase.getSearcher().doc(docId);
                if (Boolean.valueOf(doc.get(IndexItem.ISDIR))) {
                    result.setIcon(folderIcon);
                } else if (Boolean.valueOf(doc.get(IndexItem.ISROOT))) {
                    result.setIcon(diskIcon);
                } else {
                    result.setIcon(fileIcon);
                }

            } catch (IOException e) {
                result.setIcon(null);
            }

        } else {
            result.setIcon(null);
        }

        return result;
    }

}
