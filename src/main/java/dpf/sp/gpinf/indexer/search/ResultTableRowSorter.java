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

import java.awt.Dialog;
import java.util.List;

import javax.swing.table.TableRowSorter;

import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;

public class ResultTableRowSorter extends TableRowSorter<ResultTableSortModel> {
	
	ProgressDialog progressMonitor;

	public ResultTableRowSorter() {
		super(new ResultTableSortModel());
		this.setSortable(0, false);
		this.setMaxSortKeys(2);
		for (int i = 1; i < this.getModel().getColumnCount(); i++)
			this.setComparator(i, new RowComparator(i));
	}
	
	
	public void setSortKeys(final List<? extends SortKey> sortKeys) {
		
		//System.out.println("sorting");
		BackgroundSort backgroundSort = new BackgroundSort(sortKeys);
		
		progressMonitor = new ProgressDialog(App.get(), backgroundSort, true, 200, Dialog.ModalityType.MODELESS);
		progressMonitor.setNote("Ordenando...");
		
		backgroundSort.execute();
		
	}
	
	public void setSortKeysSuper(final List<? extends SortKey> sortKeys) {
		super.setSortKeys(sortKeys);
	}
	
	class BackgroundSort extends CancelableWorker{
		
		List<? extends SortKey> sortKeys;
		ResultTableRowSorter sorter = new ResultTableRowSorter();
		
		public BackgroundSort(List<? extends SortKey> sortKeys){
			this.sortKeys = sortKeys;
		}

		@Override
		protected Object doInBackground() throws Exception {
			//System.out.println("backgroundSort");
			sorter.setSortKeysSuper(sortKeys);
			//System.out.println("finished backgroundSort");
			return null;
		}
		
		@Override
		public void done(){
			progressMonitor.close();
			if(!this.isCancelled()){
				int idx = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();
				if(idx != -1)
					idx = App.get().resultsTable.convertRowIndexToModel(idx);
				
				App.get().resultsTable.setRowSorter(sorter);
				App.get().resultsTable.getTableHeader().repaint();
				
				if(idx != -1){
					idx = App.get().resultsTable.convertRowIndexToView(idx);
					App.get().resultsTable.setRowSelectionInterval(idx, idx);
				}
			}

		}
		
		@Override
		public boolean doCancel(boolean mayInterruptIfRunning) {
			cancel(true);
			return true;
		}
		
	}
	
}
