package dpf.sp.gpinf.indexer.search.mapas;

import java.awt.Canvas;
import java.awt.Component;
import java.util.HashMap;

abstract public class AbstractMapaCanvas extends Canvas {
	MapSelectionListener mapSelectionListener = null;
	MarkerEventListener markerEventListener = null;
    MarkerCheckBoxListener markerCheckBoxListener = null;

    /* abstract methods*/
	abstract public void connect();
	abstract public void disconnect();
	abstract public void setText(final String html);
	abstract public void setKML(String kml);
	abstract public void redesenha();
	abstract public void addSaveKmlFunction(Runnable save);
	abstract public void enviaSelecoes(HashMap <String, Boolean> selecoes);
	abstract public boolean isConnected();
	abstract public Component getContainer();

	public MapSelectionListener getMapSelectionListener() {
		return mapSelectionListener;
	}
	public void setMapSelectionListener(MapSelectionListener mapSelectionListener) {
		this.mapSelectionListener = mapSelectionListener;
	}
	public MarkerEventListener getMarkerEventListener() {
		return markerEventListener;
	}
	public void setMarkerEventListener(MarkerEventListener markerEventListener) {
		this.markerEventListener = markerEventListener;
	}
	public MarkerCheckBoxListener getMarkerCheckBoxListener() {
		return markerCheckBoxListener;
	}
	public void setMarkerCheckBoxListener(MarkerCheckBoxListener markerCheckBoxListener) {
		this.markerCheckBoxListener = markerCheckBoxListener;
	}
}
