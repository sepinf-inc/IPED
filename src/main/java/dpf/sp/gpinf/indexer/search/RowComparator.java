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

import java.util.Comparator;

import dpf.sp.gpinf.indexer.util.Util;

public class RowComparator implements Comparator<Integer> {

	static String[] fields = ResultTableModel.fields;
	
	private int col;
	static private int[][] order = new int[fields.length + 4][];
	static private int[] loadedCol = { 0, 0 };
	private App app = App.get();

	public RowComparator(int col) {
		this.col = col;
		//if(col >= 4)
		//	loadOrder();
	}

	public static void dispose() {
		order[loadedCol[0]] = null;
		order[loadedCol[1]] = null;
	}

	private void loadOrder() {
		order[loadedCol[1]] = null;
		loadedCol[1] = loadedCol[0];
		try {
			order[col] = (int[]) Util.readObject(App.get().codePath + "/../data/" + fields[col - 4] + ".sort");
		} catch (Exception e) {
			e.printStackTrace();
		}
		loadedCol[0] = col;
	}

	@Override
	final public int compare(Integer a, Integer b) {
		if (col == 1) {
			if (app.marcadores.selected[a] == app.marcadores.selected[b])
				return 0;
			else if (app.marcadores.selected[a] == true)
				return -1;
			else
				return 1;

		} else if (col == 2) {
			return (int)(app.results.scores[a] - app.results.scores[b]);

		} else if (col == 3) {
			return app.marcadores.getLabels(a).compareTo(app.marcadores.getLabels(b));
		
		} else{
			if (order[col] == null)
				loadOrder();
			return order[col][a] - order[col][b];
		}

	}

}
