package iped.app.ui.splash;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Vector;

public class StartUpControl {
    public static final String ipedChildProcessPID = "iped.childProcessPID";

    public static int getCurrentProcessSize() {
        try {
            Field f = ClassLoader.class.getDeclaredField("classes");
            f.setAccessible(true);
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            @SuppressWarnings("unchecked")
            Vector<Class<?>> classes = (Vector<Class<?>>) f.get(classLoader);
            return classes.size();
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
