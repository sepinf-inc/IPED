package dpf.mt.gpinf.mapas.impl;

import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.MapaModelUpdateListener;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;

public class AppMarkerCheckBoxListener implements MarkerCheckBoxListener {

	@Override
	public void onClicked(String mid, boolean checked) {
		//desabilita renderização automatica pela alteração no modelo por ser uma
		//alteração feita no próprio mapa; 
		MapaModelUpdateListener.desabilitaTemp = true;
		
        //procura pela posição correspondente na tabela do item clicado no mapa
		int pos = 0;
        MultiSearchResult results = App.get().getResults();
        for (int i = 0; i < results.getLength(); i++) {
            ItemId item = App.get().getResults().getItem(i);
            String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$
            if(gid.equals(mid)){
                pos = i;
                break;
            }
        }
        
        JTable t = App.get().getResultsTable();
		t.setValueAt(checked, t.convertRowIndexToView(pos), t.convertColumnIndexToView(1));
	}

}
