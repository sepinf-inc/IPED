package dpf.sp.gpinf.indexer.ui;

import java.io.File;

import bibliothek.gui.dock.common.CControl;

public class PanelsLayout {
    private static final File file = new File(System.getProperty("user.home") + "/.iped", "PanelsLayout.dat");

    public static boolean load(CControl control) {
        if (file.exists()) {
            try {
                control.read(file);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean save(CControl control) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists())
                file.mkdirs();

            control.write(file);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
