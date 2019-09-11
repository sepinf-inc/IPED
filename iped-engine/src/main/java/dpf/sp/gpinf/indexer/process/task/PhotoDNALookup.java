package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Properties;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import iped3.IItem;

public class PhotoDNALookup extends AbstractTask{
    
    private static final String photoDNAFilePath = "photoDNAFilePath";
    
    public static final String PHOTO_DNA_KFF_HIT = "photoDnaKffHit";
    
    public static final String PHOTO_DNA_KFF_DIST = "photoDnaKffDistance";
    
    public static final String PHOTO_DNA_KFF_HASH = "photoDnaKffHash";
    
    public static int MAX_DISTANCE = 50000;
    
    private static ArrayList<byte[]> photoDNAHashSet = new ArrayList<>();
    
    private static Comparator<byte[]> photoDNAComparator;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        loadDatabase(confParams);
    }
    
    @Override
    public boolean isEnabled() {
        return !photoDNAHashSet.isEmpty();
    }
    
    private static synchronized void loadDatabase(Properties confParams) throws IOException, DecoderException {
        try {
            photoDNAComparator = new br.dpf.sepinf.photodna.PhotoDNAComparator();
        }catch(NoClassDefFoundError e) {
            return;
        }
        
        if(photoDNAHashSet.isEmpty()) {
            String path = confParams.getProperty(photoDNAFilePath);
            if(path != null && !path.trim().isEmpty()) {
                try (BufferedReader bf = new BufferedReader(new FileReader(path.trim()))){
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
