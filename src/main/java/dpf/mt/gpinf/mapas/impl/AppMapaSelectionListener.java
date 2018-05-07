package dpf.mt.gpinf.mapas.impl;

import java.util.Arrays;
import java.util.HashSet;
import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MapSelectionListener;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemId;

public class AppMapaSelectionListener implements MapSelectionListener {

	@Override
	public void OnSelect(String[] mids) {
		
        JTable t = App.get().getResultsTable();
        
        HashSet<String> columns = new HashSet<String>();
        columns.add(IndexItem.ID);
        
        Arrays.sort(mids);

        for (int i = 0; i < App.get().getResults().getLength(); i++) {
			ItemId item = App.get().getResults().getItem(i);
			String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$
			
			if(Arrays.binarySearch(mids, gid) >= 0){
    	        int row = t.convertRowIndexToView(i);
    	        t.addRowSelectionInterval(row, row);
    	        //t.changeSelection(pos, 1, false, false);
			}
        }
	}

}
