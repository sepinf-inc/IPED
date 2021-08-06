package dpf.mt.gpinf.indexer.search.kml;

import java.awt.Dialog.ModalityType;
import java.awt.FileDialog;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;

import org.apache.commons.lang.ArrayUtils;

import dpf.mt.gpinf.mapas.util.Messages;
import iped3.IItemId;
import iped3.desktop.GUIProvider;
import iped3.desktop.ProgressDialog;
import iped3.search.IMultiSearchResultProvider;
import iped3.util.BasicProps;

public class KMLResult {
    private static FileDialog fDialog;

    protected Map<IItemId, List<Integer>> gpsItems = new HashMap<>();
    private String kmlResult;

    GUIProvider guiProvider;
    IMultiSearchResultProvider app;

    public KMLResult(IMultiSearchResultProvider app, GUIProvider guiProvider) {
        this.guiProvider = guiProvider;
        this.app = app;
    }

    public Map<IItemId, List<Integer>> getGPSItems() {
        return gpsItems;
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
                w.write(getResultsKML(cols, false));
                w.close();
                f = null;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public String getResultsKML() throws IOException {
        if (kmlResult == null)
            kmlResult = getResultsKML(new String[] { BasicProps.ID }, true);

        return kmlResult;
    }

    private String getResultsKML(String[] colunas, boolean showProgress) throws IOException {

        ProgressDialog progress = null;
        if (showProgress)
            progress = guiProvider.createProgressDialog(null, false, 1000, ModalityType.APPLICATION_MODAL);

        GetResultsKMLWorker getKML = new GetResultsKMLWorker(app, this, colunas, progress);
        getKML.execute();

        if (showProgress)
            progress.setVisible();
        try {
            String kml = getKML.get();
            if (showProgress && getKML.itemsWithGPS == 0)
                JOptionPane.showMessageDialog(null, Messages.getString("KMLResult.NoGPSItem")); //$NON-NLS-1$

            return kml;

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return ""; //$NON-NLS-1$

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
