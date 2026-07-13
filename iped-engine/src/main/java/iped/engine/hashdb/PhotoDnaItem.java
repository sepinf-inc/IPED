package iped.engine.hashdb;

import iped.utils.HashValue;

public class PhotoDnaItem extends HashValue {
    private static final long serialVersionUID = 6803131766291128252L;
    private final int hashId;
    private transient int refSqDist;

    public PhotoDnaItem(int hashId, byte[] hash) {
        super(hash);
        this.hashId = hashId;
    }

    public int getHashId() {
        return hashId;
    }

    public void setRefSqDist(int refSqDist) {
        this.refSqDist = refSqDist;
    }

    public int getRefSqDist() {
        return refSqDist;
    }

    public int sqDistance(PhotoDnaItem o) {
        int ret = 0;
        byte[] a = getBytes();
        byte[] b = o.getBytes();
        for (int i = 0; i < a.length; i++) {
            int d = (a[i] & 255) - (b[i] & 255);
            ret += d * d;
        }
        return ret;
    }
}