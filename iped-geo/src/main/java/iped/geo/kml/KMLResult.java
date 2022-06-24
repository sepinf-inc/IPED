package iped.geo.kml;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.lang.ArrayUtils;

import iped.data.IItemId;
import iped.geo.localization.Messages;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;

public class KMLResult {
    private static FileDialog fDialog;

    private GUIProvider guiProvider;
    private IMultiSearchResultProvider app;

    private Map<IItemId, List<Integer>> gpsItems = new HashMap<>();
    private String kmlResult = "";
    private int itemsWithGPS = 0;

    public KMLResult() {
    }

    public KMLResult(IMultiSearchResultProvider app, GUIProvider guiProvider) {
        this.guiProvider = guiProvider;
        this.app = app;
    }

    public Map<IItemId, List<Integer>> getGPSItems() {
        return gpsItems;
    }

    public int getItemsWithGPS() {
        return itemsWithGPS;
    }

    public String getKML() {
        return this.kmlResult;
    }

    public void setResultKML(String kml, int itemsWithGPS, Map<IItemId, List<Integer>> gpsItems) {
        this.kmlResult = kml;
        this.itemsWithGPS = itemsWithGPS;
        this.gpsItems = gpsItems;
    }

    public void saveKML() {
        if (fDialog == null)
            fDialog = guiProvider.createFileDialog(Messages.getString("KMLResult.Save"), FileDialog.SAVE); //$NON-NLS-1$

        fDialog.setVisible(true);
        if (fDialog.getFile() != null) {
            String path = fDialog.getDirectory() + fDialog.getFile();
            File f = new File(path);

            FileWriter w;
            try {
                w = new FileWriter(f);
                String[] cols = guiProvider.getColumnsManager().getLoadedCols();
                cols = (String[]) ArrayUtils.subarray(cols, 2, cols.length);
                GetResultsKMLWorker kmlWorker = new GetResultsKMLWorker(app, cols, null, null);
                kmlWorker.execute();
                w.write(kmlWorker.get().getKML());
                w.close();
                f = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static public String converteCoordFormat(String coord) {
        StringTokenizer st = new StringTokenizer(coord, "°\"'"); //$NON-NLS-1$
        String result = ""; //$NON-NLS-1$

        int grau = Integer.parseInt(st.nextToken());
        String min = st.nextToken().trim();
        String resto = st.nextToken().trim();
        double dec = (double) Integer.parseInt(min) * (double) 60 + Double.parseDouble(resto.replace(",", ".")); //$NON-NLS-1$ //$NON-NLS-2$
        if (grau > 0) {
            result += Double.toString(((double) grau) + (dec / (double) 3600));
        } else {
            result += Double.toString(((double) grau) - (dec / (double) 3600));
        }

        return result;
    }
}
