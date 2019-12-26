package dpf.sp.gpinf.indexer.util;

import java.awt.image.BufferedImage;
import java.io.InputStream;

public interface PhotoDNA {
    
    public abstract int distance(byte[] hash1, byte[] hash2);
    
    public abstract byte[] computePhotoDNA(InputStream is) throws Exception;
    
    public abstract byte[] computePhotoDNA(BufferedImage img) throws Exception;
    
    public abstract void reset();

}
