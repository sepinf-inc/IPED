package iped.geo.impl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.lucene.document.Document;
import org.roaringbitmap.RoaringBitmap;

import iped.data.IItemId;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.ItemId;
import iped.engine.task.index.IndexItem;
import iped.geo.AbstractMapCanvas;
import iped.geo.js.GetResultsJSWorker;
import iped.geo.kml.KMLResult;
import iped.geo.localization.Messages;
import iped.geo.parsers.GeofileParser;
import iped.properties.BasicProps;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;

/* 
 * Classe que controla a integração da classe App com a classe MapaCanvas
 */

public class AppMapPanel extends JPanel implements Consumer<Object[]> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    MapCanvasFactory mcf;
    MapViewer mapViewer;

    AbstractMapCanvas browserCanvas;
    boolean mapaDesatualizado = true; // variável para registrar se os dados a serem apresentados pelo mapa precisa
    // renderização
    KMLResult kmlResult;
    JTable resultsTable;
    boolean mapSrcSelected = false;
    ActionListener changeTileServer = null;

    String tilesSourceURL = null, savedTilesSourceURL = null;
    private MapPanelConfig mpConfig;

    private JProgressBar gpsProgressBar;

    /*
     * SwingWorker that prepares the result with georeferenced items that will be
     * sent to the map
     */
    private GetResultsJSWorker jsWorker;
    private PropertyChangeListener lastPropertyChangeListener;

    static final String[] fieldNames = { "GEOMETRIC_SIMPLE", "GEOMETRIC_COMPLEX", "GEOMETRIC_MULTIPOLYGON", "GEOJSON" };

    public enum MapLoadState {
        NOTLOADED, LOADING, LOADED
    };

    MapLoadState loadState = MapLoadState.NOTLOADED;
    private GetResultsJSWorker mapLoadWorker;
    private RoaringBitmap[] geoReferencedBitmap;

    public AppMapPanel(IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsProvider = resultsProvider;
        this.resultsTable = resultsProvider.getResultsTable();
        this.guiProvider = guiProvider;
        this.setLayout(new BorderLayout());

        init();
    }

    public void init() {
        gpsProgressBar = new JProgressBar();
        gpsProgressBar.setOpaque(true);
        gpsProgressBar.setStringPainted(true);
        gpsProgressBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.gray, 1), BorderFactory.createEmptyBorder(4, 0, 4, 0)));

        mcf = new MapCanvasFactory(this);

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
                updateMap();
            }
        });

        savedTilesSourceURL = JMapOptionsPane.getSavedTilesSourceURL();

        mpConfig = new MapPanelConfig();

        try {
            ConfigurationManager.get().addObject(mpConfig);
            ConfigurationManager.get().loadConfigs();
        } catch (Exception e) {
            tilesSourceURL = null;
        }

        final Component self = this;
        changeTileServer = new ActionListener() {
            private PropertyChangeListener lastTileServerChange;

            public void actionPerformed(ActionEvent e) {
                final String leadSelectionToApply = browserCanvas.getLeadSelectionToApply();
                StringBuffer url = new StringBuffer("");
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            String result = JMapOptionsPane.showOptionsDialog(self, tilesSourceURL);
                            if (result != null) {
                                url.append(result);
                            }
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                Runnable tileServerChange = new Runnable() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                if (url.length() > 0) {
                                    tilesSourceURL = url.toString();
                                    if (config(tilesSourceURL)) {
                                        mapaDesatualizado = true;

                                        /*
                                         * Sends the current lead selection to the next map rendered to select it after
                                         * load.
                                         */
                                        runAfterLoad(new Runnable() {
                                            @Override
                                            public void run() {
                                                browserCanvas.sendLeadSelection(leadSelectionToApply);
                                                browserCanvas.update();
                                            }
                                        });

                                        updateMap();
                                    }
                                }
                            }
                        });
                    }
                };
                if (loadState != MapLoadState.LOADED) {
                    if (lastTileServerChange != null) {
                        mapLoadWorker.removePropertyChangeListener(lastTileServerChange);
                    }
                    lastTileServerChange = new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            tileServerChange.run();
                        }
                    };
                    mapLoadWorker.addPropertyChangeListener(lastTileServerChange);
                } else {
                    tileServerChange.run();
                }
            }
        };
        browserCanvas = mcf.createMapCanvas(savedTilesSourceURL);
    }

    public boolean config(String url) {
        if (url == null) {
            return false;
        } else {
            if (browserCanvas == null) {
                browserCanvas = mcf.createMapCanvas(url);
                return false;
            } else {
                return browserCanvas.setTileServerUrl(url);
            }
        }
    }

    synchronized public void updateMap() {
        if (tilesSourceURL == null) {
            if (savedTilesSourceURL != null) {
                tilesSourceURL = savedTilesSourceURL;
            } else {
                try {
                    tilesSourceURL = mpConfig.getTileServerUrlPattern();
                } catch (Exception e) {
                    tilesSourceURL = null;
                }
            }

            if (tilesSourceURL == null) {
                tilesSourceURL = JMapOptionsPane.showOptionsDialog(this);
            }
            config(tilesSourceURL);
            mapaDesatualizado = true;
            gpsProgressBar.setVisible(false);
            this.add(gpsProgressBar, BorderLayout.NORTH);
            this.add(browserCanvas.getContainer(), BorderLayout.CENTER);
        }

        if (mapaDesatualizado) {
            if (jsWorker != null) {
                try {
                    jsWorker.cancel(true);
                } catch (CancellationException e) {
                    // ignores
                }
            }

            mapaDesatualizado = false;
            this.kmlResult = null;

            gpsProgressBar.setString(Messages.getString("KMLResult.LoadingGPSData") + "..."); //$NON-NLS-1$ //$NON-NLS-2$
            gpsProgressBar.setValue(0);
            gpsProgressBar.setVisible(true);

            String[] cols = new String[] { BasicProps.ID };

            if (loadState == MapLoadState.NOTLOADED) {
                loadState = MapLoadState.LOADING;
                mapLoadWorker = new GetResultsJSWorker(resultsProvider, cols, gpsProgressBar, browserCanvas, this);

                mapLoadWorker.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("state".equals(evt.getPropertyName())
                                && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) {
                            loadState = MapLoadState.LOADED;

                            syncSelectedItems();
                            mapViewer.applyCheckedItems();
                            browserCanvas.update();
                        }
                    }
                });

                mapLoadWorker.execute();
            } else if (loadState == MapLoadState.LOADING) {
                if (lastPropertyChangeListener != null) {
                    // cancels prior map update in case it hasn't finished yet
                    mapLoadWorker.removePropertyChangeListener(lastPropertyChangeListener);
                    jsWorker.cancel(true);
                }
                AppMapPanel self = this;
                // enqueue the map update to run after map load worker ends.
                lastPropertyChangeListener = new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("state".equals(evt.getPropertyName()) && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) {
                            jsWorker = new GetResultsJSWorker(resultsProvider, cols, gpsProgressBar, browserCanvas, self);
                            jsWorker.execute();
                        }
                    }
                };
                mapLoadWorker.addPropertyChangeListener(lastPropertyChangeListener);
            } else if (loadState == MapLoadState.LOADED) {
                jsWorker = new GetResultsJSWorker(resultsProvider, cols, gpsProgressBar, browserCanvas, this);
                jsWorker.execute();
            }
        } else {
            browserCanvas.update();
        }
    }

    private void syncSelectedItems() {
        int[] selected = resultsTable.getSelectedRows();
        IMultiSearchResult results = resultsProvider.getResults();
        HashMap<String, Boolean> selecoes = new HashMap<String, Boolean>();
        for (int i = 0; i < selected.length; i++) {
            int rowModel = resultsTable.convertRowIndexToModel(selected[i]);
            IItemId item = results.getItem(rowModel);
            addSelection(selecoes, item);
        }

        mapViewer.updateMapLeadCursor();

        browserCanvas.sendSelection(selecoes);
    }

    public void addSelection(HashMap<String, Boolean> selecoes, IItemId item) {
        if (kmlResult != null && kmlResult.getGPSItems().containsKey(item)) {
            List<Integer> subitems = kmlResult.getGPSItems().get(item);
            if (subitems == null) {
                String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
                selecoes.put(gid, true);
            } else {
                for (Integer subitem : subitems) {
                    String gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" //$NON-NLS-1$ //$NON-NLS-2$
                            + subitem;
                    selecoes.put(gid, true);
                }
            }
        }
    }

    @Override
    public void accept(Object[] result) {
        KMLResult kmlResult = (KMLResult) result[0];

        if (kmlResult != null) {
            if (result[1] != null) {
                geoReferencedBitmap = (RoaringBitmap[]) result[1];
            }

            if (kmlResult.getItemsWithGPS() == 0) {
                gpsProgressBar.setValue(0);
                gpsProgressBar.setString(Messages.getString("KMLResult.NoGPSItem"));
            } else {
                gpsProgressBar.setVisible(false);
            }
            this.kmlResult = kmlResult;
            browserCanvas.setKML(kmlResult.getKML());

            mapaDesatualizado = false;
        }
    }

    public void update() {
        mapViewer.applyCheckedItems();
        browserCanvas.update();
        mapaDesatualizado = false;
    }

    public boolean isMapOutDated() {
        return mapaDesatualizado;
    }

    public void setMapOutDated(boolean mapOutDated) {
        this.mapaDesatualizado = mapOutDated;
    }

    public void selectCheckbox(IItemId item, boolean b) {

        if (kmlResult != null && kmlResult.getGPSItems().containsKey(item)) {
            List<Integer> subitems = kmlResult.getGPSItems().get(item);
            if (subitems == null) {
                String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
                browserCanvas.selectCheckbox(gid, b);
            } else {
                for (Integer subitem : subitems) {
                    String gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                    browserCanvas.selectCheckbox(gid, b);
                }
            }

        }
    }

    public IMultiSearchResultProvider getResultsProvider() {
        return resultsProvider;
    }

    public void runAfterLoad(Runnable run) {
        if (this.kmlResult != null && mapaDesatualizado == false) {
            run.run();
        } else {
            if (browserCanvas != null) {
                browserCanvas.runAfterLoad(run);
            }
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();

        // disabled for now, see discussion on
        // https://github.com/sepinf-inc/IPED/issues/1443
        if (browserCanvas != null) {
            Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
            if (bgColor != null) {
                // browserCanvas.getContainer().setBackground(bgColor);
            }
            // browserCanvas.update();
        }
    }

    String lastMid = "";
    int lastPos = -1;
    boolean lastMidReset = true;

    public void disableLastMidReset() {
        lastMidReset = false;
    }

    class LastMidTableModelListener implements TableModelListener {
        @Override
        public void tableChanged(TableModelEvent e) {
            if (lastMidReset) {
                lastMid = "";
                lastPos = -1;
            }
            lastMidReset = true;
        }
    }

    LastMidTableModelListener lastMidTableModelListener = new LastMidTableModelListener();

    public int getItemPositioninResultsTable(String mid) {
        if (mid.equals(lastMid)) {
            return lastPos;
        }

        JTable t = this.getResultsProvider().getResultsTable();

        t.getModel().removeTableModelListener(lastMidTableModelListener);
        t.getModel().addTableModelListener(lastMidTableModelListener);

        int pos = -1;
        IMultiSearchResult results = this.getResultsProvider().getResults();
        for (int i = 0; i < results.getLength(); i++) {
            IItemId item = results.getItem(i);
            String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
            if (mid.equals(gid)) {
                pos = i;
                break;
            }
        }

        lastMid = mid;
        if (pos != -1) {
            lastPos = t.convertRowIndexToView(pos);

            return lastPos;
        } else {
            return -1;
        }
    }

    public MapViewer getMapViewer() {
        return mapViewer;
    }

    public void setMapViewer(MapViewer mapViewer) {
        this.mapViewer = mapViewer;
    }

    static final String[] trackSortFields = { BasicProps.NAME };

    public IItemId[] getTrackSiblings() {
        try {
            int leadIndex = resultsProvider.getResultsTable().getSelectionModel().getLeadSelectionIndex();
            if (leadIndex != -1) {
                IItemId item = resultsProvider.getResults()
                        .getItem(resultsProvider.getResultsTable().convertRowIndexToModel(leadIndex));
                int docId = resultsProvider.getIPEDSource().getLuceneId(item);
                Document doc = resultsProvider.getIPEDSource().getReader().document(docId);
                String parentId = doc.get(IndexItem.PARENTID);
                if (parentId != null) {
                    int parentDocId = resultsProvider.getIPEDSource()
                            .getLuceneId(new ItemId(item.getSourceId(), Integer.parseInt(parentId)));
                    Document parentDoc = resultsProvider.getIPEDSource().getReader().document(parentDocId);
                    if ("1".equals(parentDoc.get("geo:isTrack"))) {
                        IIPEDSearcher search = resultsProvider.createNewSearch("parentId:" + parentId, trackSortFields);

                        IMultiSearchResult results = search.multiSearch();

                        if (results.getLength() > 0) {
                            IItemId[] siblings = new IItemId[results.getLength()];
                            for (int i = 0; i < results.getLength(); i++) {
                                siblings[i] = results.getItem(i);
                            }
                            return siblings;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getSelectedJSONFeature() {
        try {
            int leadIndex = resultsProvider.getResultsTable().getSelectionModel().getLeadSelectionIndex();
            if (leadIndex != -1) {
                IItemId item = resultsProvider.getResults()
                        .getItem(resultsProvider.getResultsTable().convertRowIndexToModel(leadIndex));
                int docId = resultsProvider.getIPEDSource().getLuceneId(item);
                Document doc = resultsProvider.getIPEDSource().getReader().document(docId);
                String jsonFeature = doc.get(GeofileParser.FEATURE_STRING);
                return jsonFeature;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String[] getSelectedRegexFeatures() {
        try {
            IItemId item = resultsProvider.getResults().getItem(resultsProvider.getResultsTable().convertRowIndexToModel(resultsProvider.getResultsTable().getSelectionModel().getLeadSelectionIndex()));
            int docId = resultsProvider.getIPEDSource().getLuceneId(item);
            Document doc = resultsProvider.getIPEDSource().getReader().document(docId);

            int count = 0;
            String[][] features = new String[fieldNames.length][];
            for (int i = 0; i < fieldNames.length; i++) {
                features[i] = doc.getValues("Regex:" + fieldNames[i]);
                count += features[i].length;
            }

            String[] result = new String[count];
            int findex = 0;
            int counttoLast = 0;
            int featIndex = 0;
            for (int i = 0; i < count; i++) {
                featIndex = i - counttoLast;
                while (featIndex >= features[findex].length) {
                    counttoLast = i;
                    findex++;
                }
                result[i] = features[findex][featIndex];
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasItem(IItemId item) {
        return geoReferencedBitmap[item.getSourceId()].contains(item.getId());
    }

}
