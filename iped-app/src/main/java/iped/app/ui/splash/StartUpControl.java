package iped.app.ui.splash;

import java.io.File;
import java.lang.management.ManagementFactory;

public class StartUpControl {
    public static final String ipedChildProcessPID = "iped.childProcessPID";

    public static int getCurrentProcessSize() {
        // Coarse startup-progress signal: the number of classes loaded so far.
        // This used to read the private ClassLoader.classes field by reflection, which no
        // longer works on modern JDKs (the field was removed and strong encapsulation blocks
        // such access). The public class-loading MXBean gives an equivalent, growing count.
        return ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
    }

    public static File getTempFolder() {
        return new File(System.getProperty("java.io.tmpdir"), "iped-start");
    }

    public static File getStartUpFile(File tmpFolder, long pid) {
        return new File(tmpFolder, pid + ".tmp");
    }
}
