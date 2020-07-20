package gpinf.emule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class KnownMetParser {
    private static boolean DEBUG = false;
    private static int MAX_SIZE = 1 << 27;
    private static final long dataMin = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 365 * 20; // Aprox. -20 anos
    private static final long dataMax = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 5; // Aprox. +5 anos

    public static List<KnownMetEntry> parseToList(InputStream is) throws IOException {
        return parseToList(is, MAX_SIZE);
    }

    public static List<KnownMetEntry> parseToList(InputStream is, int maxSize) throws IOException {
        List<KnownMetEntry> l = new ArrayList<KnownMetEntry>();
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
            if (b.length >= maxSize)
                break;
        }

        if (b.length < 5)
            return null;
        int numFiles = toInt(b, 1);
        if (DEBUG)
            System.err.println("   " + numFiles); //$NON-NLS-1$
        if (numFiles < 0) {
            is.close();
            return null;
        }

        int pos = 5;
        for (int i = 0; i < numFiles; i++) {
            if (DEBUG)
                System.err.println("      " + i); //$NON-NLS-1$
            KnownMetEntry entry = new KnownMetEntry();
            int len = parseEntry(entry, pos, b);
            if (len <= 0) {
                pos++;
                continue;
            }
            pos += len;
            if (DEBUG)
                System.err.println(entry);
            l.add(entry);
        }
        is.close();
        return l;
    }

    private static int parseEntry(KnownMetEntry entry, int offset, byte[] b) {
        int pos = offset;
        long ms = toInt(b, pos) * 1000L;
        if (ms < dataMin || ms > dataMax)
            return 0;
        Date date = new Date(ms);
        entry.setLastModified(date);
        pos += 4;
        if (DEBUG)
            System.err.println("      " + date); //$NON-NLS-1$

        String hash = toHex(b, pos, 16);
        if (hash == null)
            return 0;
        entry.setHash(hash);
        pos += 16;
        if (DEBUG)
            System.err.println("      Hash=" + hash); //$NON-NLS-1$

        int numParts = toSmall(b, pos);
        if (numParts > 1024 || numParts < 0)
            return 0;

        if (DEBUG)
            System.err.println("      Parts=" + numParts); //$NON-NLS-1$
        pos += 2;
        pos += 16 * numParts;

        int numTags = toInt(b, pos);
        if (numTags < 0 || numTags > 255)
            return 0;
        if (DEBUG)
            System.err.println("      Tags=" + numTags); //$NON-NLS-1$
        pos += 4;
        if (numTags > 0 && DEBUG)
            System.err.println();

        for (int j = 0; j < numTags; j++) {
            if (DEBUG)
                System.err.println("          TagNum=" + j); //$NON-NLS-1$
            int tagType = toByte(b, pos);
            pos++;
            int tagId = 0;
            if (DEBUG)
                System.err.println("          TagType=" + tagType); //$NON-NLS-1$
            if ((tagType & 0x80) != 0) {
                tagId = toByte(b, pos);
                tagType &= 0x7F;
                pos++;
            } else {
                int len = toSmall(b, pos);
                pos += 2;
                if (len == 1)
                    tagId = toByte(b, pos);
                if (DEBUG)
                    System.err.println("          TagLen=" + len); //$NON-NLS-1$
                pos += len;
            }
            if (DEBUG)
                System.err.println("          TagID=" + Integer.toHexString(tagId)); //$NON-NLS-1$
            String strVal = null;
            int iVal = 0;
            long lVal = 0;
            if (tagType == 1) {
                pos += 16;
            } else if (tagType == 2) {
                int slen = toSmall(b, pos);
                if (slen < 0 || slen > 1024)
                    return 0;
                pos += 2;
                strVal = toStr(b, pos, slen);
                if (strVal == null)
                    return 0;
                pos += slen;
                if (DEBUG)
                    System.err.println("          Str=" + strVal); //$NON-NLS-1$
            } else if (tagType == 3) {
                iVal = toInt(b, pos);
                pos += 4;
                if (DEBUG)
                    System.err.println("          Int=" + iVal); //$NON-NLS-1$
            } else if (tagType == 4) {
                pos += 4;
            } else if (tagType == 5) {
                pos += 1;
            } else if (tagType == 7) {
                int slen = toInt(b, pos);
                pos += 4;
                pos += slen;
            } else if (tagType == 8) {
                pos += 2;
            } else if (tagType == 9) {
                pos += 1;
            } else if (tagType == 11) {
                lVal = toLong(b, pos);
                pos += 8;
                if (DEBUG)
                    System.err.println("          Long=" + lVal); //$NON-NLS-1$
            } else {
                if (DEBUG)
                    System.err.println("TagType desconhecido = " + tagType); //$NON-NLS-1$
                return 0;
            }
            if (DEBUG)
                System.err.println();

            if (tagId == 0x01) {
                entry.setName(strVal);
            } else if (tagId == 0x02) {
                if (entry.getFileSize() == -1)
                    entry.setFileSize(0);
                if (tagType != 11) {
                    lVal = iVal;
                    if (lVal < 0)
                        lVal += 1L << 32;
                }
                entry.setFileSize(entry.getFileSize() | lVal);
            } else if (tagId == 0x12) {
                entry.setPartName(strVal);
            } else if (tagId == 0x03) {
                entry.setFileType(strVal);
            } else if (tagId == 0x21) {
                entry.setLastPublishedKad(new Date((iVal - 5 * 60 * 60) * 1000L));// Subtrai 5 horas
            } else if (tagId == 0x34) {
                entry.setLastShared(new Date(iVal * 1000L));
            } else if (tagId == 0x3A) {
                if (entry.getFileSize() == -1)
                    entry.setFileSize(0);
                lVal = iVal;
                if (lVal < 0)
                    lVal += 1L << 32;
                entry.setFileSize(entry.getFileSize() | (lVal << 32));
            } else if (tagId == 0x50) {
                if (entry.getBytesTransfered() == -1)
                    entry.setBytesTransfered(0);
                entry.setBytesTransfered(entry.getBytesTransfered() | Long.parseLong(Integer.toBinaryString(iVal), 2));
            } else if (tagId == 0x51) {
                entry.setTotalRequests(iVal);
            } else if (tagId == 0x52) {
                entry.setAcceptedRequests(iVal);
            } else if (tagId == 0x54) {
                if (entry.getBytesTransfered() == -1)
                    entry.setBytesTransfered(0);
                lVal = iVal;
                if (lVal < 0)
                    lVal += 1L << 32;
                entry.setBytesTransfered(entry.getBytesTransfered() | (lVal << 32));
            }
        }
        if (entry.getName() == null)
            return 0;
        return pos - offset;
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

    private static final int toSmall(byte[] b, int offset) {
        if (offset + 1 >= b.length)
            return -1;
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8);
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
        if (toByte(b, offset) == 0xEF && toByte(b, offset + 1) == 0xBB && toByte(b, offset + 2) == 0xBF)
            return new String(b, offset + 3, length - 3, Charset.forName("UTF-8")); //$NON-NLS-1$
        return new String(b, offset, length, Charset.forName("ISO8859_1")); //$NON-NLS-1$
    }
}