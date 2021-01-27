package iped3.exception;

import java.io.IOException;

public class ZipBombException extends IOException {

    public static final int MAX_COMPRESSION = 100;
    public static final int ZIPBOMB_MIN_SIZE = 1024 * 1024 * 1024;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public ZipBombException(String msg) {
        super(msg);
    }

    public static boolean isZipBomb(Long parentSize, long subitemSize) throws ZipBombException {
        if (parentSize != null && subitemSize >= ZIPBOMB_MIN_SIZE && subitemSize > parentSize * MAX_COMPRESSION) {
            return true;
        }
        return false;
    }

}
