package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eatthepath.jvptree.DistanceFunction;
import com.eatthepath.jvptree.VPTree;

import br.dpf.sepinf.photodna.api.PhotoDNATransforms;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import gpinf.hashdb.HashDBDataSource;
import gpinf.hashdb.PhotoDnaItem;
import iped3.IItem;

public class PhotoDNALookup extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(PhotoDNALookup.class);

    private static final String cachePath = System.getProperty("user.home") + "/.indexador/photodnalookup.cache";

    public static final String PHOTO_DNA_HIT = "photoDnaHit"; 

    public static final String PHOTO_DNA_DIST = "photoDnaDistance";

    public static final String PHOTO_DNA_NEAREAST_HASH = "photoDnaNearestHash";

    public static final String PHOTO_DNA_HIT_PREFIX = "photoDnaHit:";

    public static int MAX_DISTANCE = 50000;

    public static boolean rotateAndFlip = true;
    
    private static final Object lock = new Object();

    private static VPDistance photoDNADistance = new VPDistance();

    private static VPTree<PhotoDnaItem, PhotoDnaItem> vptree = new VPTree<PhotoDnaItem, PhotoDnaItem>(photoDNADistance);

    private static boolean taskEnabled = true;

    private static PhotoDNATransforms transforms;

    private static HashDBDataSource hashDBDataSource;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (lock) {
            String value = confParams.getProperty(PhotoDNATask.ENABLE_PHOTO_DNA);
            if (value != null && !value.trim().isEmpty()) {
                taskEnabled = Boolean.valueOf(value.trim());
            }
            if (taskEnabled && vptree.isEmpty()) {
                try {
                    Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNATransforms");
                    transforms = (PhotoDNATransforms) c.newInstance();
                } catch (ClassNotFoundException e) {
                    taskEnabled = false;
                    return;
                }

                String hashDBPath = confParams.getProperty("hashesDB");
                if (hashDBPath == null) {
                    throw new IPEDException("Hash database path (hashesDB) must be configured in " + Configuration.LOCAL_CONFIG);
                }
                File hashDBFile = new File(hashDBPath.trim());
                if (!hashDBFile.exists() || !hashDBFile.canRead()) {
                    String msg = "Invalid hash database file: " + hashDBFile.getAbsolutePath();
                    if (hasIpedDatasource()) {
                        LOGGER.warn(msg);
                        return;
                    }
                    throw new IPEDException(msg);
                }
                long t = System.currentTimeMillis();
                hashDBDataSource = new HashDBDataSource(hashDBFile);
                ArrayList<PhotoDnaItem> photoDNAHashSet = readCache(hashDBFile);
                if (photoDNAHashSet != null) {
                    LOGGER.info("Load from cache file {}.", cachePath);
                } else {
                    photoDNAHashSet = hashDBDataSource.readPhotoDNA();
                    if (photoDNAHashSet == null || photoDNAHashSet.isEmpty()) {
                        LOGGER.error("PhotoDNA hashes must be loaded into IPED hash database to enable PhotoDNALookup.");
                        taskEnabled = false;
                        return;
                    }
                    if (writeCache(hashDBFile, photoDNAHashSet)) {
                        LOGGER.info("Cache file {} was created.", cachePath);
                    }
                }
                LOGGER.info("{} PhotoDNA Hashes loaded in {} ms.", photoDNAHashSet.size(), System.currentTimeMillis() - t);
                t = System.currentTimeMillis();
                vptree.addAll(photoDNAHashSet);
                LOGGER.info("Data structure built in {} ms.", System.currentTimeMillis() - t);
            }
            LOGGER.info("Task {}.", taskEnabled ? "enabled" : "disabled");
        }
    }

    private static class VPDistance implements DistanceFunction<PhotoDnaItem> {

        @Override
        public double getDistance(PhotoDnaItem o1, PhotoDnaItem o2) {
            int distance = 0;
            byte[] b1 = o1.getHash().getBytes();
            byte[] b2 = o2.getHash().getBytes();
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
        synchronized (lock) {
            if (hashDBDataSource != null) {
                hashDBDataSource.close();
            }
            if (vptree != null) {
                vptree.clear();
                vptree = null;
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
        while (rot == 0 || (rotateAndFlip && rot < 4)) {
            int degree = 90 * rot++;
            PhotoDnaItem photoDnaItemRot = new PhotoDnaItem(-1, new HashValue(transforms.rot(photodna.getBytes(), degree, flip)));
            List<PhotoDnaItem> neighbors = vptree.getAllWithinDistance(photoDnaItemRot, MAX_DISTANCE);

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

    private boolean writeCache(File hashDBFile, ArrayList<PhotoDnaItem> photoDNAHashSet) {
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
            os.writeInt(photoDNAHashSet.size());
            for (PhotoDnaItem v : photoDNAHashSet) {
                os.writeInt(v.getHashId());
                byte[] b = v.getHash().getBytes();
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

    private ArrayList<PhotoDnaItem> readCache(File hashDBFile) {
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
                    int len = is.readInt();
                    photoDNAHashSet = new ArrayList<PhotoDnaItem>(len);
                    for (int i = 0; i < len; i++) {
                        int hashId = is.readInt();
                        byte[] b = new byte[PhotoDNATask.HASH_SIZE];
                        is.read(b);
                        photoDNAHashSet.add(new PhotoDnaItem(hashId, new HashValue(b)));
                    }
                    ret = true;
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
