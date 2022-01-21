package dpf.sp.gpinf.indexer.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import bibliothek.gui.dock.common.CControl;
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

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(out);
        }
        return false;
    }
}
