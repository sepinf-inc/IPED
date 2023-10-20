package iped.engine.util;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import iped.data.IBookmarks;

public class SaveStateThread extends Thread {

    private static SaveStateThread instance = getInstance();

    private static final String BKP_DIR = "bkp"; //$NON-NLS-1$

    public static int MAX_BACKUPS = 10;
    public static long BKP_INTERVAL = 60; // seconds

    // Use a LinkedHashMap so bookmark files are saved in the order they are inserted
    private final Map<IBookmarks, File> stateMap = new LinkedHashMap<>();

    private SaveStateThread() {
    }

    public synchronized static SaveStateThread getInstance() {
        if (instance == null) {
            instance = new SaveStateThread();
            instance.setDaemon(true);
            instance.start();
        }
        return instance;
    }

    public void saveState(IBookmarks state, File file) {
        synchronized (stateMap) {
            stateMap.put(state, file);
        }
    }

    public void run() {
        while (!Thread.interrupted()) {
            try {
                IBookmarks state = null;
                File file = null;
                synchronized (stateMap) {
                    if (!stateMap.isEmpty()) {
                        Iterator<Map.Entry<IBookmarks, File>> it = stateMap.entrySet().iterator();
                        Map.Entry<IBookmarks, File> entry = it.next();
                        it.remove();
                        state = entry.getKey();
                        file = entry.getValue();
                    }
                }
                if (state != null && file != null) {
                    File tmp = new File(file.getAbsolutePath() + ".tmp");
                    if (tmp.exists())
                        tmp.delete();
                    state.saveState(tmp, true);
                    if (!file.exists()) {
                        tmp.renameTo(file);
                    } else {
                        File bkp = backupAndDelete(file);
                        if (!tmp.renameTo(file))
                            bkp.renameTo(file);
                    }
                } else {
                    Thread.sleep(200);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    private File backupAndDelete(File file) {
        File oldestBkp = null;
        File newestBkp = null;
        int numBkps = 0;
        File bkpDir = new File(file.getParentFile(), BKP_DIR);
        bkpDir.mkdir();
        for (File subfile : bkpDir.listFiles())
            if (subfile.getName().endsWith(".bkp.iped")) { //$NON-NLS-1$
                numBkps++;
                if (newestBkp == null || newestBkp.lastModified() < subfile.lastModified())
                    newestBkp = subfile;
                if (oldestBkp == null || oldestBkp.lastModified() > subfile.lastModified())
                    oldestBkp = subfile;
            }
        if (numBkps < MAX_BACKUPS) {
            String baseName = file.getName().substring(0, file.getName().lastIndexOf('.'));
            oldestBkp = new File(bkpDir, baseName + "." + numBkps + ".bkp.iped"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (newestBkp == null || (System.currentTimeMillis() - newestBkp.lastModified()) / 1000 > BKP_INTERVAL) {
            oldestBkp.delete();
            file.renameTo(oldestBkp);
            oldestBkp.setLastModified(System.currentTimeMillis());
            return oldestBkp;
        } else {
            file.delete();
            return newestBkp;
        }
    }
}
