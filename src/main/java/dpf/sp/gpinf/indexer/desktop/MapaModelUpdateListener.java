package dpf.sp.gpinf.indexer.desktop;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import dpf.sp.gpinf.indexer.search.ItemId;

/**
 * Monitora mudanças nos resultados das pesquisas para alterar o conteúdo
 * do mapa.
 *
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 */

public class MapaModelUpdateListener implements TableModelListener {

	App app;
	public static volatile boolean desabilitaTemp = false;
	public static volatile boolean updatingSelection = false;
	
	public MapaModelUpdateListener(App app){
		this.app = app;
	}

	@Override
	public void tableChanged(TableModelEvent e) {
		if(e.getColumn()==1){//se o evento foi disparado pelo check box que fica na coluna 1
		    MapaModelUpdateListener.updatingSelection = true;
			ItemId item = app.getResults().getItem(e.getFirstRow());
			String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
			
			Boolean b = (Boolean) app.resultsModel.getValueAt(e.getFirstRow(), e.getColumn());
			
			app.getBrowserPane().selecionaMarcador(gid, !b.booleanValue());			
		}
		
		/* Se a alteração foi feita no próprio mapa, ela não precisa ser refeita. */
		if(!desabilitaTemp){
			app.getBrowserPane().setMapaDesatualizado(true);

			/* somente chamado se o tab de mapas estiver sendo exibido */ 
		    if(app.mapTabDock != null && app.mapTabDock.isShowing()){
		        if(!updatingSelection)
		            app.getBrowserPane().redesenhaMapa();
		        else{
		            app.getBrowserPane().redesenha();
		        }
		        
		        updatingSelection = false;
		    }
		}else{
			//rehabilita renderização automatica pela alteração no modelo
			MapaModelUpdateListener.desabilitaTemp = false;
		}
	}

}
