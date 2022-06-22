package iped.geo.impl;

import iped.geo.AbstractMapCanvas;
import iped.geo.googlemaps.MapCanvasWebkit;
import iped.geo.kml.KMLResult;
import iped.geo.openstreet.MapCanvasOpenStreet;

public class MapCanvasFactory {

    MapCanvasOpenStreet browserCanvasOS;
    AbstractMapCanvas browserCanvasWK;
    AppMapPanel appMapaPanel;

    public MapCanvasFactory(AppMapPanel appMapaPanel) {
        this.appMapaPanel = appMapaPanel;
    }

    public AbstractMapCanvas createMapCanvas(String url) {
        AbstractMapCanvas browserCanvas;
        boolean browserCanvasWasNull = false;

        if (url.contains("googlemaps")) {
            if (browserCanvasWK == null) {
                browserCanvasWasNull = true;
                browserCanvasWK = new MapCanvasWebkit();
            }
            browserCanvas = browserCanvasWK;
        } else {
            if (browserCanvasOS == null) {
                browserCanvasWasNull = true;
                browserCanvasOS = new MapCanvasOpenStreet();
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

            browserCanvas.setMapSelectionListener(new AppMapSelectionListener(appMapaPanel));
            browserCanvas.setMarkerEventListener(new AppMapMarkerEventListener(appMapaPanel));
            browserCanvas.setMarkerCheckBoxListener(new AppMarkerCheckBoxListener(appMapaPanel));
            browserCanvas.setOnChangeTileServer(appMapaPanel.changeTileServer);
        }

        return browserCanvas;
    }

}
