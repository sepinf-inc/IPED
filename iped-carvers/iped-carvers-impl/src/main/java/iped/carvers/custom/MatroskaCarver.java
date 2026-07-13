package iped.carvers.custom;

import java.io.IOException;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.utils.IOUtil;

public class MatroskaCarver extends DefaultCarver {
    private static final long[] DESCRIPTORS = { 0L, 0x80L, 0x4000L, 0x200000L, 0x10000000L, 0x0800000000L,
            0x040000000000L, 0x02000000000000L, 0x0100000000000000L, };
    private static final long[] VALUES = { 0L, 0x7fL, 0x3fffL, 0x1fffffL, 0x0fffffffL, 0x07ffffffffL, 0x03ffffffffffL,
            0x01ffffffffffffL, 0x00ffffffffffffffL, };
    private static final String[] keys = { "webm", "matroska" };

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        SeekableInputStream is = null;
        try {
            is = parentEvidence.getSeekableInputStream();
            is.seek(header.getOffset());
            byte[] bytes = is.readNBytes(256);
            if (!check(bytes)) {
                return -1;
            }
            long offset = 0;
            for (int i = 0; i < 4; i++) {
                if ((offset = decode(bytes, i % 2 == 1, offset)) == -1) {
                    break;
                }
            }
            Long parentLen = parentEvidence.getLength();
            if (parentLen != null && header.getOffset() + offset > parentLen) {
                offset = parentLen - header.getOffset();
            }
            return offset;
        } catch (IOException e) {
        } finally {
            IOUtil.closeQuietly(is);
        }
        return -1;
    }

    private static boolean check(byte[] bytes) {
        for (String key : keys) {
            NEXT: for (int i = 0; i < bytes.length - key.length(); i++) {
                for (int j = 0; j < key.length(); j++) {
                    if ((bytes[i + j] & 0xFF) != key.charAt(j)) {
                        continue NEXT;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static long decode(byte[] bytes, boolean skip, long offset) {
        if (offset >= bytes.length || offset < 0) {
            return -1;
        }
        int descLen = bytes[(int) (offset++)] & 0xff;
        if (descLen == 0) {
            return -1;
        }
        int mask = 0x80;
        long encVal = descLen;
        int encLen = 1;
        while ((descLen & mask) != mask) {
            mask >>= 1;
            if (offset >= bytes.length) {
                return -1;
            }
            encVal = (encVal << 8) | (bytes[(int) (offset++)] & 0xff);
            encLen++;
        }
        if (encLen < 0 || encLen > 8 || (encVal & VALUES[encLen]) != (encVal ^ DESCRIPTORS[encLen])) {
            return -1;
        }
        if (skip) {
            offset += encVal & VALUES[encLen];
        }
        return offset;
    }
}
