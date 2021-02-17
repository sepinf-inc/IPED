package dpf.inc.sepinf.UsnJrnl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class Util {
    public static int readInt16(InputStream in) throws IOException {
        return readInt16(in, false);
    }

    public static long readInt32(InputStream in) throws IOException {
        return readInt32(in, false);
    }

    public static long readInt64(InputStream in) throws IOException {
        return readInt64(in, false);
    }

    public static int readInt16(InputStream in, boolean bigEndian) throws IOException {

        int b1 = in.read(), b2 = in.read();
        b1 &= 0xFF;
        b2 &= 0xFF;
        int r = 0;
        if (!bigEndian) {
            r = b1 | (b2 << 8);
        } else {
            r = b2 | (b1 << 8);
        }
        return r;
       
    }

    public static long readInt32(InputStream in, boolean bigEndian) throws IOException {
        
        long i = 0;
        byte len = 4;
        byte[] b = IOUtils.readFully(in, len);
        for (int j = 0; j < len; j++) {
            long a = b[j];
            a = a & 0xFF;

            if (!bigEndian) {
                i |= (a << (j * 8));
            } else {
                i |= (a << ((len - j - 1) * 8));
            }
        }
        return i;
        

    }

    public static long readInt64(InputStream in, boolean bigEndian) throws IOException {
      
        long i = 0;
        byte len = 8;
        byte[] b = IOUtils.readFully(in, len);
        for (int j = 0; j < len; j++) {
            long a = b[j];
            a = a & 0xFF;
            if (!bigEndian) {
                i |= (a << (j * 8L));
            } else {
                i |= (a << ((len - j - 1) * 8L));
            }
        }
        return i;
      
    }

    public static String readString(InputStream in, int len) throws IOException {
        
        byte[] b = IOUtils.readFully(in, len);
        return new String(b, StandardCharsets.UTF_16LE);

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
