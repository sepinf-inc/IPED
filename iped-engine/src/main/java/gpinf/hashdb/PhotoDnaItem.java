package gpinf.hashdb;

import iped3.IHashValue;

public class PhotoDnaItem {
    private final int hashId;
    private final IHashValue hash;

    public PhotoDnaItem(int hashId, IHashValue hash) {
        this.hash = hash;
        this.hashId = hashId;
    }

    public IHashValue getHash() {
        return hash;
    }

    public int getHashId() {
        return hashId;
    }
    
    public String toString() {
        return hash.toString();
    }
}
