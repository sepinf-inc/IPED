package dpf.mt.gpinf.mapas.impl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import dpf.mt.gpinf.indexer.search.kml.KMLResult;
import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import iped3.IItemId;
import iped3.desktop.GUIProvider;
import iped3.search.IMultiSearchResultProvider;

/* 
 * Classe que controla a integração da classe App com a classe MapaCanvas
 */

public class AppMapaPanel extends JPanel {

	IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    MapaCanvasFactory mcf;

    AbstractMapaCanvas browserCanvas;
    boolean mapaDesatualizado = true; // variável para registrar se os dados a serem apresentados pelo mapa precisa
                                      // renderização
    KMLResult kmlResult;
    JTable resultsTable;
    boolean mapSrcSelected=false;
    ActionListener changeTileServer = null;
    
    String tilesSourceURL = null, savedTilesSourceURL = null;
	private MapaPanelConfig mpConfig;

    public AppMapaPanel(IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        this.setLayout(new BorderLayout());

        init();
    }

    public void init() {
    	mcf = new MapaCanvasFactory(this);
    	
        this.addMouseListener(new MouseListener() {
			@Override
			public void mouseReleased(MouseEvent e) {
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				redesenhaMapa();
			}
		});
        
        savedTilesSourceURL = JMapOptionsPane.getSavedTilesSourceURL();
        
		mpConfig = new MapaPanelConfig();

		try {
            ConfigurationManager.get().addObject(mpConfig);
            ConfigurationManager.get().loadConfigs();
		}catch(Exception e) {
			tilesSourceURL=null;
		}
        
        final Component self = this;
        changeTileServer= new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				StringBuffer url = new StringBuffer("");
				try {
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {
							url.append(JMapOptionsPane.showOptionsDialog(self));
						}
					});
				} catch (InvocationTargetException | InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if(url!=null) {
							tilesSourceURL=url.toString();
				        	config(tilesSourceURL);
				        	mapaDesatualizado=true;
				        	redesenhaMapa();
						}
					}
				});
			}
        };
    }
    
    public void config(String url) {
    	if(url==null) {
    	}else {
    		//remove o antigo browsercanvas, caso haja algum configurado
    		if(browserCanvas!=null) {
            	this.remove(browserCanvas.getContainer());
            	this.repaint();
        	}

    		browserCanvas = mcf.createMapCanvas(url);

            this.add(browserCanvas.getContainer(), BorderLayout.CENTER);
    	}
    }

    public void redesenhaMapa() {    	
    	if(tilesSourceURL==null) {
    		if(savedTilesSourceURL!=null) {
    			tilesSourceURL=savedTilesSourceURL;
    		}else {
        		try {
        			tilesSourceURL=mpConfig.getTileServerUrlPattern();
        		}catch(Exception e) {
        			tilesSourceURL=null;
        		}
    		}

        	if(tilesSourceURL==null) {
        		tilesSourceURL = JMapOptionsPane.showOptionsDialog(this);
        	}
        	config(tilesSourceURL);
    	}

        if (mapaDesatualizado && (resultsProvider.getResults().getLength() > 0)) {
            String kml = ""; //$NON-NLS-1$
            try {
                kmlResult = new KMLResult(resultsProvider, guiProvider);
                kml = kmlResult.getResultsKML();
                browserCanvas.setKML(kml);
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                mapaDesatualizado = false;
            }
        } else {
            browserCanvas.redesenha();
        }
    }

    public void redesenha() {
        browserCanvas.redesenha();
        mapaDesatualizado = false;
    }

    public boolean isMapaDesatualizado() {
        return mapaDesatualizado;
    }

    public void setMapaDesatualizado(boolean mapaDesatualizado) {
        this.mapaDesatualizado = mapaDesatualizado;
    }

    public void selecionaMarcador(IItemId item, boolean b) {

        if (kmlResult != null && kmlResult.getGPSItems().containsKey(item)) {
            List<Integer> subitems = kmlResult.getGPSItems().get(item);
            if (subitems == null) {
                String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
                browserCanvas.selecionaMarcador(gid, b);
            } else {
                for (Integer subitem : subitems) {
                    String gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                    browserCanvas.selecionaMarcador(gid, b);
                }
            }

        }
    }

    public IMultiSearchResultProvider getResultsProvider() {
        return resultsProvider;
    }
}
