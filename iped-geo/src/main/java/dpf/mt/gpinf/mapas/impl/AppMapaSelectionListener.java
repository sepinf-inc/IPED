package dpf.mt.gpinf.mapas.impl;

import java.util.Arrays;
import java.util.HashSet;
import javax.swing.JTable;

import dpf.mt.gpinf.indexer.search.kml.GetResultsKMLWorker;
import dpf.mt.gpinf.mapas.MapSelectionListener;
import iped3.ItemId;
import iped3.util.BasicProps;

public class AppMapaSelectionListener implements MapSelectionListener {
    AppMapaPanel mapaPanel;

    public AppMapaSelectionListener(AppMapaPanel mapaPanel) {
        this.mapaPanel = mapaPanel;
    }

    @Override
    public void OnSelect(String[] mids) {

        JTable t = mapaPanel.getResultsProvider().getResultsTable();

        HashSet<String> columns = new HashSet<String>();
        columns.add(BasicProps.ID);

        for (int i = 0; i < mids.length; i++)
            // normalize items with multiple locations
            mids[i] = GetResultsKMLWorker.getBaseGID(mids[i]);

        Arrays.sort(mids);

        for (int i = 0; i < mapaPanel.getResultsProvider().getResults().getLength(); i++) {
            ItemId item = mapaPanel.getResultsProvider().getResults().getItem(i);
            String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$

            if (Arrays.binarySearch(mids, gid) >= 0) {
                int row = t.convertRowIndexToView(i);
                t.addRowSelectionInterval(row, row);
                // t.changeSelection(pos, 1, false, false);
            }
        }
    }

}
