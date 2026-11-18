package iped.geo.impl;

import javax.swing.JTable;

import iped.geo.MarkerCheckBoxListener;
import iped.geo.kml.GetResultsKMLWorker;

public class AppMarkerCheckBoxListener implements MarkerCheckBoxListener {
    AppMapPanel mapaPanel;

    public AppMarkerCheckBoxListener(AppMapPanel mapaPanel) {
        this.mapaPanel = mapaPanel;
    }

    @Override
    public void onClicked(String mid, boolean checked) {
        // desabilita renderização automatica pela alteração no modelo por ser uma
        // alteração feita no próprio mapa;
        MapViewer.desabilitaTemp = true;

        // procura pela posição correspondente na tabela do item clicado no mapa
        int pos = 0;
        mid = GetResultsKMLWorker.getBaseGID(mid);

        pos = mapaPanel.getItemPositioninResultsTable(mid);
        if (pos != -1) {
            JTable t = mapaPanel.getResultsProvider().getResultsTable();
            mapaPanel.disableLastMidReset();
            t.setValueAt(checked, pos, t.convertColumnIndexToView(1));
        }

    }

}
