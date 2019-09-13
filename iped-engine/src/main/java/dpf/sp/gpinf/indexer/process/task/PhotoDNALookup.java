package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Properties;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private static ArrayList<byte[]> photoDNAHashSet = new ArrayList<>();
    
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
        
        if(taskEnabled && photoDNAHashSet.isEmpty()) {
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
                    while((line = bf.readLine()) != null) {
                        String hash = line.split(",")[1];
                        photoDNAHashSet.add(getBytes(hash));
                    }
                }
            }
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
        if(hashStr == null || photoDNAHashSet.isEmpty())
            return;
        
        byte[] photodna = getBytes(hashStr);
        int min_dist = Integer.MAX_VALUE;
        byte[] nearestNeighbor = null;
        for(byte[] hash : photoDNAHashSet) {
            int dist = photoDNAComparator.compare(hash, photodna);
            if(dist < min_dist) {
                min_dist = dist;
                nearestNeighbor = hash;
            }
            if(dist <= MAX_DISTANCE) {
                evidence.setExtraAttribute(PHOTO_DNA_KFF_HIT, "true");
                break;
            }
        }
        evidence.setExtraAttribute(PHOTO_DNA_KFF_DIST, min_dist);
        evidence.setExtraAttribute(PHOTO_DNA_KFF_HASH, new String(Hex.encodeHex(nearestNeighbor, false)));
                
    }
    
    private static byte[] getBytes(String hash) throws DecoderException {
        return Hex.decodeHex(hash.toCharArray());
    }

}
