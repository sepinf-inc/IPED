package dpf.mt.gpinf.mapas;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import dpf.mt.gpinf.mapas.util.Messages;

abstract public class AbstractMapaCanvas extends Canvas {
    MapSelectionListener mapSelectionListener = null;
    MarkerEventListener markerEventListener = null;
    MarkerCheckBoxListener markerCheckBoxListener = null;

    ActionListener onChangeTileServer = null;

    protected HashMap<String, Boolean> selecoesAfazer;
    protected Runnable saveRunnable;

    /* abstract methods */
    abstract public void connect();

    abstract public void disconnect();

    abstract public void setText(final String html);

    abstract public void setKML(String kml);

    abstract public void redesenha();

    abstract public void addSaveKmlFunction(Runnable save);

    abstract public boolean isConnected();

    abstract public Component getContainer();

    abstract public void selecionaMarcador(String mid, boolean b);

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

    public Runnable getSaveRunnable() {
        return saveRunnable;
    }

    public void enviaSelecoes(final HashMap<String, Boolean> selecoes) {
        if (this.selecoesAfazer == null) {
            this.selecoesAfazer = new HashMap<String, Boolean>();
        }

        String[] marks = new String[selecoes.keySet().size()];
        marks = selecoes.keySet().toArray(marks);
        for (int i = 0; i < marks.length; i++) {
            this.selecoesAfazer.put(marks[i], selecoes.get(marks[i]));
        }
    }

    public void setOnChangeTileServer(ActionListener actionListener) {
    	this.onChangeTileServer = actionListener;
    }

    public void fireChangeTileServer() {
    	if(this.onChangeTileServer!=null) {
    		this.onChangeTileServer.actionPerformed(new ActionEvent(this,1,"changeTileServer"));
    	}
    }

    public String replaceLocalizedMarks(String src, String prefix) throws IOException {
    	StringBuffer html = new StringBuffer(src);
    	
    	Set<String> keys=Messages.getKeys();
    	for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			if(key.startsWith(prefix)) {
				int i = html.indexOf("{{"+key+"}}");
				html.replace(i, i+key.length()+4, Messages.getString(key));
			}
		}
    	
    	return html.toString();
    }

    public String getToolBarHtml() throws IOException {
    	return replaceLocalizedMarks(IOUtils.toString(AbstractMapaCanvas.class.getResourceAsStream("toolbar.html"), "UTF-8"), "toolbar");
    }

}
