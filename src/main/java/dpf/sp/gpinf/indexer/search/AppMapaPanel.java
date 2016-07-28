package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import dpf.sp.gpinf.indexer.search.kml.KMLResult;
import dpf.sp.gpinf.indexer.search.mapas.MapaCanvas;

/* 
 * Classe que controla a integração da classe App com a classe MapaCanvas
 */

public class AppMapaPanel extends JPanel {
	MapaCanvas browserCanvas;
	final App app;
    boolean mapaDesatualizado = true; //variável para registrar se os dados a serem apresentados pelo mapa precisa renderização 
	
	public AppMapaPanel(App app){
		this.app = app;
	    this.setLayout(new BorderLayout());
	    
	    init();
	    this.add(browserCanvas, BorderLayout.CENTER);
	}
	
	public void init(){
	    browserCanvas = new MapaCanvas();
	    browserCanvas.addSaveKmlFunction(new Runnable() {
			public void run() {
				KMLResult.saveKML();
			}
		});
	    browserCanvas.setMapSelectionListener(new AppMapaSelectionListener());
	    browserCanvas.setMarkerEventListener(new AppMapMarkerEventListener());
	    browserCanvas.setMarkerCheckBoxListener(new AppMarkerCheckBoxListener());

	    //Adiciona listener para indicar a seleção de item ao Mapa
	    app.getResultsTable().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if((app.getResultTab().getSelectedIndex()!=2)&&(!mapaDesatualizado)){
					ListSelectionModel lsm = (ListSelectionModel) e.getSource();
					HashMap <String, Boolean> selecoes = new HashMap <String, Boolean>(); 
					for(int i=e.getFirstIndex(); i<=e.getLastIndex(); i++){
						boolean selected = lsm.isSelectedIndex(i);
						//int im = resultsTable.convertColumnIndexToModel(i);

			        	org.apache.lucene.document.Document doc = null;
			        	try {
							doc = App.get().searcher.doc(app.getResults().docs[i]);
				        	selecoes.put(doc.get("id"), selected);
						} catch (IOException ex) {
							ex.printStackTrace();
							break;
						}
					}
					browserCanvas.enviaSelecoes(selecoes);
				}
			}
		});

	    // provoca a atualização do mapa na mudança de tabs
	    app.getResultTab().addChangeListener(new ChangeListener() {
	    	@Override
			public void stateChanged(ChangeEvent e) {
				if(app.getResultTab().getSelectedIndex()==2){
					redesenhaMapa();
				}
			}		
		});
	
	}

	public void redesenhaMapa(){
		    if(mapaDesatualizado){
		    	//se todo o modelo estiver desatualizado, gera novo KML e recarrega todo o mapa
				if(!browserCanvas.isConnected()){
					this.setVisible(true);
					browserCanvas.connect();
					//força a rederização do Mapa (resolvendo o bug da primeira renderização 
					app.treeSplitPane.setDividerLocation(app.treeSplitPane.getDividerLocation()-1);
				}
				
			    String kml = "";
			    try {
			    	kml = KMLResult.getResultsKML(app);
			    	browserCanvas.setKML(kml);
				} catch (IOException e1) {
					e1.printStackTrace();
				}finally {
					mapaDesatualizado = false;
				}
			}else{
				browserCanvas.redesenha();
			}
	  }

	public boolean isMapaDesatualizado() {
		return mapaDesatualizado;
	}

	public void setMapaDesatualizado(boolean mapaDesatualizado) {
		this.mapaDesatualizado = mapaDesatualizado;
	}

}
