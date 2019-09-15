package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eatthepath.jvptree.DistanceFunction;
import com.eatthepath.jvptree.VPTree;

import br.dpf.sepinf.photodna.PhotoDNATransforms;
import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IItem;

public class PhotoDNALookup extends AbstractTask{
    
    private static Logger LOGGER = LoggerFactory.getLogger(PhotoDNALookup.class);
    
    private static final String photoDNAFilePath = "photoDNAFilePath";
    
    public static final String PHOTO_DNA_KFF_HIT = "photoDnaKffHit";
    
    public static final String PHOTO_DNA_KFF_DIST = "photoDnaKffDistance";
    
    public static final String PHOTO_DNA_KFF_HASH = "photoDnaKffHash";
    
    public static int MAX_DISTANCE = 50000;
    
    public static boolean rotateAndFlip = true;
    
    private static VPTree<byte[], byte[]> vptree = new VPTree<>(new VPDistance());
    
    private static Comparator<byte[]> photoDNAComparator;
    
    private static boolean taskEnabled = true;
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        try {
            if(photoDNAComparator == null)
                photoDNAComparator = new br.dpf.sepinf.photodna.PhotoDNAComparator();
        }catch(NoClassDefFoundError e) {
            taskEnabled = false;
            return;
        }
        
        if(taskEnabled && vptree.isEmpty()) {
            String path = confParams.getProperty(photoDNAFilePath);
            if(path != null && !path.trim().isEmpty()) {
                File photoDnaHashSet = new File(path.trim());
                if (!photoDnaHashSet.exists()) {
                    String msg = "Invalid hash database path on " + photoDnaHashSet.getAbsolutePath(); //$NON-NLS-1$
                    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
                    for (File source : args.getDatasources()) {
                        if (source.getName().endsWith(".iped")) {
                            LOGGER.warn(msg);
                            taskEnabled = false;
                            return;
                        }
                    }
                    throw new IPEDException(msg);
                }
                try (BufferedReader bf = new BufferedReader(new FileReader(photoDnaHashSet))){
                    String line = null;
                    ArrayList<byte[]> photoDNAHashSet = new ArrayList<>();
                    while((line = bf.readLine()) != null) {
                        String hash = line.split(",")[1];
                        photoDNAHashSet.add(getBytes(hash));
                    }
                    vptree.addAll(photoDNAHashSet);
                }
            }
        }
    }
    
    private static class VPDistance implements DistanceFunction<byte[]>{

        @Override
        public double getDistance(byte[] o1, byte[] o2) {
            int distance = 0;
            for(int i = 0; i < o1.length; i++) {
                int diff = (0xff & o1[i]) - (0xff & o2[i]);
                distance += diff * diff;
            }
            return distance;
        }
        
    }
    
    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        
        String hashStr = (String)evidence.getExtraAttribute(PhotoDNATask.PHOTO_DNA);
        if(hashStr == null || vptree.isEmpty())
            return;
        
        byte[] photodna = getBytes(hashStr);
        
        int rot = 0;
        boolean flip = false;
        while(rot == 0 || (rotateAndFlip && rot < 4)) {
            int degree = 90 * rot++;
            byte[] photodnaRot = PhotoDNATransforms.rotate(photodna, degree, flip);
            
            List<byte[]> neighbors = vptree.getAllWithinDistance(photodnaRot, MAX_DISTANCE);
            
            byte[] nearest = null;
            int min_dist = Integer.MAX_VALUE;
            for(byte[] neighbor : neighbors) {
                int dist = photoDNAComparator.compare(neighbor, photodnaRot);
                if(dist < min_dist) {
                    min_dist = dist;
                    nearest = neighbor;
                }
            }
            if(nearest != null) {
                evidence.setExtraAttribute(PHOTO_DNA_KFF_HIT, "true");
                evidence.setExtraAttribute(PHOTO_DNA_KFF_DIST, min_dist);
                evidence.setExtraAttribute(PHOTO_DNA_KFF_HASH, new String(Hex.encodeHex(nearest, false)));
                break;
            }
            if(rot == 4 && !flip) {
                rot = 0;
                flip = true;
            }
        }
    }
    
    private static byte[] getBytes(String hash) throws DecoderException {
        return Hex.decodeHex(hash.toCharArray());
    }

}
