package iped.geo.impl;

import java.awt.event.MouseEvent;

import javax.swing.JTable;

import iped.data.IItemId;
import iped.geo.MarkerEventListener;
import iped.geo.kml.GetResultsKMLWorker;
import iped.search.IMultiSearchResult;

public class AppMapMarkerEventListener implements MarkerEventListener {
    AppMapPanel mapaPanel;

    public AppMapMarkerEventListener(AppMapPanel mapaPanel) {
        this.mapaPanel = mapaPanel;
    }

    @Override
    public void onClicked(String mid, MouseEvent e) {
        int pos = 0;

        // procura pela posição correspondente na tabela do item clicado no mapa
        mid = GetResultsKMLWorker.getBaseGID(mid);
        IMultiSearchResult results = mapaPanel.getResultsProvider().getResults();
        for (int i = 0; i < results.getLength(); i++) {
            IItemId item = results.getItem(i);
            String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
            if (mid.equals(gid)) {
                pos = i;
                break;
            }
        }

        JTable t = mapaPanel.getResultsProvider().getResultsTable();
        pos = t.convertRowIndexToView(pos);
        if (e.isShiftDown()) {
            if (t.isRowSelected(pos)) {
                t.removeRowSelectionInterval(pos, pos);
            } else {
                t.addRowSelectionInterval(pos, pos);
            }
        } else {
            t.setRowSelectionInterval(pos, pos);
        }
    }

    @Override
    public void onMouseEntered(String mid, MouseEvent e) {
    }

    @Override
    public void onMouseExited(String mid, MouseEvent e) {
    }

    @Override
    public void onMousePressed(String mid, MouseEvent e) {
    }

    @Override
    public void onMouseReleased(String mid, MouseEvent e) {
    }

}
