package dpf.sp.gpinf.indexer.desktop;

import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;

public class AppMarkerCheckBoxListener implements MarkerCheckBoxListener {

	@Override
	public void onClicked(String mid, boolean checked) {
		//desabilita renderização automatica pela alteração no modelo por ser uma
		//alteração feita no próprio mapa; 
		MapaModelUpdateListener.desabilitaTemp = true;
		
		App app = App.get();
		app.getResultsTable().setValueAt(checked, App.get().getResultsTable().getSelectedRow(), 1);
	}

}
