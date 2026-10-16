package iped.geo;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import iped.geo.localization.Messages;

abstract public class AbstractMapCanvas extends Canvas {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    protected static final String ALLMARKERS_TAG = "allmarkers";
    MapSelectionListener mapSelectionListener = null;
    protected MarkerEventListener markerEventListener = null;
    protected MarkerCheckBoxListener markerCheckBoxListener = null;
    protected String tourOrder;
    protected boolean loaded = false;

    ActionListener onChangeTileServer = null;

    protected ArrayList<Runnable> onLoadRunnables = new ArrayList<Runnable>();

    protected HashMap<String, Boolean> checkMapToApply;
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

    public void clearSelection() {
        this.selectionMapToApply = new HashMap<String, Boolean>();
        this.selectionMapToApply.put(AbstractMapCanvas.ALLMARKERS_TAG, false);
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

    public void clearCheck() {
        this.checkMapToApply = new HashMap<String, Boolean>();
        this.checkMapToApply.put(AbstractMapCanvas.ALLMARKERS_TAG, false);
    }

    public void sendCheck(final HashMap<String, Boolean> checkedMap) {
        if (this.checkMapToApply == null) {
            this.checkMapToApply = new HashMap<String, Boolean>();
        }

        String[] marks = new String[checkedMap.keySet().size()];
        marks = checkedMap.keySet().toArray(marks);
        for (int i = 0; i < marks.length; i++) {
            this.checkMapToApply.put(marks[i], checkedMap.get(marks[i]));
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
        return replaceLocalizedMarks(IOUtils.toString(AbstractMapCanvas.class.getResourceAsStream("toolbar.html"), "UTF-8"), "toolbar");
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

    public void executeOnLoadRunnables() {
        if (onLoadRunnables.size() > 0) {
            for (Iterator iterator = onLoadRunnables.iterator(); iterator.hasNext();) {
                Runnable runnable = (Runnable) iterator.next();
                runnable.run();
            }
            onLoadRunnables.clear();
        }
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

    public String getTourOrder() {
        return tourOrder;
    }

    public void setTourOrder(String tourOrder) {
        this.tourOrder = tourOrder;
    }

    /*
     * returns true if main html needs to be reloaded to finish the tile server
     * update
     */
    public boolean setTileServerUrl(String url) {
        return false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void updateView(List<StringBuffer> gidsList) {
        // TODO Auto-generated method stub

    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * Creates the placemarks on the loaded map. The creation is done in batches,
     * one javascript call per list entry.
     * <p>
     *
     * @param gidsList
     *            a list of List<String> object with the placemarks informations.
     */
    public abstract void createPlacemarks(List<StringBuffer> gidsList);

    public void drawPolyline(List<StringBuffer> gids) {
        // TODO Auto-generated method stub

    }

    public abstract void drawJSONFeature(String mid, String jsonFeature);

    public void drawJSONFeatures(String[] jsonFeatures) {
        // TODO Auto-generated method stub

    }

}
