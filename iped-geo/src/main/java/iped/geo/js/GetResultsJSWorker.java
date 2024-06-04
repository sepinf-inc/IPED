package iped.geo.js;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.JProgressBar;
import javax.swing.SortOrder;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;
import org.roaringbitmap.RoaringBitmap;

import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.search.MultiSearchResult;
import iped.geo.AbstractMapCanvas;
import iped.geo.kml.KMLResult;
import iped.geo.localization.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.utils.SimpleHTMLEncoder;
import iped.viewers.api.IMultiSearchResultProvider;

public class GetResultsJSWorker extends iped.viewers.api.CancelableWorker<KMLResult, Integer> {
    public static final String MARKER_PREFIX = "marker_";
    IMultiSearchResultProvider app;
    String[] colunas;
    JProgressBar progress;
    int contSemCoordenadas = 0;
    AtomicInteger itemsWithGPS = new AtomicInteger(0);
    AbstractMapCanvas browserCanvas;
    Consumer consumer;
    private double minlongit;
    private double maxlongit;
    private double minlat;
    private double maxlat;

    RoaringBitmap[] lastResultBitmap = null;
    private IPEDMultiSource msource;

    public GetResultsJSWorker(IMultiSearchResultProvider app, String[] colunas, JProgressBar progress, AbstractMapCanvas browserCanvas, Consumer consumer) {
        this.app = app;
        this.colunas = colunas;
        this.progress = progress;
        this.browserCanvas = browserCanvas;
        this.consumer = consumer;

        int sourceId = 0;
        msource = (IPEDMultiSource) this.app.getIPEDSource();
    }

    @Override
    public void done() {
        if (consumer != null) {
            Object[] result = new Object[2];
            try {
                result[0] = this.get();
                if (lastResultBitmap != null) {
                    result[1] = lastResultBitmap;
                } else {
                    result[1] = createCasesEmptyBitmapArray(msource);// creates an empty bitmap array to avoid NPE
                }
            } catch (Exception e) {
                if (e instanceof CancellationException) {

                } else {
                    e.printStackTrace();
                }
            }
            if (result != null) {
                consumer.accept(result);
            }
        }
    }

    @Override
    protected KMLResult doInBackground() throws Exception {
        if (browserCanvas.isLoaded()) {
            return doReloadInBackground();
        } else {
            browserCanvas.load();
            return createAllPlacemarks();
        }
    }

    protected KMLResult doReloadInBackground() throws Exception {
        int countPlacemark = 0;
        KMLResult kmlResult = new KMLResult();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            String coluna = null;
            boolean descendingOrder = false;
            try {
                coluna = app.getSortColumn();
                descendingOrder = app.getSortOrder().equals(SortOrder.DESCENDING);
            } catch (Exception ex) {
                coluna = BasicProps.ID;
                descendingOrder = false;
            }

            if (descendingOrder) {
                browserCanvas.setTourOrder(coluna + "-DESC");
            } else {
                browserCanvas.setTourOrder(coluna);
            }

            minlongit = 190.0;
            maxlongit = -190.0;
            minlat = 190.0;
            maxlat = -190.0;

            IMultiSearchResult results = app.getResults();
            Document doc;

            if (progress != null) {
                progress.setMaximum(results.getLength());
            }

            String query = ExtraProperties.LOCATIONS.replace(":", "\\:") + ":*";

            IIPEDSearcher searcher = app.createNewSearch(query);
            MultiSearchResult multiResult = (MultiSearchResult) searcher.multiSearch();

            Map<IItemId, List<Integer>> gpsItems = Collections.synchronizedMap(new HashMap<>());
            for (IItemId item : multiResult.getIterator()) {
                gpsItems.put(item, null);
            }

            List<StringBuffer> gidsList = Collections.synchronizedList(new ArrayList<>());

            StringBuffer gids = null;

            int batchSize = 1000;// number of items that will be sent to webview via JS call per thread
            int maporder = 0;

            for (int row = 0; row < results.getLength(); row++) {
                if (isCancelled()) {
                    return null;
                }

                int finalRow = row;

                if (row % batchSize == 0) {// if number of items that will be sent to webview via JS call is reached
                    if (gids != null) {
                        gids.append("]");
                        gidsList.add(gids);
                    }
                    gids = new StringBuffer();
                    gids.append("[");
                }

                if (progress != null) {
                    progress.setValue(finalRow + 1);
                }

                final StringBuffer finalGids = gids;
                final IItemId item = results.getItem(app.getResultsTable().convertRowIndexToModel(finalRow));
                if (!gpsItems.containsKey(item)) {// if item does not contains any georeference
                    continue;
                }

                addItemGeoToGidLists(item, gpsItems, finalGids, maporder);

                maporder++;
                countPlacemark++;
            }
            if (gids != null && gids.length() > 5) {
                gids.append("]");
                gidsList.add(gids);
            }
            browserCanvas.updateView(gidsList);
            kmlResult.setResultKML("", itemsWithGPS.get(), gpsItems);
            browserCanvas.viewAll(minlongit, minlat, maxlongit, maxlat);
        } catch (Exception e) {
            if (!isCancelled()) {
                e.printStackTrace();
            }
        } finally {
            executorService.shutdown();
        }

        return kmlResult;

    }

    void addItemGeoToGidLists(IItemId item, Map<IItemId, List<Integer>> gpsItems, StringBuffer finalGids,
            int finalMapOrder) throws IOException {
        int luceneId = app.getIPEDSource().getLuceneId(item);
        Document doc = app.getIPEDSource().getSearcher().doc(luceneId);

        String[] locations = doc.getValues(ExtraProperties.LOCATIONS);

        if (locations != null && locations.length == 1) {
            String[] locs = locations[0].split(";"); //$NON-NLS-1$
            String lat = locs[0].trim();
            String longit = locs[1].trim();

            updateViewableRegion(longit, lat);

            String gid = GetResultsJSWorker.MARKER_PREFIX + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$

            gpsItems.put(item, null);

            int checked = 0;
            if (app.getIPEDSource().getMultiBookmarks().isChecked(item)) {
                checked = 1;
            }

            itemsWithGPS.incrementAndGet();
            finalGids.append("['" + gid + "'," + finalMapOrder + "," + checked + "],");
        } else {
            int subitem = -1;
            List<Integer> subitems = new ArrayList<>();
            gpsItems.put(item, subitems);
            for (String location : locations) {
                String[] locs = location.split(";"); //$NON-NLS-1$
                String lat = locs[0].trim();
                String longit = locs[1].trim();

                updateViewableRegion(longit, lat);
                subitem++;

                String gid = GetResultsJSWorker.MARKER_PREFIX + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                                         // //$NON-NLS-3$

                subitems.add(subitem);

                itemsWithGPS.incrementAndGet();
                finalGids.append("['" + gid + "'," + finalMapOrder + "],");
            }
        }

        if (progress != null)
            progress.setString(Messages.getString("KMLResult.LoadingGPSData") + ": " + (itemsWithGPS)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    synchronized private void updateViewableRegion(String longit, String lat) {
        try {
            double dlongit = Double.parseDouble(longit);
            if (dlongit < minlongit) {
                minlongit = dlongit;
            }
            if (dlongit > maxlongit) {
                maxlongit = dlongit;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            double dlat = Double.parseDouble(lat);
            if (dlat < minlat) {
                minlat = dlat;
            }
            if (dlat > maxlat) {
                maxlat = dlat;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getBaseGID(String gid) {
        if (gid.split("_").length == 4) {
            return gid.substring(0, gid.lastIndexOf('_'));
        } else {
            return gid;
        }
    }

    static public String htmlFormat(String html) {
        if (html == null) {
            return ""; //$NON-NLS-1$
        }
        return SimpleHTMLEncoder.htmlEncode(html);
    }

    static public String resolveAltitude(Document doc) {
        String alt = doc.get(ExtraProperties.COMMON_META_PREFIX + Metadata.ALTITUDE.getName());
        return alt;
    }

    protected KMLResult createAllPlacemarks() throws Exception {
        int countPlacemark = 0;
        KMLResult kmlResult = new KMLResult();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {

            String coluna = null;
            boolean descendingOrder = false;
            try {
                coluna = app.getSortColumn();
                descendingOrder = app.getSortOrder().equals(SortOrder.DESCENDING);
            } catch (Exception ex) {
                coluna = BasicProps.ID;
                descendingOrder = false;
            }

            if (descendingOrder) {
                browserCanvas.setTourOrder(coluna + "-DESC");
            } else {
                browserCanvas.setTourOrder(coluna);
            }

            minlongit = 190.0;
            maxlongit = -190.0;
            minlat = 190.0;
            maxlat = -190.0;

            Document doc;

            String query = ExtraProperties.LOCATIONS.replace(":", "\\:") + ":*";

            IIPEDSearcher searcher = app.createNewSearch(query, false);
            IMultiSearchResult multiResult = searcher.multiSearch();

            IMultiSearchResult results = multiResult;
            if (progress != null) {
                progress.setMaximum(results.getLength());
            }

            Map<IItemId, List<Integer>> gpsItems = Collections.synchronizedMap(new HashMap<>());

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
            df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$

            List<StringBuffer> gidsList = Collections.synchronizedList(new ArrayList<>());
            StringBuffer gids = null;

            int batchSize = 1000;

            lastResultBitmap = createCasesEmptyBitmapArray(msource);

            for (int row = 0; row < results.getLength(); row++) {
                if (isCancelled()) {
                    return null;
                }

                int finalRow = row;

                if (row % batchSize == 0) {
                    if (gids != null) {
                        gids.append("]");
                        gidsList.add(gids);
                    }
                    gids = new StringBuffer();
                    gids.append("[");
                }

                final StringBuffer finalGids = gids;

                addCreateItemGeoToGidLists(results, finalRow, finalGids, gpsItems);

                countPlacemark++;
            }
            if (gids != null && gids.length() > 5) {
                gids.append("]");
                gidsList.add(gids);
            }

            browserCanvas.createPlacemarks(gidsList);
            browserCanvas.viewAll(minlongit, minlat, maxlongit, maxlat);
            browserCanvas.setLoaded(true);
            kmlResult.setResultKML("", itemsWithGPS.get(), gpsItems);
        } catch (Exception e) {
            if (!isCancelled()) {
                e.printStackTrace();
            }
        } finally {
            executorService.shutdown();
        }

        return kmlResult;
    }

    void addCreateItemGeoToGidLists(IMultiSearchResult results, int finalRow, StringBuffer finalGids,
            Map<IItemId, List<Integer>> gpsItems) throws IOException {
        if (progress != null) {
            progress.setValue(finalRow + 1);
        }

        IItemId item = results.getItem(finalRow);
        lastResultBitmap[item.getSourceId()].add(item.getId());

        int luceneId = app.getIPEDSource().getLuceneId(item);
        Document doc = app.getIPEDSource().getSearcher().doc(luceneId);

        String lat;
        String longit;
        String alt = resolveAltitude(doc);

        String[] locations = doc.getValues(ExtraProperties.LOCATIONS);

        if (locations != null && locations.length == 1) {
            String[] locs = locations[0].split(";"); //$NON-NLS-1$

            // fix invalid values such as -043.2307
            lat = Float.valueOf(locs[0].trim()).toString();
            longit = Float.valueOf(locs[1].trim()).toString();

            String gid = GetResultsJSWorker.MARKER_PREFIX + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$

            boolean checked = app.getIPEDSource().getMultiBookmarks().isChecked(item);
            boolean selected = app.getResultsTable().isRowSelected(finalRow);

            if (finalGids.length() > 1) {
                finalGids.append(",");
            }
            finalGids.append("['" + gid + "'," + finalRow + ",'"
                    + StringEscapeUtils.escapeJavaScript(htmlFormat(doc.get(BasicProps.NAME))) + "','"
                    + Messages.getString("KMLResult.SearchResultsDescription") + "'," + lat + "," + longit + ","
                    + checked + "," + selected + "]");

            updateViewableRegion(longit, lat);
            itemsWithGPS.incrementAndGet();
            gpsItems.put(item, null);

        } else if (locations != null && locations.length > 1) {
            int subitem = -1;
            List<Integer> subitems = new ArrayList<>();
            gpsItems.put(item, subitems);
            for (String location : locations) {
                String[] locs = location.split(";"); //$NON-NLS-1$

                // fix invalid values such as -043.2307
                lat = Float.valueOf(locs[0].trim()).toString();
                longit = Float.valueOf(locs[1].trim()).toString();

                subitem++;
                String bgid = GetResultsJSWorker.MARKER_PREFIX + item.getSourceId() + "_" //$NON-NLS-1$
                        + item.getId(); // $NON-NLS-1$ //$NON-NLS-3$
                String gid = GetResultsJSWorker.MARKER_PREFIX + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                                         // //$NON-NLS-3$

                boolean checked = app.getIPEDSource().getMultiBookmarks().isChecked(item);
                boolean selected = app.getResultsTable().isRowSelected(finalRow);

                if (finalGids.length() > 1) {
                    finalGids.append(",");
                }
                finalGids.append("['" + gid + "'," + finalRow + ",'"
                        + StringEscapeUtils.escapeJavaScript(htmlFormat(doc.get(BasicProps.NAME))) + "','"
                        + Messages.getString("KMLResult.SearchResultsDescription") + "'," + lat + "," + longit + ","
                        + checked + "," + selected + ",'" + bgid + "']");

                updateViewableRegion(longit, lat);
                itemsWithGPS.incrementAndGet();
                subitems.add(subitem);
            }
        } else {
            contSemCoordenadas++;
        }
    }

    public RoaringBitmap[] createCasesEmptyBitmapArray(IPEDMultiSource msource) {
        List<IPEDSource> sources = msource.getAtomicSources();
        RoaringBitmap[] result = new RoaringBitmap[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            result[i] = new RoaringBitmap();
        }
        return result;
    }

}
