package dpf.sp.gpinf.indexer.search.maps;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import dpf.sp.gpinf.indexer.search.App;

public class MapaListener implements TableModelListener {

	MapaCanvas browser;
	
	public MapaListener(MapaCanvas browser){
		this.browser = browser;
	}

	@Override
	public void tableChanged(TableModelEvent e) {
		browser.setDesatualizado(true);
		/* somente chamado se o tab de mapas estiver sendo exibido */ 
	    if(App.get().resultTab.getSelectedIndex()==2){
	    	browser.redesenhaMapa(App.get());
	    }
	}

}
