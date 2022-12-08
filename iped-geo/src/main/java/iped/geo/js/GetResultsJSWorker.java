package iped.geo.js;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import javax.swing.JProgressBar;
import javax.swing.SortOrder;

import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;

import iped.data.IItemId;
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
    IMultiSearchResultProvider app;
    String[] colunas;
    JProgressBar progress;
    int contSemCoordenadas = 0, itemsWithGPS = 0;
    AbstractMapCanvas browserCanvas;
    Consumer consumer;
    private double minlongit;
    private double maxlongit;
    private double minlat;
    private double maxlat;


    public GetResultsJSWorker(IMultiSearchResultProvider app, String[] colunas, JProgressBar progress, AbstractMapCanvas browserCanvas, Consumer consumer) {
        this.app = app;
        this.colunas = colunas;
        this.progress = progress;
        this.browserCanvas = browserCanvas;
        this.consumer = consumer;
    }

    @Override
    public void done() {
        if (consumer != null) {
            Object result = null;
            try {
                result = this.get();
            } catch (Exception e) {
                if(e instanceof CancellationException) {
                    
                }else {
                    e.printStackTrace();
                }
            }
            if(result!=null) {
                consumer.accept(result);
            }
        }
    }

    @Override
    protected KMLResult doInBackground() throws Exception {
        int countPlacemark=0;
        KMLResult kmlResult = new KMLResult();

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
            
            minlongit=190.0; maxlongit=-190.0; minlat=190.0; maxlat=-190.0;

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
                if(isCancelled()) {
                    return null;
                }

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
                double dlat;
                String longit;
                double dlongit;
                String alt = resolveAltitude(doc);

                String[] locations = doc.getValues(ExtraProperties.LOCATIONS);

                if (locations != null && locations.length == 1) {
                    String[] locs = locations[0].split(";"); //$NON-NLS-1$
                    lat = locs[0].trim();
                    longit = locs[1].trim();
                    generateLocationJSMarker(coluna, doc, df, row, item, lat, longit, alt, -1);
                    updateViewableRegion(longit, lat);
                    countPlacemark++;
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
                        countPlacemark++;
                        subitems.add(subitem);
                    }
                } else {
                    contSemCoordenadas++;
                }
                
                if(countPlacemark==10) {
                    browserCanvas.viewAll(minlongit, minlat , maxlongit, maxlat);//adjust the map early to show the first added coordinate instead of 
                                            //preconfigured map initial position
                }
            }
            kmlResult.setResultKML("", itemsWithGPS, gpsItems);
            browserCanvas.refreshMap();
            browserCanvas.viewAll(minlongit, minlat, maxlongit, maxlat);
        } catch (Exception e) {
            if(!isCancelled()) {
                e.printStackTrace();
            }
        } finally {
            // Thread.currentThread().setContextClassLoader(oldCcl);

        }

        return kmlResult;

    }

    private void updateViewableRegion(String longit, String lat) {
        try {
            double dlongit = Double.parseDouble(longit);
            if(dlongit<minlongit) {
                minlongit = dlongit;
            }
            if(dlongit>maxlongit) {
                maxlongit=dlongit;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        try {
            double dlat = Double.parseDouble(lat);
            if(dlat<minlat) {
                minlat = dlat;
            }
            if(dlat>maxlat) {
                maxlat = dlat;
            }
        }catch (Exception e) {
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
        boolean checked = app.getIPEDSource().getMultiBookmarks().isChecked(item);
        boolean selected = app.getResultsTable().isRowSelected(row);

        browserCanvas.addPlacemark(gid, htmlFormat(doc.get(BasicProps.NAME)), Messages.getString("KMLResult.SearchResultsDescription"), longit, lat, checked, selected);
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
