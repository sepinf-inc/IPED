package dpf.sp.gpinf.indexer.desktop;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * Monitora mudanças nos resultados das pesquisas para alterar o conteúdo
 * do mapa.
 *
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 */

public class MapaModelUpdateListener implements TableModelListener {

	App app;
	public static boolean desabilitaTemp=false;
	
	public MapaModelUpdateListener(App app){
		this.app = app;
	}

	@Override
	public void tableChanged(TableModelEvent e) {
		if(!desabilitaTemp){
			app.getBrowserPane().setMapaDesatualizado(true);

			/* somente chamado se o tab de mapas estiver sendo exibido */ 
		    if(app.mapTabDock.isShowing()){
		    	app.getBrowserPane().redesenhaMapa();
		    }
		}else{
			//rehabilita renderização automatica pela alteração no modelo
			MapaModelUpdateListener.desabilitaTemp = false;
		}
	}

}
