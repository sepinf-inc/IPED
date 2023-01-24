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

import java.awt.Dialog;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.RowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.parallelsorter.ParallelTableRowSorter;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.events.RowSorterTableDataChange;
import iped.viewers.util.ProgressDialog;

public class ResultTableRowSorter extends ParallelTableRowSorter<ResultTableSortModel> {
    
    private static final int MAX_COMPARATOR_CACHE = 3;
    
    private static Logger logger = LoggerFactory.getLogger(ResultTableRowSorter.class);

    private static volatile Map<Integer, RowComparator> comparatorCache = new LinkedHashMap<Integer, RowComparator>(16, 0.75f, true){
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
        protected boolean removeEldestEntry(Map.Entry<Integer, RowComparator> eldest) {
            return this.size() > MAX_COMPARATOR_CACHE;
        }
    };

    public ResultTableRowSorter() {
        super(new ResultTableSortModel());
        this.setSortable(0, false);
        this.setMaxSortKeys(2);
    }

    @Override
    public Comparator<?> getComparator(int column) {
        if (RowComparator.isNewIndexReader())
            comparatorCache.clear();
        RowComparator comp = comparatorCache.get(column);
        if (comp == null) {
            comp = new RowComparator(column);
            comparatorCache.put(column, comp);
        }
        return comp;
    }

    @Override
    protected boolean useToString(int column) {
        return false;
    }

    @Override
    public void setSortKeys(final List<? extends SortKey> sortKeys) {
        List<? extends SortKey> oldSortKeys = super.getSortKeys();
        if ((oldSortKeys == sortKeys) || (oldSortKeys.size() == 0 && sortKeys == null)) {
            return;
        }
        if (sortKeys == null) {
            super.setSortKeys(null);
            App.get().resultsModel.fireTableChanged(new RowSorterTableDataChange(App.get().resultsModel));
        } else {
            BackgroundSort backgroundSort = new BackgroundSort(sortKeys);
            backgroundSort.execute();
        }
    }

    public void setSortKeysSuper(final List<? extends SortKey> sortKeys) {
        super.setSortKeys(sortKeys);
    }

    class BackgroundSort extends CancelableWorker {

        ProgressDialog progressMonitor;
        List<? extends SortKey> sortKeys;
        ResultTableRowSorter sorter = new ResultTableRowSorter();

        public BackgroundSort(List<? extends SortKey> sortKeys) {
            this.sortKeys = sortKeys;
            progressMonitor = new ProgressDialog(App.get(), this, true, 200, Dialog.ModalityType.APPLICATION_MODAL);
            progressMonitor.setNote(Messages.getString("ResultTableRowSorter.Sorting")); //$NON-NLS-1$
        }

        @Override
        protected Object doInBackground() {
            List<String> sortKeysString = getSortKeysString(sortKeys);
            logger.info("Sorting by {}...", sortKeysString);
            long t = System.currentTimeMillis();
            sorter.setSortKeysSuper(sortKeys);
            t = System.currentTimeMillis() - t;
            logger.info("Sorting by {} took {}ms", sortKeysString, t);
            return null;
        }
        
        private List<String> getSortKeysString(List<? extends SortKey> list) {
            return list.stream().map(s -> "Col " + s.getColumn() + " order " + s.getSortOrder()).collect(Collectors.toList());
        }

        @Override
        public void done() {
            progressMonitor.close();
            int idx = App.get().resultsTable.getSelectionModel().getLeadSelectionIndex();
            if (idx != -1) {
                idx = App.get().resultsTable.convertRowIndexToModel(idx);
            }

            RowSorter oldSorter = App.get().resultsTable.getRowSorter();
            App.get().resultsTable.setRowSorter(null);

            if (!this.isCancelled()) {
                App.get().resultsTable.setRowSorter(sorter);
                App.get().resultsModel.fireTableChanged(new RowSorterTableDataChange(App.get().resultsModel, sortKeys));
            } else {
                App.get().resultsTable.setRowSorter(oldSorter);
            }

            App.get().resultsTable.getTableHeader().repaint();
            App.get().galleryModel.fireTableStructureChanged();

            if (idx != -1) {
                idx = App.get().resultsTable.convertRowIndexToView(idx);
                App.get().resultsTable.setRowSelectionInterval(idx, idx);
            }

        }

        @Override
        public boolean doCancel(boolean mayInterruptIfRunning) {
            cancel(true);
            return true;
        }

    }

}
