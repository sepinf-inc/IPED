package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eatthepath.jvptree.DistanceFunction;
import com.eatthepath.jvptree.VPTree;

import br.dpf.sepinf.photodna.api.PhotoDNATransforms;
import dpf.sp.gpinf.indexer.process.task.ProjectVICHashLookup.ProjectVicPhotoDNA;
import dpf.sp.gpinf.indexer.process.task.ProjectVICHashLookup.VicEntry;
import dpf.sp.gpinf.indexer.util.HashValue;
import iped3.IHashValue;
import iped3.IItem;

public class PhotoDNALookup extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(PhotoDNALookup.class);

    private static final String photoDNAFilePath = "photoDNAHashDatabase";

    public static final String PHOTO_DNA_KFF_HIT = "photoDnaKffHit";

    public static final String PHOTO_DNA_KFF_DIST = "photoDnaKffDistance";

    public static final String PHOTO_DNA_KFF_HASH = "photoDnaKffHash";

    public static int MAX_DISTANCE = 50000;

    public static boolean rotateAndFlip = true;

    private static VPTree<IHashValue, IHashValue> vptree = new VPTree<>(new VPDistance());

    private static VPDistance photoDNADistance = new VPDistance();

    private static boolean taskEnabled = true;

    private static PhotoDNATransforms transforms;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        if (taskEnabled && vptree.isEmpty()) {
            try {
                Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNATransforms");
                transforms = (PhotoDNATransforms) c.newInstance();
            } catch (ClassNotFoundException e) {
                taskEnabled = false;
                return;
            }
            ArrayList<IHashValue> photoDNAHashSet = new ArrayList<>();
            String path = confParams.getProperty(photoDNAFilePath);
            if (path != null && !path.trim().isEmpty()) {
                File photoDnaHashSet = new File(path.trim());
                if (!photoDnaHashSet.exists()) {
                    String msg = "Invalid hash database path on " + photoDnaHashSet.getAbsolutePath(); //$NON-NLS-1$
                    LOGGER.error(msg);
                } else {
                    try (BufferedReader bf = new BufferedReader(new FileReader(photoDnaHashSet))) {
                        String line = null;
                        int idx = -1;
                        while ((line = bf.readLine()) != null) {
                            String[] hashes = line.split("\\*");
                            if (idx == -1) {
                                for (int i = 0; i < hashes.length; i++)
                                    if (hashes[i].trim().length() == 2 * PhotoDNATask.HASH_SIZE)
                                        idx = i;
                            }
                            photoDNAHashSet.add(new HashValue(hashes[idx].trim()));
                        }
                    }
                }
            }
            
            photoDNAHashSet.addAll(ProjectVICHashLookup.buildPhotoDNAReferenceList());
            
            vptree.addAll(photoDNAHashSet);
            if(vptree.isEmpty()) {
                taskEnabled = false;
            }
        }
    }

    private static class VPDistance implements DistanceFunction<IHashValue> {

        @Override
        public double getDistance(IHashValue o1, IHashValue o2) {
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
        if (vptree != null) {
            vptree.clear();
            vptree = null;
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        String hashStr = (String) evidence.getExtraAttribute(PhotoDNATask.PHOTO_DNA);
        if (hashStr == null || vptree.isEmpty())
            return;

        HashValue photodna = new HashValue(hashStr);

        int rot = 0;
        boolean flip = false;
        while (rot == 0 || (rotateAndFlip && rot < 4)) {
            int degree = 90 * rot++;
            HashValue photodnaRot = new HashValue(transforms.rot(photodna.getBytes(), degree, flip));

            List<IHashValue> neighbors = vptree.getAllWithinDistance(photodnaRot, MAX_DISTANCE);

            IHashValue nearest = null;
            int min_dist = Integer.MAX_VALUE;
            for (IHashValue neighbor : neighbors) {
                int dist = (int) photoDNADistance.getDistance(neighbor, photodnaRot);
                if (dist < min_dist) {
                    min_dist = dist;
                    nearest = neighbor;
                }
            }
            if (nearest != null) {
                evidence.setExtraAttribute(PHOTO_DNA_KFF_HIT, "true");
                evidence.setExtraAttribute(PHOTO_DNA_KFF_DIST, min_dist);
                evidence.setExtraAttribute(PHOTO_DNA_KFF_HASH, nearest.toString());
                if(nearest instanceof ProjectVicPhotoDNA) {
                    VicEntry vicInfo = ((ProjectVicPhotoDNA) nearest).getVicEntry();
                    ProjectVICHashLookup.storeProjectVicEntryInfo(evidence, vicInfo);
                }
                break;
            }
            if (rot == 4 && !flip) {
                rot = 0;
                flip = true;
            }
        }
    }

}
