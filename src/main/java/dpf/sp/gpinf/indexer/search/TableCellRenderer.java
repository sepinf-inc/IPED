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

import java.awt.Component;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.process.task.FileDocument;

public class TableCellRenderer extends DefaultTableCellRenderer{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
	private static Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
	private static Icon diskIcon = UIManager.getIcon("FileView.hardDriveIcon");

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		
		DefaultTableCellRenderer result = (DefaultTableCellRenderer)super.getTableCellRendererComponent(table,  value,  isSelected,  hasFocus,  row,  column);
		
		int idx = App.get().resultsTable.convertRowIndexToModel(row);
		
		if(column == 4){
			try {
				int docId = App.get().results.docs[idx];
				Document doc = App.get().searcher.doc(docId);
				if(Boolean.valueOf(doc.get(FileDocument.ISDIR)))
					result.setIcon(folderIcon);
				
				else if(Boolean.valueOf(doc.get(FileDocument.ISROOT)))
					result.setIcon(diskIcon);
				else
					result.setIcon(fileIcon);
				
			} catch (IOException e) {
				result.setIcon(null);
			}
		
		}else
			result.setIcon(null);
		
		
		return result;
	}
	
}
