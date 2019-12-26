package dpf.sp.gpinf.indexer.util;

public interface PhotoDNATransforms {
    
    public abstract byte[] rotate(byte[] hash, int degree, boolean flip);

}
