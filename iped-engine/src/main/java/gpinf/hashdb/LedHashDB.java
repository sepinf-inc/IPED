package gpinf.hashdb;

public class LedHashDB {
    private final byte[] md5_512;
    private final byte[] md5_64k;
    private final int[] hashIds;

    public LedHashDB(byte[] md5_512, byte[] md5_64k, int[] hashIds) {
        this.md5_512 = md5_512;
        this.md5_64k = md5_64k;
        this.hashIds = hashIds;
    }

    public byte[] getMD5_512() {
        return md5_512;
    }

    public byte[] getMD5_64K() {
        return md5_64k;
    }

    public int[] getHashIds() {
        return hashIds;
    }

    public int size() {
        return hashIds.length;
    }

    public boolean containsMD5_512(byte[] bytes) {
        if (bytes.length != 16) return false;
        return binarySearch(md5_512, bytes) >= 0;
    }

    public int hashIdFromMD5_64K(byte[] bytes) {
        if (bytes.length != 16) return -1;
        int pos = binarySearch(md5_64k, bytes);
        if (pos < 0) return -1;
        return hashIds[pos];
    }

    private static int binarySearch(byte[] arr, byte[] bytes) {
        int low = 0;
        int high = (arr.length >>> 4) - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = 0;
            int off = mid << 4;
            for (int i = 0; i < 16 && cmp == 0; i++) {
                cmp = Integer.compare(arr[off + i] & 255, bytes[i] & 255);
            }
            if (cmp == 0) return mid;
            if (cmp < 0) low = mid + 1;
            else high = mid - 1;
        }
        return -(low + 1);
    }
}
