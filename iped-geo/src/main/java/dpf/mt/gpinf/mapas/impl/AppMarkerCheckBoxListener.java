package dpf.mt.gpinf.mapas.impl;

import javax.swing.JTable;

import dpf.mt.gpinf.indexer.search.kml.GetResultsKMLWorker;
import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;
import iped3.ItemId;
import iped3.search.MultiSearchResult;

public class AppMarkerCheckBoxListener implements MarkerCheckBoxListener {
	AppMapaPanel mapaPanel;
	
	public AppMarkerCheckBoxListener(AppMapaPanel mapaPanel) {
		this.mapaPanel = mapaPanel;
	}

	@Override
	public void onClicked(String mid, boolean checked) {
		//desabilita renderização automatica pela alteração no modelo por ser uma
		//alteração feita no próprio mapa; 
		MapaViewer.desabilitaTemp = true;
		
        //procura pela posição correspondente na tabela do item clicado no mapa
		int pos = 0;
		mid = GetResultsKMLWorker.getBaseGID(mid);
        MultiSearchResult results = mapaPanel.getResultsProvider().getResults();
        for (int i = 0; i < results.getLength(); i++) {
            ItemId item = results.getItem(i);
            String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
            if(mid.equals(gid)){
                pos = i;
                break;
            }
        }
        
        JTable t = mapaPanel.getResultsProvider().getResultsTable();
		t.setValueAt(checked, t.convertRowIndexToView(pos), t.convertColumnIndexToView(1));
	}

}
