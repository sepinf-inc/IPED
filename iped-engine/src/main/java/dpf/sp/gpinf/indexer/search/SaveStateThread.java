package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dpf.sp.gpinf.indexer.util.Util;
import iped3.search.IBookmarks;

public class SaveStateThread extends Thread {

    private static SaveStateThread instance = getInstance();

    private static final String BKP_DIR = "bkp"; //$NON-NLS-1$

    public static int MAX_BACKUPS = 10;
    public static long BKP_INTERVAL = 60; // seconds

    private static Map<IBookmarks, File> stateMap = new ConcurrentHashMap<>();

    private SaveStateThread() {
    }

    public static SaveStateThread getInstance() {
        if (instance == null) {
            instance = new SaveStateThread();
            instance.setDaemon(true);
            instance.start();
        }
        return instance;
    }

    public synchronized void saveState(IBookmarks state, File file) {
        stateMap.put(state, file);
    }

    public void run() {
        while (!Thread.interrupted()) {
            try {
                for (IBookmarks state : stateMap.keySet().toArray(new IBookmarks[0])) {
                    File file = stateMap.remove(state);
                    if (file == null)
                        continue;

                    File tmp = new File(file.getAbsolutePath() + ".tmp"); //$NON-NLS-1$
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
                }
                Thread.sleep(200);

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
