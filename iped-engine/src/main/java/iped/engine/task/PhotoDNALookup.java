package iped.engine.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eatthepath.jvptree.DistanceFunction;
import com.eatthepath.jvptree.VPTree;

import br.dpf.sepinf.photodna.api.PhotoDNATransforms;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.config.PhotoDNALookupConfig;
import iped.engine.hashdb.HashDBDataSource;
import iped.engine.hashdb.PhotoDnaItem;
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

    private static VPDistance photoDNADistance = new VPDistance();

    private static VPTree<PhotoDnaItem, PhotoDnaItem> vptree = new VPTree<PhotoDnaItem, PhotoDnaItem>(photoDNADistance);

    private static boolean taskEnabled;

    private static PhotoDNATransforms transforms;

    private static HashDBDataSource hashDBDataSource;

    private PhotoDNALookupConfig pdnaLookupConfig;

    @Override
    public List<Configurable<?>> getConfigurables() {
        PhotoDNALookupConfig result = ConfigurationManager.get().findObject(PhotoDNALookupConfig.class);
        if(result == null) {
            result = new PhotoDNALookupConfig();
        }
        return Arrays.asList(result);
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
                            ArrayList<PhotoDnaItem> photoDNAHashSet = readCache(hashDBFile,
                                    pdnaLookupConfig.getStatusHashDBFilter());
                            if (photoDNAHashSet != null) {
                                LOGGER.info("Load from cache file {}.", cachePath);
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
                                photoDNAHashSet = hashDBDataSource.readPhotoDNA(statusFilter);
                                if (photoDNAHashSet == null || photoDNAHashSet.isEmpty()) {
                                    LOGGER.error("PhotoDNA hashes must be loaded into IPED hashes database to enable PhotoDNALookup.");
                                } else if (writeCache(hashDBFile, photoDNAHashSet,
                                        pdnaLookupConfig.getStatusHashDBFilter())) {
                                    LOGGER.info("Cache file {} was created.", cachePath);
                                }
                            }
                            if (photoDNAHashSet != null && !photoDNAHashSet.isEmpty()) { 
                                LOGGER.info("{} PhotoDNA Hashes loaded in {} ms.", photoDNAHashSet.size(), System.currentTimeMillis() - t);
                                t = System.currentTimeMillis();
                                vptree.addAll(photoDNAHashSet);
                                LOGGER.info("Data structure built in {} ms.", System.currentTimeMillis() - t);
                                taskEnabled = true;
                            }
                        }
                    }
                }
                LOGGER.info("Task {}.", taskEnabled ? "enabled" : "disabled");
                init.set(true);
            }
        }
    }

    private static class VPDistance implements DistanceFunction<PhotoDnaItem> {

        @Override
        public double getDistance(PhotoDnaItem o1, PhotoDnaItem o2) {
            int distance = 0;
            byte[] b1 = o1.getBytes();
            byte[] b2 = o2.getBytes();
            for (int i = 0; i < b1.length; i++) {
                int diff = (0xff & b1[i]) - (0xff & b2[i]);
                distance += diff * diff;
            }
            // This is not a metric (do not satisfy triangle inequality)
            // Might it produce a wrong VPTree index???
            return distance;
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
                if (vptree != null) {
                    vptree.clear();
                    vptree = null;
                }
                finished.set(true);
            }
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        String hashStr = (String) evidence.getExtraAttribute(PhotoDNATask.PHOTO_DNA);
        if (hashStr == null) return;

        HashValue photodna = new HashValue(hashStr);

        int rot = 0;
        boolean flip = false;
        while (rot == 0 || (pdnaLookupConfig.isRotateAndFlip() && rot < 4)) {
            int degree = 90 * rot++;
            PhotoDnaItem photoDnaItemRot = new PhotoDnaItem(-1, transforms.rot(photodna.getBytes(), degree, flip));
            List<PhotoDnaItem> neighbors = vptree.getAllWithinDistance(photoDnaItemRot, pdnaLookupConfig.getMaxDistance());

            PhotoDnaItem nearest = null;
            int minDist = Integer.MAX_VALUE;
            for (PhotoDnaItem neighbor : neighbors) {
                int dist = (int) photoDNADistance.getDistance(neighbor, photoDnaItemRot);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = neighbor;
                }
            }
            if (nearest != null) {
                evidence.setExtraAttribute(PHOTO_DNA_HIT, "true");
                evidence.setExtraAttribute(PHOTO_DNA_DIST, minDist);
                evidence.setExtraAttribute(PHOTO_DNA_NEAREAST_HASH, nearest.toString());

                String md5 = hashDBDataSource.getMD5(nearest.getHashId());
                if (md5 != null) {
                    evidence.setExtraAttribute(PHOTO_DNA_HIT_PREFIX + "md5", md5);
                }
                Map<String, List<String>> properties = hashDBDataSource.getProperties(nearest.getHashId());
                for (String name : properties.keySet()) {
                    if (!name.equalsIgnoreCase("photoDna")) {
                        List<String> value = properties.get(name);
                        evidence.setExtraAttribute(PHOTO_DNA_HIT_PREFIX + name, value);
                    }
                }
                break;
            }
            if (rot == 4 && !flip) {
                rot = 0;
                flip = true;
            }
        }
    }

    private boolean writeCache(File hashDBFile, ArrayList<PhotoDnaItem> photoDNAHashSet, String filter) {
        File cacheFile = new File(cachePath);
        boolean ret = false;
        DataOutputStream os = null;
        try {
            if (cacheFile.getParentFile() != null && !cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }
            os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)));
            os.writeLong(hashDBFile.length());
            os.writeLong(hashDBFile.lastModified());
            os.writeInt(filter.length());
            os.writeChars(filter);
            os.writeInt(photoDNAHashSet.size());
            for (PhotoDnaItem v : photoDNAHashSet) {
                os.writeInt(v.getHashId());
                byte[] b = v.getBytes();
                os.write(b);
            }
            ret = true;
        } catch (Exception e) {
            LOGGER.warn("Error writing cache file " + cacheFile.getPath(), e);
            return false;
        } finally {
            IOUtil.closeQuietly(os);
            if (!ret) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {}
            }
        }
        return ret;
    }

    private ArrayList<PhotoDnaItem> readCache(File hashDBFile, String filter) {
        File cacheFile = new File(cachePath);
        if (!cacheFile.exists()) return null;
        DataInputStream is = null;
        boolean ret = false;
        ArrayList<PhotoDnaItem> photoDNAHashSet = null;
        try {
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile), 1 << 20));
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
                            int len = is.readInt();
                            photoDNAHashSet = new ArrayList<PhotoDnaItem>(len);
                            for (int i = 0; i < len; i++) {
                                int hashId = is.readInt();
                                byte[] b = new byte[PhotoDNATask.HASH_SIZE];
                                is.read(b);
                                photoDNAHashSet.add(new PhotoDnaItem(hashId, b));
                            }
                            ret = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error reading cache file " + cacheFile.getPath(), e);
            return null;
        } finally {
            IOUtil.closeQuietly(is);
            if (!ret) {
                try {
                    cacheFile.delete();
                } catch (Exception e) {}
            }
        }
        return ret ? photoDNAHashSet : null;
    }
}
