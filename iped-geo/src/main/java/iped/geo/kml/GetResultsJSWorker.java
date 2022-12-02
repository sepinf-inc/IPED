package iped.geo.kml;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

import javax.swing.JProgressBar;
import javax.swing.SortOrder;

import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;

import iped.data.IItemId;
import iped.geo.AbstractMapCanvas;
import iped.geo.localization.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.utils.DateUtil;
import iped.utils.SimpleHTMLEncoder;
import iped.viewers.api.IMultiSearchResultProvider;

public class GetResultsJSWorker extends iped.viewers.api.CancelableWorker<Void, Integer> {
    IMultiSearchResultProvider app;
    String[] colunas;
    JProgressBar progress;
    int contSemCoordenadas = 0, itemsWithGPS = 0;
    AbstractMapCanvas browserCanvas;


    public GetResultsJSWorker(IMultiSearchResultProvider app, String[] colunas, JProgressBar progress, AbstractMapCanvas browserCanvas) {
        this.app = app;
        this.colunas = colunas;
        this.progress = progress;
        this.browserCanvas = browserCanvas;
    }

    @Override
    public void done() {
        /*
        if (consumer != null) {
            KMLResult kmlResult;
            try {
                kmlResult = this.get();
            } catch (Exception e) {
                e.printStackTrace();
                kmlResult = new KMLResult();
            }
            consumer.accept(kmlResult);
        }
        */
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            browserCanvas.load();

            String coluna = null;
            boolean descendingOrder = false;
            try {
                coluna = app.getSortColumn();
                descendingOrder = app.getSortOrder().equals(SortOrder.DESCENDING);
            } catch (Exception ex) {
                coluna = BasicProps.ID;
                descendingOrder = false;
            }


            IMultiSearchResult results = app.getResults();
            Document doc;

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
            df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$

            if (progress != null) {
                progress.setMaximum(results.getLength());
            }

            String query = ExtraProperties.LOCATIONS.replace(":", "\\:") + ":*";

            IIPEDSearcher searcher = app.createNewSearch(query);
            IMultiSearchResult multiResult = searcher.multiSearch();

            Map<IItemId, List<Integer>> gpsItems = new HashMap<>();
            for (IItemId item : multiResult.getIterator()) {
                gpsItems.put(item, null);
            }

            for (int row = 0; row < results.getLength(); row++) {

                if (progress != null) {
                    progress.setValue(row + 1);
                }

                IItemId item = results.getItem(app.getResultsTable().convertRowIndexToModel(row));

                if (!gpsItems.containsKey(item)) {
                    continue;
                }

                int luceneId = app.getIPEDSource().getLuceneId(item);
                doc = app.getIPEDSource().getSearcher().doc(luceneId);

                String lat;
                String longit;
                String alt = resolveAltitude(doc);

                String[] locations = doc.getValues(ExtraProperties.LOCATIONS);

                if (locations != null && locations.length == 1) {
                    String[] locs = locations[0].split(";"); //$NON-NLS-1$
                    lat = locs[0].trim();
                    longit = locs[1].trim();
                    generateLocationJSMarker(coluna, doc, df, row, item, lat, longit, alt, -1);
                    gpsItems.put(item, null);

                } else if (locations != null && locations.length > 1) {
                    int subitem = -1;
                    List<Integer> subitems = new ArrayList<>();
                    gpsItems.put(item, subitems);
                    for (String location : locations) {
                        String[] locs = location.split(";"); //$NON-NLS-1$
                        lat = locs[0].trim();
                        longit = locs[1].trim();
                        generateLocationJSMarker(coluna, doc, df, row, item, lat, longit, alt, ++subitem);
                        subitems.add(subitem);
                    }
                } else {
                    contSemCoordenadas++;
                }

            }
            
            browserCanvas.viewAll();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Thread.currentThread().setContextClassLoader(oldCcl);

        }

        return null;

    }

    public static String getBaseGID(String gid) {
        if (gid.split("_").length == 4) {
            return gid.substring(0, gid.lastIndexOf('_'));
        } else
            return gid;

    }

    private void generateLocationJSMarker(String coluna, Document doc, SimpleDateFormat df, int row, IItemId item, String lat, String longit, String alt, int subitem) {
        if (progress != null)
            progress.setString(Messages.getString("KMLResult.LoadingGPSData") + ": " + (++itemsWithGPS)); //$NON-NLS-1$ //$NON-NLS-2$

        // necessário para múltiplos casos carregados, pois ids se repetem
        String gid;
        if (subitem < 0) {
            gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        
        browserCanvas.addPlacemark(gid,longit,lat);
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
}
