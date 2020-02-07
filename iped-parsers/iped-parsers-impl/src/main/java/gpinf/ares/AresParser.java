package gpinf.ares;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AresParser {

    private static int MAX_SIZE = 1 << 27;

    public static List<AresEntry> parseToList(InputStream is) throws IOException {
        List<AresEntry> l = new ArrayList<AresEntry>();
        byte[] block = new byte[1 << 20];
        byte[] b = new byte[0];
        while (true) {
            int read = is.read(block);
            if (read <= 0)
                break;
            byte[] aux = new byte[b.length + read];
            System.arraycopy(b, 0, aux, 0, b.length);
            System.arraycopy(block, 0, aux, b.length, read);
            b = aux;
            if (b.length >= MAX_SIZE)
                throw new IOException("File is too large"); //$NON-NLS-1$
        }

        if (b.length < 23)
            return null;

        String signature = toStr(b, 0, 14);
        if (signature.equals("__ARESDB1.02H_")) { //$NON-NLS-1$
            parseShareH(b, l);
        } else if (signature.equals("__ARESDB1.04L_")) { //$NON-NLS-1$
            parseShareL(b, l);
        } else {
            System.err.println(
                    "Incorrect Signature: no '__ARESDB1.02H_' (ShareH.dat) neighter '__ARESDB1.04L_' (ShareL.dat)."); //$NON-NLS-1$
            l = null;
        }

        return l;
    }

    private static void parseShareH(byte[] b, List<AresEntry> l) {
        int pos = 14;
        while (pos < b.length) {
            AresEntry entry = new AresEntry();
            byte[] d = decode(b, pos, 23, true);
            pos += 23;

            entry.setHash(toHex(d, 0, 20));
            entry.setShared(toByte(d, 20) == 1);
            int lun = toSmall(d, 21);
            if (lun == 0)
                continue;
            if (lun > 1024)
                break;

            d = decode(b, pos, lun, false);
            pos += lun;
            parseEntry(d, entry);
            l.add(entry);
        }
    }

    private static void parseShareL(byte[] b, List<AresEntry> l) {
        int pos = 14;
        while (pos < b.length) {
            AresEntry entry = new AresEntry();
            byte[] d = decode(b, pos, 47, true);
            pos += 47;

            entry.setHash(toHex(d, 0, 20));
            int lun = toSmall(d, 45);
            if (lun == 0)
                continue;
            if (lun > 2048)
                break;

            entry.setMimeType(String.valueOf(toByte(d, 20)));
            entry.setShared(true);
            entry.setSize(toLong(d, 25));

            d = decode(b, pos, lun, false);
            pos += lun;
            parseEntry(d, entry);
            l.add(entry);
        }
    }

    private static void parseEntry(byte[] d, AresEntry entry) {
        int p = 0;
        while (true) {
            if (p + 3 > d.length)
                break;
            int tipo = d[p++];
            int detail = toSmall(d, p);
            p += 2;
            String s = toStr(d, p, detail);

            switch (tipo) {
                case 1:
                    entry.setPath(s);
                    break;
                case 2:
                    entry.setTitle(s);
                    break;
                case 3:
                    entry.setArtist(s);
                    break;
                case 4:
                    entry.setAlbum(s);
                    break;
                case 5:
                    entry.setCategory(s);
                    break;
                case 6:
                    entry.setYear(s);
                    break;
                case 7:
                    entry.setVdInfo(s);
                    break;
                case 8:
                    entry.setLanguage(s);
                    break;
                case 9:
                    entry.setUrl(s);
                    break;
                case 10:
                    entry.setComment(s);
                    break;
                case 11:
                    entry.setDate(new Date(toInt(d, p) * 1000L));
                    break;
                case 17:
                    entry.setCorrupted(true);
                    break;
                case 18:
                    entry.setHashOfPHash(s);
                    break;
            }
            p += detail;
        }
    }

    private static byte[] decode(byte[] b, int offset, int len, boolean hdr) {
        int a = 13871;
        if (!hdr)
            a++;
        byte[] ret = new byte[len];
        for (int i = 0; i < len; i++) {
            int c = b[i + offset] & 0xFF;
            ret[i] = (byte) ((c ^ (a >>> 8)) & 0xFF);
            a = (a + c) * 23219 + 36126;
        }
        return ret;
    }

    private static final int toSmall(byte[] b, int offset) {
        if (offset + 1 >= b.length)
            return -1;
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8);
    }

    private static final int toInt(byte[] b, int offset) {
        if (offset + 3 >= b.length)
            return -1;
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8) | ((b[offset + 2] & 0XFF) << 16)
                | ((b[offset + 3] & 0XFF) << 24);
    }

    private static final long toLong(byte[] b, int offset) {
        if (offset + 7 >= b.length)
            return -1;
        return (b[offset] & 0XFFL) | ((b[offset + 1] & 0XFFL) << 8) | ((b[offset + 2] & 0XFFL) << 16)
                | ((b[offset + 3] & 0XFFL) << 24) | ((b[offset + 4] & 0XFFL) << 32) | ((b[offset + 5] & 0XFFL) << 40)
                | ((b[offset + 6] & 0XFFL) << 48) | ((b[offset + 7] & 0XFFL) << 56);
    }

    private static final int toByte(byte[] b, int offset) {
        if (offset < 0 || offset >= b.length)
            return -1;
        return b[offset] & 0XFF;
    }

    private static final String toHex(byte[] b, int offset, int length) {
        if (offset + length >= b.length)
            return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String s = Integer.toHexString(b[offset + i] & 0XFF);
            if (s.length() == 1)
                sb.append('0');
            sb.append(s);
        }
        return sb.toString();
    }

    private static final String toStr(byte[] b, int offset, int length) {
        if (offset + length >= b.length)
            return null;
        return new String(b, offset, length, Charset.forName("UTF-8")); //$NON-NLS-1$
    }
}