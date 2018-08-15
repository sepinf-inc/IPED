package dpf.sp.gpinf.indexer.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TouchSleuthkitImages {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TouchSleuthkitImages.class);
    
    private static final byte[] ewfSignature = new byte[] {0x45,0x56,0x46,0x09,0x0D,0x0A,(byte) 0xFF,0x00};
    
    private static final Set<Long> preOpenedEvidenceIDs = new HashSet<Long>();  
    
    public static void preOpenImagesOnSleuth(SleuthkitCase sleuthCase, boolean cacheWarmUpEnabled, int maxThreads) {
        if (sleuthCase == null) return;
        LOGGER.info("Pre-opening Images on Sleuthkit"); //$NON-NLS-1$
        try {
            final Map<Long, List<String>> imgPaths = sleuthCase.getImagePaths();
            final List<Content> contents = new ArrayList<Content>(sleuthCase.getRootObjects());

            if (cacheWarmUpEnabled) {
                final LinkedList<String> paths = new LinkedList<String>();
                final Set<String> pending = new HashSet<String>();
                for (Content c : contents) {
                    long id = c.getDataSource().getId();
                    synchronized (preOpenedEvidenceIDs) {
                        if (preOpenedEvidenceIDs.contains(id)) continue;
                    }
                    List<String> p = imgPaths.get(id);
                    if (p != null) {
                        for (String path : p) {
                            paths.add(path);
                            pending.add(path);
                        }
                    }
                }
                if (!paths.isEmpty()) {
                    int totSegments = paths.size();
                    long tTotalOpenOnSleuth = System.currentTimeMillis();
                    Thread[] threads = new Thread[Math.min(maxThreads + 1, paths.size() + 1)];
                    final AtomicLong totSections = new AtomicLong();
                    for (int i = 1; i < threads.length; i++) {
                        (threads[i] = new Thread() {
                            public void run() {
                                byte[] b0 = new byte[13];
                                byte[] b1 = new byte[24];
                                while (true) {
                                    long tWarmUp = System.currentTimeMillis();
                                    String path = null;
                                    synchronized (paths) {
                                        if (paths.isEmpty()) break;
                                        path = paths.removeFirst();
                                    }
                                    File img = new File(path);
                                    InputStream in = null;
                                    try {
                                        in = new BufferedInputStream(new FileInputStream(img), 2048);
                                        long offset = in.read(b0);
                                        boolean isEwf = true;
                                        for (int j = 0; j < ewfSignature.length; j++) {
                                            if (b0[j] != ewfSignature[j]) {
                                                isEwf = false;
                                                break;
                                            }
                                        }
                                        if (isEwf) {
                                            offset += in.read(b1);
                                            int sections = 0;
                                            while (++sections < 65536) {
                                                long nextSection = 0;
                                                for (int i = 0; i < 8; i++) {
                                                    nextSection <<= 8;
                                                    nextSection |= b1[23 - i] & 0xFF;
                                                }
                                                if (nextSection <= 0) break;
                                                for (int j = 0; j < 16 && offset < nextSection; j++) {
                                                    offset += in.skip(nextSection - offset);
                                                }
                                                offset += in.read(b1);
                                            }
                                            tWarmUp = System.currentTimeMillis() - tWarmUp;
                                            LOGGER.debug("Cache warm up for file " + img.getAbsolutePath() + ", sections = " + sections + ", elapsed ms = " + tWarmUp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                            synchronized (paths) {
                                                totSections.addAndGet(sections);
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        try {
                                            if (in != null) in.close();
                                        } catch (Exception e2) {}
                                    }
                                    if (path != null) {
                                        synchronized (pending) {
                                            pending.remove(path);
                                        }
                                    }
                                }
                            }
                        }).start();
                    }
                    (threads[0] = new Thread() {
                        public void run() {
                            try {
                                while (!contents.isEmpty()) {
                                    for (int i = 0; i < contents.size(); i++) {
                                        Content c = contents.get(i);
                                        long id = c.getDataSource().getId();
                                        synchronized (preOpenedEvidenceIDs) {
                                            if (preOpenedEvidenceIDs.contains(id)) {
                                                contents.remove(i--);
                                                continue;
                                            }
                                        }
                                        List<String> l = imgPaths.get(id);
                                        if (l != null && !l.isEmpty()) {
                                            Set<String> s = new HashSet<String>(l);
                                            synchronized (pending) {
                                                s.retainAll(pending);
                                            }
                                            if (!s.isEmpty()) continue;
                                        }
                                        synchronized (preOpenedEvidenceIDs) {
                                            preOpenedEvidenceIDs.add(id);
                                            contents.remove(i--);
                                        }
                                        byte[] b = new byte[1];
                                        long tSleuthInit = System.currentTimeMillis();
                                        c.read(b, 0, 1);
                                        tSleuthInit = System.currentTimeMillis() - tSleuthInit;
                                        LOGGER.info("Evidence " + c.getName() + " opened on Sleuth, elapsed time (ms) = " + tSleuthInit); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    }
                                    Thread.sleep(100);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    for (int i = 0; i < threads.length; i++) {
                        try {
                            threads[i].join();
                        } catch (InterruptedException e) {}
                    }
                    tTotalOpenOnSleuth = System.currentTimeMillis() - tTotalOpenOnSleuth;
                    LOGGER.info("Pre-open images on Sleuth: cache warm up = enabled, total time (ms) = " + tTotalOpenOnSleuth + ", evidences = " + imgPaths.size() + ", segments = " + totSegments + ", sections = " + totSections.get()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                }
            } else {
                long tTotalOpenOnSleuth = System.currentTimeMillis();
                for (Content c : contents) {
                    long id = c.getDataSource().getId();
                    synchronized (preOpenedEvidenceIDs) {
                        if (preOpenedEvidenceIDs.contains(id)) continue;
                    }
                    byte[] b = new byte[1];
                    long tSleuthInit = System.currentTimeMillis();
                    try {
                        c.read(b, 0, 1);
                    } catch (TskCoreException e) {
                        e.printStackTrace();
                    }
                    tSleuthInit = System.currentTimeMillis() - tSleuthInit;
                    LOGGER.info("Evidence " + c.getName() + " opened on Sleuth, elapsed time (ms) = " + tSleuthInit); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    synchronized (preOpenedEvidenceIDs) {
                        preOpenedEvidenceIDs.add(id);
                    }
                }
                tTotalOpenOnSleuth = System.currentTimeMillis() - tTotalOpenOnSleuth;
                LOGGER.info("Pre-open images on Sleuth: cache warm up = disabled, total time (ms) = " + tTotalOpenOnSleuth + ", evidences = " + imgPaths.size()); //$NON-NLS-1$ //$NON-NLS-2$
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
