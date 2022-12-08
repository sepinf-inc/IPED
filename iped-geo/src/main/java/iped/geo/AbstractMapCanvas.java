package iped.geo;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import iped.geo.localization.Messages;

abstract public class AbstractMapCanvas extends Canvas {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    MapSelectionListener mapSelectionListener = null;
    MarkerEventListener markerEventListener = null;
    MarkerCheckBoxListener markerCheckBoxListener = null;

    ActionListener onChangeTileServer = null;

    protected ArrayList<Runnable> onLoadRunnables = new ArrayList<Runnable>();

    protected HashMap<String, Boolean> selectionMapToApply;
    protected String leadSelectionToApply;
    protected Runnable saveRunnable;

    /* abstract methods */
    abstract public void connect();

    abstract public void disconnect();

    abstract public void setText(final String html);

    abstract public void setKML(String kml);

    abstract public void update();

    abstract public void addSaveKmlFunction(Runnable save);

    abstract public boolean isConnected();

    abstract public Component getContainer();

    abstract public void selectCheckbox(String mid, boolean b);

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

    public void sendSelection(final HashMap<String, Boolean> selectionMap) {
        if (this.selectionMapToApply == null) {
            this.selectionMapToApply = new HashMap<String, Boolean>();
        }

        String[] marks = new String[selectionMap.keySet().size()];
        marks = selectionMap.keySet().toArray(marks);
        for (int i = 0; i < marks.length; i++) {
            this.selectionMapToApply.put(marks[i], selectionMap.get(marks[i]));
        }
    }

    public void setOnChangeTileServer(ActionListener actionListener) {
        this.onChangeTileServer = actionListener;
    }

    public void fireChangeTileServer() {
        if (this.onChangeTileServer != null) {
            this.onChangeTileServer.actionPerformed(new ActionEvent(this, 1, "changeTileServer"));
        }
    }

    public String replaceLocalizedMarks(String src, String prefix) throws IOException {
        StringBuffer html = new StringBuffer(src);

        Set<String> keys = Messages.getKeys();
        for (Iterator<String> iterator = keys.iterator(); iterator.hasNext();) {
            String key = iterator.next();
            if (key.startsWith(prefix)) {
                int i = html.indexOf("{{" + key + "}}");
                html.replace(i, i + key.length() + 4, Messages.getString(key));
            }
        }

        return html.toString();
    }

    public String getToolBarHtml() throws IOException {
        return replaceLocalizedMarks(
                IOUtils.toString(AbstractMapCanvas.class.getResourceAsStream("toolbar.html"), "UTF-8"), "toolbar");
    }

    public void sendLeadSelection(String gid) {
        leadSelectionToApply = gid;
    }

    public String getLeadSelectionToApply() {
        return leadSelectionToApply;
    }

    public void setLeadSelectionToApply(String leadSelectionToApply) {
        this.leadSelectionToApply = leadSelectionToApply;
    }

    public void runAfterLoad(Runnable run) {
        onLoadRunnables.add(run);
    }

    public void load() {
    }

    public void viewAll() {
    }

    public void viewAll(double minlongit, double minlat, double maxlongit, double maxlat) {
    }

    public void refreshMap() {
        
    }

    public abstract void addPlacemark(String gid, String htmlFormat, String string, String longit, String lat,
            boolean checked, boolean selected);

}
