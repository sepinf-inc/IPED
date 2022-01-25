package dpf.sp.gpinf.indexer.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CExternalizeArea;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.CStation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.intern.station.CScreenDockStation;
import bibliothek.gui.dock.common.location.CExternalizedLocation;
import bibliothek.gui.dock.common.location.CMaximalExternalizedLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.station.screen.ScreenDockProperty;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.util.IOUtil;

public class PanelsLayout {
    private static final File dir = new File(System.getProperty("user.home") + "/.iped");
    private static final File file = new File(dir, "PanelsLayout.dat");
    private static final File fileExt = new File(dir, "PanelsLayoutExt.dat");

    public static boolean load(CControl control) {
        BufferedReader in = null;
        try {
            if (file.exists())
                control.read(file);

            if (fileExt.exists()) {
                in = new BufferedReader(new FileReader(fileExt));

                App app = App.get();
                int colCount = Integer.parseInt(in.readLine());
                app.setGalleryColCount(colCount);

                String[] s = in.readLine().split(",");
                int x = Integer.parseInt(s[0]);
                int y = Integer.parseInt(s[1]);
                int w = Integer.parseInt(s[2]);
                int h = Integer.parseInt(s[3]);
                app.setBounds(x, y, w, h);

                int state = Integer.parseInt(in.readLine());
                app.setExtendedState(state);

                Map<String, ScreenDockProperty> locations = new HashMap<String, ScreenDockProperty>();
                Set<String> maximized = new HashSet<String>();
                String line = null;
                while ((line = in.readLine()) != null) {
                    s = line.split(",");
                    if (s.length == 6) {
                        x = Integer.parseInt(s[1]);
                        y = Integer.parseInt(s[2]);
                        w = Integer.parseInt(s[3]);
                        h = Integer.parseInt(s[4]);
                        locations.put(s[0], new ScreenDockProperty(x, y, w, h));
                        if (s[5].equals("1"))
                            maximized.add(s[0]);
                    }
                }

                int n = control.getCDockableCount();
                for (int i = 0; i < n; i++) {
                    CDockable dock = control.getCDockable(i);
                    if (dock instanceof DefaultSingleCDockable) {
                        DefaultSingleCDockable sd = (DefaultSingleCDockable) dock;
                        ScreenDockProperty location = locations.get(sd.getUniqueId());
                        if (location != null) {
                            CStation<?> station = sd.getParentStation();
                            if (station instanceof CExternalizeArea) {
                                CScreenDockStation area = ((CExternalizeArea) station).getStation();
                                area.move(sd.intern(), location);
                                if (maximized.contains(sd.getUniqueId()))
                                    sd.setExtendedMode(ExtendedMode.MAXIMIZED);
                            }
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(in);
        }
        return false;
    }

    public static boolean save(CControl control) {
        BufferedWriter out = null;
        try {
            if (!dir.getParentFile().exists())
                dir.mkdirs();

            control.write(file);

            out = new BufferedWriter(new FileWriter(fileExt));

            App app = App.get();
            out.write("" + app.getGalleryColCount());
            out.newLine();

            out.write(app.getX() + "," + app.getY() + "," + app.getWidth() + "," + app.getHeight());
            out.newLine();

            out.write("" + app.getExtendedState());
            out.newLine();

            // Externalized dockables positions are also saved, as they aren't restored
            // correctly by CControl.read().
            int n = control.getCDockableCount();
            for (int i = 0; i < n; i++) {
                CDockable dock = control.getCDockable(i);
                CLocation loc = dock.getBaseLocation();
                if (loc instanceof CExternalizedLocation) {
                    if (dock instanceof DefaultSingleCDockable) {
                        DefaultSingleCDockable sd = (DefaultSingleCDockable) dock;
                        CExternalizedLocation e = (CExternalizedLocation) loc;
                        boolean isMaximized = loc instanceof CMaximalExternalizedLocation;
                        out.write(sd.getUniqueId() + "," + e.getX() + "," + e.getY() + "," + e.getWidth() + ","
                                + e.getHeight() + "," + (isMaximized ? 1 : 0));
                        out.newLine();
                    }
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(out);
        }
        return false;
    }
}
