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

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;

import dpf.sp.gpinf.indexer.util.Util;

public class RowComparator implements Comparator<Integer> {

	static String[] fields = ResultTableModel.fields;
	
	private int col;
	private boolean sortFileExists;
	private String sortFilePath;
	private boolean isNum = true;
	
	static private int[][] order = new int[fields.length + ResultTableModel.fixedCols.length][];
	static private int[] loadedCol = { 0, 0 };
	
	private static AtomicReader atomicReader;
	
	private App app = App.get();
	
	private SortedDocValues sdv;
	private NumericDocValues ndv;

	public RowComparator(int col) {
		this.col = col;
		if(col >= ResultTableModel.fixedCols.length){
			fields = ResultTableModel.fields;
			sortFilePath = App.get().codePath + "/../data/" + fields[col - ResultTableModel.fixedCols.length] + ".sort";
			sortFileExists = new File(sortFilePath).exists();
			try {
				if(atomicReader == null)
					atomicReader = SlowCompositeReaderWrapper.wrap(app.reader); 
				sdv = atomicReader.getSortedDocValues(fields[col - ResultTableModel.fixedCols.length]);
				if(sdv == null)
					ndv = atomicReader.getNumericDocValues(fields[col - ResultTableModel.fixedCols.length]);
					 
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void dispose() {
		//order[loadedCol[0]] = null;
		//order[loadedCol[1]] = null;
	}

	private void loadOrder() {
		order[loadedCol[1]] = null;
		loadedCol[1] = loadedCol[0];
		try {
			order[col] = (int[]) Util.readObject(sortFilePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
		loadedCol[0] = col;
	}

	@Override
	public int compare(Integer a, Integer b) {
		
		if(Thread.currentThread().isInterrupted())
			throw new RuntimeException("Ordenação cancelada.");
		
		switch(col){
			case 1:
				if (app.marcadores.selected[app.ids[a]] == app.marcadores.selected[app.ids[b]])
					return 0;
				else if (app.marcadores.selected[app.ids[a]] == true)
					return -1;
				else
					return 1;
			case 2:
				return (int)(app.results.scores[a] - app.results.scores[b]);
				
			case 3:
				return app.marcadores.getLabels(app.ids[a]).compareTo(app.marcadores.getLabels(app.ids[b]));
				
			default:
				if(sortFileExists){
					if (order[col] == null)
						loadOrder();
					return order[col][app.ids[a]] - order[col][app.ids[b]];
					
				}else if(sdv != null){
					return sdv.getOrd(a) - sdv.getOrd(b);
					
				}else if(ndv != null){
					return Long.compare(ndv.get(a), ndv.get(b));
					
				}else
					return 0;
				
		}
	}

}
