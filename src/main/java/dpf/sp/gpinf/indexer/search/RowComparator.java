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

import dpf.sp.gpinf.indexer.util.IOUtil;

public class RowComparator implements Comparator<Integer> {

	static String[] fields = ResultTableModel.fields;
	//{ "nome", "tipo", "tamanho", "categoria", "criacao", "modificacao", "acesso", "hash", "marcador", "deletado", "caminho" };
	
	private int col;
	static private int[][] order = new int[fields.length + 4][];
	static private int[] loadedCol = { 0, 0 };
	App app = App.get();

	public RowComparator(int col) {
		this.col = col;
		// if(col >= 4)
		// loadOrder();
	}

	public static void dispose() {
		order[loadedCol[0]] = null;
		order[loadedCol[1]] = null;
	}

	private void loadOrder() {
		order[loadedCol[1]] = null;
		loadedCol[1] = loadedCol[0];
		try {
			order[col] = (int[]) IOUtil.readObject(App.get().codePath + "/../data/" + fields[col - 4] + ".sort");
		} catch (Exception e) {
			e.printStackTrace();
		}
		loadedCol[0] = col;
	}

	@Override
	public int compare(Integer a, Integer b) {
		if (col == 1) {
			if (App.get().marcadores.selected[a] == App.get().marcadores.selected[b])
				return 0;
			else if (App.get().marcadores.selected[a] == true)
				return -1;
			else
				return 1;

		} else if (col == 2) {
			if (App.get().results.scores[a] == App.get().results.scores[b])
				return 0;
			else if (App.get().results.scores[a] > App.get().results.scores[b])
				return 1;
			else
				return -1;

		/*} else if (col == 3) {
			if (App.get().marcadores.read[a] == App.get().marcadores.read[b])
				return 0;
			else if (App.get().marcadores.read[a] == false)
				return 1;
			else
				return -1;
		*/
		} else if (col == 3) {
			return app.marcadores.getLabels(app.ids[a]).compareTo(app.marcadores.getLabels(app.ids[b]));
		
		} else {
			if (order[col] == null)
				loadOrder();
			return order[col][a] < order[col][b] ? -1 : (order[col][a] > order[col][b] ? 1 : 0);
		}

	}

}
