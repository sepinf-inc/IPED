package iped.parsers.whatsapp;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProtoBufDecoder {
    class Part {
        private final int idx, type;
        private final Object value;

        public Part(int idx, int type, Object value) {
            this.idx = idx;
            this.type = type;
            this.value = value;
        }

        public int getIdx() {
            return idx;
        }

        public int getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

        @SuppressWarnings("unchecked")
        public List<Part> getChilds() {
            if (value instanceof List<?>) {
                return (List<Part>) value;
            }
            return null;
        }

        @Override
        public String toString() {
            return "[idx=" + idx + ", type=" + type + ", value=" + value + "]";
        }
    }

    private int pos;
    private final byte[] bytes;

    public ProtoBufDecoder(byte[] bytes) {
        this.bytes = bytes;
    }

    public List<Part> decode() {
        try {
            pos = 0;
            skipHeader();
            List<Part> l = new ArrayList<Part>();
            while (left() > 0) {
                int okPos = pos;
                int idxType = Integer.parseInt(readVarInt());
                int type = idxType & 0b111;
                int idx = idxType >> 3;
                Object value = null;
                if (type == 0) {
                    value = readVarInt();
                } else if (type == 2) {
                    int len = Integer.parseInt(readVarInt());
                    value = readBuffer(len);
                } else if (type == 5) {
                    value = readInt32LE();
                } else if (type == 1) {
                    long v1 = readInt32LE();
                    long v2 = readInt32LE();
                    value = v1 | (v2 << 32);
                } else {
                    pos = okPos;
                    break;
                }
                Part part = new Part(idx, type, value);
                l.add(part);
            }
            if (left() == 0) {
                return l;
            }
        } catch (Exception e) {
        }
        return null;
    }

    private Object readBuffer(int len) {
        byte[] res = new byte[len];
        System.arraycopy(bytes, pos, res, 0, len);
        pos += len;
        try {
            List<Part> l = new ProtoBufDecoder(res).decode();
            if (l != null) {
                return l;
            }
        } catch (Exception e) {
        }
        try {
            return new String(res, StandardCharsets.UTF_8);
        } catch (Exception e) {
        }
        return res;
    }

    private int readInt32BE() {
        int ret = ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8)
                | ((bytes[pos + 3] & 0xFF) << 0);
        pos += 4;
        return ret;
    }

    private int readInt32LE() {
        int ret = ((bytes[pos] & 0xFF) << 0) | ((bytes[pos + 1] & 0xFF) << 8) | ((bytes[pos + 2] & 0xFF) << 16)
                | ((bytes[pos + 3] & 0xFF) << 24);
        pos += 4;
        return ret;
    }

    private String readVarInt() {
        BigInteger res = BigInteger.ZERO;
        int shift = 0;
        int b = 0;
        do {
            b = bytes[pos++] & 0xFF;
            res = res.add(BigInteger.valueOf(b & 0x7f).shiftLeft(shift));
            shift += 7;
        } while (b >= 0x80);
        return res.toString();
    }

    private void skipHeader() {
        int bak = pos;
        if (bytes[pos] == 0 && left() >= 5) {
            pos++;
            int len = readInt32BE();
            if (len > left()) {
                pos = bak;
            }
        }
    }

    private int left() {
        return bytes.length - pos;
    }
}
