package iped.app.ui.splash;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

public class StartUpControl {
    public static final String ipedChildProcessPID = "iped.childProcessPID";

    public static int getCurrentProcessSize() {
        try {
            ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
            return (int) cl.getTotalLoadedClassCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static File getTempFolder() {
        return new File(System.getProperty("java.io.tmpdir"), "iped-start");
    }

    public static File getStartUpFile(File tmpFolder, long pid) {
        return new File(tmpFolder, pid + ".tmp");
    }
}
