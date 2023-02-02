package iped.geo.impl;

import iped.geo.AbstractMapCanvas;
import iped.geo.kml.KMLResult;
import iped.geo.openstreet.MapCanvasOpenStreet;

public class MapCanvasFactory {

    MapCanvasOpenStreet browserCanvasOS;
    AppMapPanel appMapaPanel;

    public MapCanvasFactory(AppMapPanel appMapaPanel) {
        this.appMapaPanel = appMapaPanel;
    }

    public AbstractMapCanvas createMapCanvas(String url) {
        AbstractMapCanvas browserCanvas;
        boolean browserCanvasWasNull = false;

        if (browserCanvasOS == null) {
            browserCanvasWasNull = true;
            browserCanvasOS = new MapCanvasOpenStreet();
        }
        browserCanvasOS.setUrl(url);
        browserCanvas = browserCanvasOS;

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
