package iped.parsers.emule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class KnownMetDecoder {
    private static int MAX_SIZE = 1 << 28;
    private static final long dataMin = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 365 * 30; // About -30 years
    private static final long dataMax = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 5; // About +5 years

    public static List<KnownMetEntry> parseToList(InputStream is) throws IOException {
        return parseToList(is, MAX_SIZE, false);
    }

    public static List<KnownMetEntry> parseToList(InputStream is, int maxSize, boolean checkConstraints) throws IOException {
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
        if (numFiles < 0) {
            return null;
        }

        int pos = 5;

        while (pos < b.length) {
            KnownMetEntry entry = new KnownMetEntry();
            int len = parseEntry(entry, pos, b, checkConstraints);
            if (len <= 0) {
                pos++;
                continue;
            }
            pos += len;
            l.add(entry);
        }
        return l;
    }

    public static int parseEntry(KnownMetEntry entry, int offset, byte[] b, boolean checkConstraints) {
        int pos = offset;
        long ms = toInt(b, pos) * 1000L;
        if (checkConstraints && (ms < dataMin || ms > dataMax))
            return 0;
        Date date = new Date(ms);
        entry.setLastModified(date);
        pos += 4;

        String hash = toHex(b, pos, 16);
        if (hash == null)
            return -1;
        entry.setHash(hash);
        pos += 16;

        int numParts = toSmall(b, pos);
        if (checkConstraints && (numParts > 4096 || numParts < 0))
            return -2;

        pos += 2;
        pos += 16 * numParts;

        int numTags = toInt(b, pos);
        if (checkConstraints && (numTags < 0 || numTags > 1024))
            return -3;

        // For some files numTags is zero (or one), but the parser will try to read at least 
        // the first two tags, which should be the file name and its size. 
        if (numTags < 2)
            numTags = 2;
        
        pos += 4;

        for (int j = 0; j < numTags; j++) {
            int tagType = toByte(b, pos);
            pos++;
            int tagId = 0;
            if ((tagType & 0x80) != 0) {
                tagId = toByte(b, pos);
                tagType &= 0x7F;
                pos++;
            } else {
                int len = toSmall(b, pos);
                pos += 2;
                if (len == 1)
                    tagId = toByte(b, pos);
                pos += len;
            }
            String strVal = null;
            int iVal = 0;
            long lVal = 0;
            if (tagType == 1) {
                pos += 16;
            } else if (tagType == 2) {
                int slen = toSmall(b, pos);
                if (slen < 0 || slen > 1024)
                    return -4;
                pos += 2;
                strVal = toStr(b, pos, slen);
                if (strVal == null)
                    return -5;
                pos += slen;
            } else if (tagType == 3) {
                iVal = toInt(b, pos);
                pos += 4;
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
            } else {
                return -6;
            }

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
            return -7;
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

    public static final String toStr(byte[] b, int offset, int length) {
        if (offset + length > b.length)
            return null;
        if (toByte(b, offset) == 0xEF && toByte(b, offset + 1) == 0xBB && toByte(b, offset + 2) == 0xBF) {
            Charset utf8 = Charset.forName("UTF-8");
            StringBuilder sb = new StringBuilder();
            int last = offset + 3;
            for (int i = offset + 3; i < offset + length; i++) {
                int a0 = toByte(b, i);
                if (a0 == 0xcc && toByte(b, i + 1) == 0x81 && i > offset + 3) {
                    char a = (char) toByte(b, i - 1);
                    char v = 0;
                    if (a == 'a')
                        v = 'á';
                    else if (a == 'e')
                        v = 'é';
                    else if (a == 'i')
                        v = 'í';
                    else if (a == 'o')
                        v = 'ó';
                    else if (a == 'u')
                        v = 'ú';
                    else if (a == 'c')
                        v = 'ç';
                    else if (a == 'A')
                        v = 'Á';
                    else if (a == 'E')
                        v = 'É';
                    else if (a == 'I')
                        v = 'Í';
                    else if (a == 'O')
                        v = 'Ó';
                    else if (a == 'U')
                        v = 'Ú';
                    else if (a == 'C')
                        v = 'Ç';
                    if (v != 0) {
                        sb.append(new String(b, last, i - last - 1, utf8));
                        sb.append(v);
                        i++;
                        last = i + 1;
                    }
                } else if (a0 == 0xC3) {
                    int ignore = 1;
                    for (int j = i + 1; j < offset + length; j++) {
                        int a2 = toByte(b, j + 1);
                        if (a2 == 0xC2) {
                            ignore++;
                            continue;
                        }
                        int a1 = toByte(b, j);
                        if (a1 == 0x82 || a1 == 0x83 || a1 == 0xC2 || a1 == 0xC3 || a1 == 0xC6 || a1 == 0x92
                                || a1 == 0x3F) {
                            ignore++;
                            continue;
                        }
                        if (ignore >= 3) {
                            sb.append(new String(b, last, i - last, utf8));
                            sb.append(new String(new byte[] { (byte) a0, (byte) a1 }, utf8));
                            i = j;
                            last = i + 1;
                        }
                        break;
                    }
                }
            }
            sb.append(new String(b, last, offset + length - last, utf8));
            return sb.toString();
        }
        return new String(b, offset, length, Charset.forName("ISO8859_1"));
    }
}
