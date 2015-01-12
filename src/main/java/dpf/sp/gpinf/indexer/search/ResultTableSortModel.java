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

import javax.swing.table.AbstractTableModel;

public class ResultTableSortModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] columnNames = ResultTableModel.columnNames;
	//{ "", "", "", "Marcador", "Nome", "Tipo", "Tamanho", "Categoria", "Criação", "Modificação", "Acesso", "Hash", "Marcador","Deletado","Caminho" };

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public int getRowCount() {
		return App.get().results.length;
	}

	@Override
	public Object getValueAt(int row, int col) {
		if (col == 2)
			return row;
		else
			return App.get().results.docs[row];
	}
}
