package iped.engine.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.api.PhotoDNATransforms;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.config.PhotoDNALookupConfig;
import iped.engine.hashdb.HashDBDataSource;
import iped.engine.hashdb.PhotoDnaHit;
import iped.engine.hashdb.PhotoDnaItem;
import iped.engine.hashdb.PhotoDnaTree;
import iped.utils.HashValue;
import iped.utils.IOUtil;

public class PhotoDNALookup extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(PhotoDNALookup.class);

    private static final String cachePath = System.getProperty("user.home") + "/.iped/photodnalookup.cache";

    public static final String PHOTO_DNA_HIT_PREFIX = "photoDnaDb:";

    public static final String PHOTO_DNA_HIT = PHOTO_DNA_HIT_PREFIX + "hit";

    public static final String PHOTO_DNA_DIST = PHOTO_DNA_HIT_PREFIX + "distance";

    public static final String PHOTO_DNA_NEAREAST_HASH = PHOTO_DNA_HIT_PREFIX + "nearestHash";

    private static final AtomicBoolean init = new AtomicBoolean(false);

    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static PhotoDnaTree pdnaTree;

    private static boolean taskEnabled;

    private static PhotoDNATransforms transforms;

    private static HashDBDataSource hashDBDataSource;
    
    // Magic number used as the first bytes of cache file
    private static final int magic = 0x20250826;

    private PhotoDNALookupConfig pdnaLookupConfig;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new PhotoDNALookupConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        pdnaLookupConfig = configurationManager.findObject(PhotoDNALookupConfig.class);

        synchronized (init) {
            if (!init.get()) {
                if (pdnaLookupConfig.isEnabled()) {
                    try {
                        Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNATransforms");
                        transforms = (PhotoDNATransforms) c.getDeclaredConstructor().newInstance();
                    } catch (ClassNotFoundException e) {
                        LOGGER.error(PhotoDNATask.PDNA_NOT_FOUND_MSG);
                        init.set(true);
                        return;
                    }
                    LocalConfig localConfig = (LocalConfig) configurationManager.findObject(LocalConfig.class);
                    if (localConfig.getHashDbFile() == null) {
                        LOGGER.error("Hashes database path (hashesDB) must be configured in {}", Configuration.LOCAL_CONFIG);
                    } else {
                        File hashDBFile = localConfig.getHashDbFile();
                        if (!hashDBFile.exists() || !hashDBFile.canRead() || !hashDBFile.isFile()) {
                            String msg = (!hashDBFile.exists() ? "Missing": "Invalid") + " hashes database file: " + hashDBFile.getAbsolutePath();
                            if (hasIpedDatasource()) {
                                LOGGER.warn(msg);
                            } else {
                                LOGGER.error(msg);
                            }
                        } else {
                            long t = System.currentTimeMillis();
                            hashDBDataSource = new HashDBDataSource(hashDBFile);
                            readCache(hashDBFile, pdnaLookupConfig.getStatusHashDBFilter());
                            if (pdnaTree != null) {
                                LOGGER.info("Load from cache file {} in {} ms.", cachePath, System.currentTimeMillis() - t);
                                taskEnabled = true;
                            } else {
                                Set<String> statusFilter = null;
                                if (!pdnaLookupConfig.getStatusHashDBFilter().isEmpty()) {
                                    statusFilter = new HashSet<String>();
                                    String[] s = pdnaLookupConfig.getStatusHashDBFilter().split(",");
                                    for(String a : s) {
                                        a = a.trim();
                                        if (!a.isEmpty()) {
                                            statusFilter.add(a);
                                        }
                                    }
                                }
                                ArrayList<PhotoDnaItem> photoDNAHashSet = hashDBDataSource.readPhotoDNA(statusFilter);
                                if (photoDNAHashSet == null || photoDNAHashSet.isEmpty()) {
                                    LOGGER.error("PhotoDNA hashes must be loaded into IPED hashes database to enable PhotoDNALookup.");
                                } else {
                                    LOGGER.info("{} PhotoDNA hashes loaded in {} ms.", photoDNAHashSet.size(), System.currentTimeMillis() - t);
                                    t = System.currentTimeMillis();
                                    pdnaTree = new PhotoDnaTree(photoDNAHashSet.toArray(new PhotoDnaItem[0]));
                                    LOGGER.info("Data structure built in {} ms.", System.currentTimeMillis() - t);
                                    taskEnabled = true;
                                    t = System.currentTimeMillis();
                                    if (writeCache(hashDBFile, pdnaLookupConfig.getStatusHashDBFilter())) {
                                        LOGGER.info("Cache file {} was created in {} ms.", cachePath, System.currentTimeMillis() - t);
                                    }
                                }
                            }
                        }
                    }
                }
                LOGGER.info("Task {}.", taskEnabled ? "enabled" : "disabled");
                init.set(true);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public void finish() throws Exception {
        synchronized (finished) {
            if (!finished.get()) {
                if (hashDBDataSource != null) {
                    hashDBDataSource.close();
                }
                pdnaTree = null;
                finished.set(true);
            }
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        PhotoDnaHit hit = null;
        String hashStr = (String) evidence.getExtraAttribute(PhotoDNATask.PHOTO_DNA);
        if (hashStr != null) {
            // Single value
            hit = lookup(hashStr);

        } else {
            @SuppressWarnings("unchecked")
            List<String> l = (List<String>) evidence.getExtraAttribute(PhotoDNATask.PHOTO_DNA_FRAMES);
            if (l != null) {
                // Multiple values
                List<PhotoDnaHit> hits = new ArrayList<>();
                for (String hash : l) {
                    PhotoDnaHit frameHit = lookup(hash);
                    if (frameHit != null) {
                        hits.add(frameHit);
                    }
                }
                if (!hits.isEmpty()) {
                    hit = combineHits(hits, l.size());
                    if (hit.sqDist > pdnaLookupConfig.getMaxDistance()) {
                        hit = null;
                    }
                }
            }
        }
        if (hit != null) {
            evidence.setExtraAttribute(PHOTO_DNA_HIT, "true");
            evidence.setExtraAttribute(PHOTO_DNA_DIST, hit.sqDist);
            evidence.setExtraAttribute(PHOTO_DNA_NEAREAST_HASH, hit.nearest.toString());

            String md5 = hashDBDataSource.getMD5(hit.nearest.getHashId());
            if (md5 != null) {
                evidence.setExtraAttribute(PHOTO_DNA_HIT_PREFIX + "md5", md5);
            }
            Map<String, List<String>> properties = hashDBDataSource.getProperties(hit.nearest.getHashId());
            for (String name : properties.keySet()) {
                if (!name.equalsIgnoreCase("photoDna")) {
                    List<String> value = properties.get(name);
                    evidence.setExtraAttribute(PHOTO_DNA_HIT_PREFIX + name, value);
                }
            }
        }
    }

    private PhotoDnaHit combineHits(List<PhotoDnaHit> hits, int len) {
        int maxHits = 0;
        int minDist = Integer.MAX_VALUE;
        List<Integer> dists = new ArrayList<Integer>();
        PhotoDnaHit hit = new PhotoDnaHit();
        for (int i = 0; i < hits.size(); i++) {
            PhotoDnaHit a = hits.get(i);
            dists.add(a.sqDist);
            int currHits = 1;
            int currDist = a.sqDist;
            for (int j = i + 1; j < hits.size(); j++) {
                PhotoDnaHit b = hits.get(j);
                if (a.nearest.equals(b.nearest)) {
                    currHits++;
                    currDist = Math.min(currDist, b.sqDist);
                }
            }
            if (currHits > maxHits || (currHits == maxHits && currDist < minDist)) {
                minDist = currDist;
                maxHits = currHits;
                hit.nearest = a.nearest;
            }
        }
        Collections.sort(dists);
        int missDist = pdnaLookupConfig.getMaxDistance() * 2;
        while (dists.size() < len) {
            dists.add(missDist);
        }
        double sum = 0;
        double weight = 1;
        double div = 0;
        for (int d : dists) {
            sum += d * weight;
            div += weight;
            weight *= 0.5;
        }
        hit.sqDist = (int) Math.round(sum / div);
        return hit;
    }

    private PhotoDnaHit lookup(String hashStr) {
        HashValue photodna = new HashValue(hashStr);
        int rot = 0;
        boolean flip = false;
        PhotoDnaHit hit = new PhotoDnaHit();
        hit.sqDist = pdnaLookupConfig.getMaxDistance() + 1;
        while (rot == 0 || (pdnaLookupConfig.isRotateAndFlip() && rot < 4)) {
            int degree = 90 * rot++;
            byte[] refBytes = transforms.rot(photodna.getBytes(), degree, flip);
            if (entropy(refBytes) > pdnaLookupConfig.getMinEntropy()) {
                // Only search if photoDNA has high entropy, to minimize false positives
                PhotoDnaHit a = pdnaTree.search(refBytes, hit.sqDist);
                if (a != null && a.sqDist < hit.sqDist) {
                    hit = a;
                }
            }
            if (rot == 4 && !flip) {
                rot = 0;
                flip = true;
            }
        }
        return hit.nearest == null ? null : hit;
    }

    /**
     * Estimate photoDNA entropy, counting non-repeated sequential bytes.
     */
    private static double entropy(byte[] bytes) {
        int n = 0;
        byte a = bytes[0];
        for (int i = 1; i < bytes.length; i++) {
            byte b = bytes[i];
            if (a != b) {
                n++;
                a = b;
            }
        }
        return n / (double) (bytes.length - 1);
    }

    private static boolean writeCache(File hashDBFile, String filter) {
        File cacheFile = new File(cachePath);
        boolean ret = false;
        ObjectOutputStream os = null;
        try {
            if (cacheFile.getParentFile() != null && !cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }
            os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile), 1 << 20));
            os.writeInt(magic);
            os.writeLong(hashDBFile.length());
            os.writeLong(hashDBFile.lastModified());
            os.writeInt(filter.length());
            os.writeChars(filter);
            os.writeObject(pdnaTree);
            ret = true;
        } catch (Exception e) {
            LOGGER.warn("Error writing cache file " + cacheFile.getPath(), e);
            return false;
        } finally {
            IOUtil.closeQuietly(os);
            if (!ret) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {
                }
            }
        }
        return ret;
    }

    private static void readCache(File hashDBFile, String filter) {
        /*
         Cache file content:
             magic (int)
             DB length (long)
             DB last modified (long)
             Status filter length (int)
             Status chars (char * length)
             PhotoDnaTree (object)
         */
        pdnaTree = null;
        File cacheFile = new File(cachePath);
        if (!cacheFile.exists())
            return;
        ObjectInputStream is = null;
        try {
            is = new ObjectInputStream(new BufferedInputStream(new FileInputStream(cacheFile), 1 << 20));
            int m = is.readInt();
            if (m == magic) {
                long fileLen = is.readLong();
                if (fileLen == hashDBFile.length()) {
                    long fileLastModified = is.readLong();
                    if (fileLastModified == hashDBFile.lastModified()) {
                        int filterLen = is.readInt();
                        if (filterLen == filter.length()) {
                            char[] c = new char[filterLen];
                            for (int i = 0; i < filterLen; i++) {
                                c[i] = is.readChar();
                            }
                            if (filter.equals(new String(c))) {
                                pdnaTree = (PhotoDnaTree) is.readObject();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error reading cache file " + cacheFile.getPath(), e);
        } finally {
            IOUtil.closeQuietly(is);
            if (pdnaTree == null) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {
                }
            }
        }
    }
}
