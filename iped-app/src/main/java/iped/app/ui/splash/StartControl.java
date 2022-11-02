package iped.app.ui.splash;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Vector;

public class StartControl {
    private static final File tmpdir = new File(System.getProperty("java.io.tmpdir"));
    private static File processFile;

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

    public static void setProcessPid() {
        long pid = ProcessHandle.current().pid();
        setChildProcessPid(pid, false);
    }

    public static void setChildProcessPid(long pid) {
        setChildProcessPid(pid, true);
    }

    public static void setChildProcessPid(long pid, boolean reset) {
        processFile = new File(tmpdir, "iped-start-" + pid + ".tmp");
        if (reset) {
            if (processFile.exists()) {
                processFile.delete();
            }
            processFile.deleteOnExit();
        }
    }

    public static int readProcessFile() {
        /*BufferedRe out = null;
        try {
            out = new BufferedWriter(new FileWriter(processFile));
            out.write(String.valueOf(v));
        } catch (Exception e) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
            }
        }*/
        return 0;
    }

    public static void writeProcessFile(int v) {
        /*try (BufferedWriter out = new BufferedWriter(new FileWriter(processFile))){
            out.write(String.valueOf(v));
        } */
    }
}
