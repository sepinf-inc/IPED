package iped.parsers.plist;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.dd.plist.NSNumber;
import com.dd.plist.UID;

public class PListHelper {

    private static final long THIRTY_YEARS_IN_SECONDS = TimeUnit.DAYS.toSeconds(365 * 30);
    private static final long TWO_YEARS_IN_SECONDS = TimeUnit.DAYS.toSeconds(365 * 2);

    protected static final String METADATA_KEY_SEPARATOR = ":";

    protected static final String ARR = "array";
    protected static final String DATA = "data";
    protected static final String DATE = "date";
    protected static final String DICT = "dict";
    protected static final String KEY = "key";
    protected static final String NUMBER = "number";
    protected static final String PLIST = "plist";
    protected static final String SET = "set";
    protected static final String STRING = "string";
    protected static final String UID = "uid";

    public static int getUIDInteger(UID uid) {
        byte[] b = new byte[4];
        byte[] b2 = uid.getBytes();
        for (int i = b.length - 1, j = b2.length - 1; i >= 0 && j >= 0; i--, j--) {
            b[i] = b2[j];
        }
        ByteBuffer wrapped = ByteBuffer.wrap(b);
        return wrapped.getInt();
    }

    public static Date getPossibleDate(NSNumber number) {
        try {
            long timeSeconds = Long.parseLong(number.stringValue());
            long nowSeconds = System.currentTimeMillis() / 1000;

            // converts 30 years to now and 2 years from now timestamps
            if (timeSeconds > (nowSeconds - THIRTY_YEARS_IN_SECONDS) && timeSeconds < (nowSeconds + TWO_YEARS_IN_SECONDS)) {
                return new Date(timeSeconds * 1000);
            }
        } catch (NumberFormatException ignore) {
        }
        return null;
    }

    public static String appendPath(String path, String entry) {
        return path.isBlank() ? entry : path + ":" + entry;
    }
}
