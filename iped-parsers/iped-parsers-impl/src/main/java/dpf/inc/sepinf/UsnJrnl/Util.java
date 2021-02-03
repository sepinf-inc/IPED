package dpf.inc.sepinf.UsnJrnl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Util {
    public static int readInt16(InputStream in) {
        return readInt16(in, true);
    }

    public static int readInt32(InputStream in) {
        return readInt32(in, true);
    }

    public static long readInt64(InputStream in) {
        return readInt64(in, true);
    }

    public static int readInt16(InputStream in, boolean bigEndian) {
        try {
            int b1 = in.read(), b2 = in.read();
            if (bigEndian) {
                return b1 + (b2 << 8);
            } else {
                return b2 + (b1 << 8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static int readInt32(InputStream in, boolean bigEndian) {
        try {
            int i = 0;
            byte len = 4;
            for (int j = 0; j < len; j++) {
                int a = in.read();

                if (bigEndian) {
                    i |= (a << (j * 8));
                } else {
                    i |= (a << ((len - j - 1) * 8));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static long readInt64(InputStream in, boolean bigEndian) {
        try {
            long i = 0;
            byte len = 8;
            for (int j = 0; j < len; j++) {
                long a = in.read();
                if (bigEndian) {
                    i |= (a << (j * 8L));
                } else {
                    i |= (a << ((len - j - 1) * 8L));
                }
            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static String readString(InputStream in, int len) {
        try {
            byte[] b = new byte[len];
            in.read(b, 0, len);
            return new String(b, StandardCharsets.UTF_16LE);
        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;
    }

    public static boolean zero(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

}
