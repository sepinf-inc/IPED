package dpf.mt.gpinf.mapas.impl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTable;

import dpf.mt.gpinf.indexer.search.kml.KMLResult;
import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.mt.gpinf.mapas.webkit.MapaCanvasWebkit;
import iped3.IItemId;
import iped3.desktop.GUIProvider;
import iped3.search.IMultiSearchResultProvider;

/* 
 * Classe que controla a integração da classe App com a classe MapaCanvas
 */

public class AppMapaPanel extends JPanel {
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    AbstractMapaCanvas browserCanvas;
    boolean mapaDesatualizado = true; // variável para registrar se os dados a serem apresentados pelo mapa precisa
                                      // renderização
    KMLResult kmlResult;
    JTable resultsTable;

    public AppMapaPanel(IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        this.setLayout(new BorderLayout());

        init();
    }

    public void init() {
        browserCanvas = new MapaCanvasWebkit();
        browserCanvas.addSaveKmlFunction(new Runnable() {
            public void run() {
                KMLResult kml = new KMLResult(resultsProvider, guiProvider);
                kml.saveKML();
            }
        });

        browserCanvas.setMapSelectionListener(new AppMapaSelectionListener(this));
        browserCanvas.setMarkerEventListener(new AppMapMarkerEventListener(this));
        browserCanvas.setMarkerCheckBoxListener(new AppMarkerCheckBoxListener(this));

        this.add(browserCanvas.getContainer(), BorderLayout.CENTER);
    }

    public void redesenhaMapa() {
        if (mapaDesatualizado && (resultsProvider.getResults().getLength() > 0)) {
            // se todo o modelo estiver desatualizado, gera novo KML e recarrega todo o mapa
            if (!browserCanvas.isConnected()) {
                this.setVisible(true);

                browserCanvas.connect();

                // força a rederização do Mapa (resolvendo o bug da primeira renderização
                for (Component c : getComponents()) {
                    c.repaint();
                }
                repaint();
            }

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
