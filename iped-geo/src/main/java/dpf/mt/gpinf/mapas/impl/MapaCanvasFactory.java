package dpf.mt.gpinf.mapas.impl;

import dpf.mt.gpinf.indexer.search.kml.KMLResult;
import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.mt.gpinf.mapas.googlemaps.MapaCanvasWebkit;
import dpf.mt.gpinf.mapas.openstreet.MapaCanvasOpenStreet;

public class MapaCanvasFactory {

    MapaCanvasOpenStreet browserCanvasOS;
    AbstractMapaCanvas browserCanvasWK;
    AppMapaPanel appMapaPanel;

    public MapaCanvasFactory(AppMapaPanel appMapaPanel) {
        this.appMapaPanel = appMapaPanel;
    }

    public AbstractMapaCanvas createMapCanvas(String url) {
        AbstractMapaCanvas browserCanvas;
        boolean browserCanvasWasNull = false;

        if (url.contains("googlemaps")) {
            if (browserCanvasWK == null) {
                browserCanvasWasNull = true;
                browserCanvasWK = new MapaCanvasWebkit();
            }
            browserCanvas = browserCanvasWK;
        } else {
            if (browserCanvasOS == null) {
                browserCanvasWasNull = true;
                browserCanvasOS = new MapaCanvasOpenStreet();
            }
            browserCanvasOS.setUrl(url);
            browserCanvas = browserCanvasOS;
        }

        if (browserCanvasWasNull) {
            browserCanvas.addSaveKmlFunction(new Runnable() {
                public void run() {
                    KMLResult kml = new KMLResult(appMapaPanel.getResultsProvider(), appMapaPanel.guiProvider);
                    kml.saveKML();
                }
            });

            browserCanvas.setMapSelectionListener(new AppMapaSelectionListener(appMapaPanel));
            browserCanvas.setMarkerEventListener(new AppMapMarkerEventListener(appMapaPanel));
            browserCanvas.setMarkerCheckBoxListener(new AppMarkerCheckBoxListener(appMapaPanel));
            browserCanvas.setOnChangeTileServer(appMapaPanel.changeTileServer);
        }

        return browserCanvas;
    }

}
