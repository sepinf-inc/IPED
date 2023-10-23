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
import iped.geo.localization.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.utils.DateUtil;
import iped.utils.SimpleHTMLEncoder;
import iped.viewers.api.IMultiSearchResultProvider;

public class GetResultsKMLWorker extends iped.viewers.api.CancelableWorker<KMLResult, Integer> {
    IMultiSearchResultProvider app;
    String[] colunas;
    JProgressBar progress;
    int contSemCoordenadas = 0, itemsWithGPS = 0;
    Consumer<KMLResult> consumer;

    public GetResultsKMLWorker(IMultiSearchResultProvider app, String[] colunas, JProgressBar progress, Consumer<KMLResult> consumer) {
        this.app = app;
        this.colunas = colunas;
        this.progress = progress;
        this.consumer = consumer;
    }

    @Override
    public void done() {
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
    }

    @Override
    protected KMLResult doInBackground() throws Exception {
        KMLResult kmlResult = new KMLResult();
        try {
            StringBuilder tourPlayList = new StringBuilder(""); //$NON-NLS-1$
            StringBuilder kml = new StringBuilder(""); //$NON-NLS-1$

            String coluna = null;
            boolean descendingOrder = false;
            try {
                coluna = app.getSortColumn();
                descendingOrder = app.getSortOrder().equals(SortOrder.DESCENDING);
            } catch (Exception ex) {
                coluna = BasicProps.ID;
                descendingOrder = false;
            }

            kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"); //$NON-NLS-1$
            kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" >"); //$NON-NLS-1$
            kml.append("<Document>"); //$NON-NLS-1$
            kml.append("<name>" + Messages.getString("KMLResult.SearchResults") + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            kml.append("<open>1</open>"); //$NON-NLS-1$
            kml.append("<description>" + Messages.getString("KMLResult.SearchResultsDescription") + "</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            kml.append("<Style id=\"basico\"><BalloonStyle><![CDATA[" //$NON-NLS-1$
                    + " $[name] <br/> $[description] <br/> " + Messages.getString("KMLResult.ShowInTree") //$NON-NLS-1$ //$NON-NLS-2$
                    + "]]>" //$NON-NLS-1$
                    + "</BalloonStyle></Style>"); //$NON-NLS-1$

            kml.append("<Folder>"); //$NON-NLS-1$
            kml.append("<name>" + Messages.getString("KMLResult.Results") + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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
                    generateLocationKML(tourPlayList, kml, coluna, doc, df, row, item, lat, longit, alt, -1);
                    gpsItems.put(item, null);

                } else if (locations != null && locations.length > 1) {
                    int subitem = -1;
                    List<Integer> subitems = new ArrayList<>();
                    gpsItems.put(item, subitems);
                    for (String location : locations) {
                        String[] locs = location.split(";"); //$NON-NLS-1$
                        lat = locs[0].trim();
                        longit = locs[1].trim();
                        generateLocationKML(tourPlayList, kml, coluna, doc, df, row, item, lat, longit, alt, ++subitem);
                        subitems.add(subitem);
                    }
                } else {
                    contSemCoordenadas++;
                }

            }
            kml.append("</Folder>"); //$NON-NLS-1$

            kml.append("<gx:Tour>"); //$NON-NLS-1$
            if (descendingOrder) {
                kml.append("  <name>" + coluna + "-DESC</name>"); //$NON-NLS-1$ //$NON-NLS-2$
            } else {
                kml.append("  <name>" + coluna + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            kml.append("  <gx:Playlist>"); //$NON-NLS-1$
            kml.append(tourPlayList);
            kml.append("  </gx:Playlist>"); //$NON-NLS-1$
            kml.append("</gx:Tour>"); //$NON-NLS-1$

            kml.append("</Document>"); //$NON-NLS-1$
            kml.append("</kml>"); //$NON-NLS-1$

            kmlResult.setResultKML(kml.toString(), itemsWithGPS, gpsItems);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Thread.currentThread().setContextClassLoader(oldCcl);

        }

        return kmlResult;

    }

    public static String getBaseGID(String gid) {
        if (gid.split("_").length == 4) {
            return gid.substring(0, gid.lastIndexOf('_'));
        } else
            return gid;

    }

    private void generateLocationKML(StringBuilder tourPlayList, StringBuilder outerKml, String coluna, org.apache.lucene.document.Document doc, SimpleDateFormat df, int row, IItemId item, String lat, String longit, String alt,
            int subitem) {
        if (progress != null)
            progress.setString(Messages.getString("KMLResult.LoadingGPSData") + ": " + (++itemsWithGPS)); //$NON-NLS-1$ //$NON-NLS-2$

        // necessário para múltiplos casos carregados, pois ids se repetem
        String gid;
        if (subitem < 0) {
            gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        StringBuilder kml = new StringBuilder();
        kml.append("<Placemark>"); //$NON-NLS-1$
        // kml+="<styleUrl>#basico</styleUrl>";
        kml.append("<id>" + gid + "</id>"); //$NON-NLS-1$ //$NON-NLS-2$
        kml.append("<name>" + htmlFormat(doc.get(BasicProps.NAME)) + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$
        if (!BasicProps.ID.equals(coluna))
            kml.append("<description>" + htmlFormat(coluna) + ":" + htmlFormat(doc.get(coluna)) + "</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        else
            kml.append("<description>" + htmlFormat(coluna) + ":" + htmlFormat(gid) + "</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        if (alt == null) {
            kml.append("<Point><coordinates>" + longit + "," + lat + ",0</coordinates></Point>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } else {
            kml.append("<Point><altitudemode>absolute</altitudemode><extrude>1</extrude><coordinates>" + longit + "," //$NON-NLS-1$ //$NON-NLS-2$
                    + lat + "," + alt + "</coordinates></Point>"); //$NON-NLS-1$
        }

        tourPlayList.append("<gx:FlyTo>" //$NON-NLS-1$
                + "<gx:duration>5.0</gx:duration>" //$NON-NLS-1$
                + "<gx:flyToMode>bounce</gx:flyToMode>" //$NON-NLS-1$
                + "<LookAt>" //$NON-NLS-1$
                + "<longitude>" + longit + "</longitude>" //$NON-NLS-1$ //$NON-NLS-2$
                + "<latitude>" + lat + "</latitude>" //$NON-NLS-1$ //$NON-NLS-2$
                + "<altitude>300</altitude>" //$NON-NLS-1$
                + "<altitudeMode>relativeToGround</altitudeMode>" //$NON-NLS-1$
                + "</LookAt>" //$NON-NLS-1$
                + "</gx:FlyTo>" //$NON-NLS-1$
                + "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>" //$NON-NLS-1$
                + " <Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>1</gx:balloonVisibility></Placemark>" //$NON-NLS-1$ //$NON-NLS-2$
                + " </Change></Update></gx:AnimatedUpdate>" //$NON-NLS-1$
                + "<gx:Wait><gx:duration>1.0</gx:duration></gx:Wait>" //$NON-NLS-1$
                + "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>" //$NON-NLS-1$
                + " <Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>0</gx:balloonVisibility></Placemark>" //$NON-NLS-1$ //$NON-NLS-2$
                + " </Change></Update></gx:AnimatedUpdate>"); //$NON-NLS-1$

        kml.append("<ExtendedData>"); //$NON-NLS-1$

        for (int j = 0; j < colunas.length; j++) {
            if (!BasicProps.ID.equals(colunas[j]))
                kml.append("<Data name=\"" + htmlFormat(colunas[j]) + "\"><value>" + htmlFormat(doc.get(colunas[j])) //$NON-NLS-1$ //$NON-NLS-2$
                        + "</value></Data>"); //$NON-NLS-1$
            else
                kml.append("<Data name=\"" + BasicProps.ID + "\"><value>" + gid + "</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        boolean checked = app.getIPEDSource().getMultiBookmarks().isChecked(item);
        kml.append("<Data name=\"checked\"><value>" + checked + "</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$

        boolean selected = app.getResultsTable().isRowSelected(row);
        kml.append("<Data name=\"selected\"><value>" + selected + "</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$
        kml.append("</ExtendedData>"); //$NON-NLS-1$

        String dataCriacao = doc.get(BasicProps.CREATED);
        if (dataCriacao != null && !dataCriacao.isEmpty())
            try {
                dataCriacao = df.format(DateUtil.stringToDate(dataCriacao)) + "Z"; //$NON-NLS-1$
            } catch (ParseException e) {
                dataCriacao = ""; //$NON-NLS-1$
            }
        kml.append("<TimeSpan><begin>" + dataCriacao + "</begin></TimeSpan>"); //$NON-NLS-1$ //$NON-NLS-2$

        kml.append("</Placemark>"); //$NON-NLS-1$

        outerKml.append(kml);
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
