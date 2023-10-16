package iped.geo.impl;

import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.JTable;

import iped.data.IItemId;
import iped.geo.MarkerEventListener;
import iped.geo.kml.GetResultsKMLWorker;

public class AppMapMarkerEventListener implements MarkerEventListener {
    AppMapPanel mapaPanel;
    MapViewer mapViewer;

    public AppMapMarkerEventListener(AppMapPanel mapaPanel) {
        this.mapaPanel = mapaPanel;
        mapViewer = mapaPanel.getMapViewer();
    }

    @Override
    public void onClicked(String mid, MouseEvent e) {
        // procura pela posição correspondente na tabela do item clicado no mapa
        mid = GetResultsKMLWorker.getBaseGID(mid);
        JTable t = mapaPanel.getResultsProvider().getResultsTable();
        int pos = mapaPanel.getItemPositioninResultsTable(mid);
        boolean olddesabilitaTemp = mapViewer.desabilitaTemp;
        mapViewer.desabilitaTemp = true;
        if (e != null) {
            if (!((e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK)) {
                AppMapMarkerEventListener.doTableSelection(t, pos, e.isShiftDown());
            }
        }

        IItemId[] siblings = mapaPanel.getTrackSiblings();
        if (siblings != null && siblings.length > 0) {
            StringBuffer gids = new StringBuffer();
            gids.append("[");
            for (int i = 0; i < siblings.length; i++) {
                IItemId item = siblings[i];
                gids.append("'");
                gids.append(item.getSourceId());
                gids.append("_");
                gids.append(item.getId());
                gids.append("'");
                gids.append(",");
            }
            gids.append("]");
            ArrayList<StringBuffer> gidsList = new ArrayList<StringBuffer>();
            gidsList.add(gids);
            mapaPanel.browserCanvas.drawPolyline(gidsList);
        }
        String jsonFeature = mapaPanel.getSelectedJSONFeature();
        if (jsonFeature != null) {
            mapaPanel.browserCanvas.drawJSONFeature(jsonFeature);
        }

        mapViewer.desabilitaTemp = olddesabilitaTemp;
    }

    static public void doTableSelection(JTable t, int pos, boolean additiveSelection) {
        if (additiveSelection) {
            if (t.isRowSelected(pos)) {
                t.removeRowSelectionInterval(pos, pos);
            } else {
                t.addRowSelectionInterval(pos, pos);
            }
        } else {
            boolean wasSelected = t.isRowSelected(pos);
            t.getSelectionModel().setValueIsAdjusting(true);
            t.removeRowSelectionInterval(0, t.getRowCount() - 1);
            if (!wasSelected) {
                t.setRowSelectionInterval(pos, pos);
            }
            t.getSelectionModel().setValueIsAdjusting(false);
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
