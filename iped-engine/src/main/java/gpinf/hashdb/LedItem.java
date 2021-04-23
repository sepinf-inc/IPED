package gpinf.hashdb;

public class LedItem {
    private final long length;
    private final String md5;
    private final String ext;

    public LedItem(long length, String md5, String ext) {
        this.length = length;
        this.md5 = md5;
        this.ext = ext;
    }

    public long getLength() {
        return length;
    }

    public String getMD5() {
        return md5;
    }

    public String getExt() {
        return ext;
    }
}
