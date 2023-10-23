package iped.app.ui.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import bibliothek.gui.dock.common.location.CStackLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.station.screen.ScreenDockProperty;
import iped.app.ui.App;
import iped.utils.IOUtil;
import iped.viewers.MultiViewer;

public class PanelsLayout {
    private static final File dir = new File(System.getProperty("user.home"), ".iped");
    private static final File file = new File(dir, "PanelsLayout.dat");
    private static final File fileExt = new File(dir, "PanelsLayoutExt.dat");

    public static boolean load(CControl control) {
        BufferedReader in = null;
        try {
            if (file.exists() && replaceOldClassName()) {
                // Store location and parent per CDockable before restoring the layout
                Map<CDockable, CDockable> asidePerDockable = new HashMap<CDockable, CDockable>();
                for (int i = 0; i < control.getCDockableCount(); i++) {
                    CDockable di = control.getCDockable(i);
                    CLocation li = di.getBaseLocation();
                    if (li instanceof CStackLocation) {
                        CStackLocation si = (CStackLocation) li;
                        for (int j = 0; j < control.getCDockableCount(); j++) {
                            if (j != i) {
                                CDockable dj = control.getCDockable(j);
                                CLocation lj = dj.getBaseLocation();
                                if (lj instanceof CStackLocation) {
                                    CStackLocation sj = (CStackLocation) lj;
                                    if (si.getParent() != null && si.getParent().equals(sj.getParent())
                                            && si.getIndex() == sj.getIndex() - 1) {
                                        asidePerDockable.put(dj, di);
                                    }
                                }
                            }
                        }
                    }
                }

                // Restore previously saved layout
                control.read(file);

                // Store docks that are showing
                List<CDockable> showingDockables = new ArrayList<CDockable>();
                for (int i = 0; i < control.getCDockableCount(); i++) {
                    CDockable dock = control.getCDockable(i);
                    if (dock.intern().isDockableShowing()) {
                        showingDockables.add(dock);
                    }
                }

                // Fix #1565: handle CDockables added in newer IPED versions, that didn't exist
                // when the layout was saved. If location is null, restore location based on
                // positions before control.read().
                boolean changed = false;
                for (int i = 0; i < control.getCDockableCount(); i++) {
                    CDockable dock = control.getCDockable(i);
                    if (dock.getBaseLocation() == null) {
                        changed = true;
                        dock.setVisible(true);
                        CDockable prevDock = asidePerDockable.get(dock);
                        if (prevDock != null) {
                            dock.setLocationsAside(prevDock);
                        }
                    }
                }

                if (changed) {
                    // Restore docks that were showing (selected tabs)
                    for (CDockable dock : showingDockables) {
                        if (!dock.intern().isDockableShowing()) {
                            if (dock instanceof DefaultSingleCDockable) {
                                DefaultSingleCDockable sd = (DefaultSingleCDockable) dock;
                                App.get().selectDockableTab(sd);
                            }
                        }
                    }
                }
            }

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

    private static boolean replaceOldClassName() {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String string = new String(bytes, StandardCharsets.ISO_8859_1);
            string = string.replace("ViewersRepository", MultiViewer.class.getSimpleName());
            Files.write(file.toPath(), string.getBytes(StandardCharsets.ISO_8859_1));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean save(CControl control) {
        BufferedWriter out = null;
        try {
            if (!dir.exists())
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
