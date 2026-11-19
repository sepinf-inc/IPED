package iped.parsers.whatsapp;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProtoBufDecoder {
    private static final long lmask = (1L << 32) - 1;

    class Part {
        private final int idx, type, level;
        private final Object value;
        private final byte[] raw;

        public Part(int idx, int type, Object value, int level, byte[] raw) {
            this.idx = idx;
            this.type = type;
            this.value = value;
            this.level = level;
            this.raw = raw;
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

        @SuppressWarnings("unchecked")
        public Part getChild(int idx) {
            if (value instanceof List<?>) {
                List<Part> l = (List<Part>) value;
                for (Part p : l) {
                    if (p.getIdx() == idx) {
                        return p;
                    }
                }
            }
            return null;
        }

        public byte[] getBytes() {
            return getBytes(false);
        }

        public byte[] getBytes(boolean force) {
            if (value instanceof byte[]) {
                return (byte[]) value;
            }
            if (force && raw != null) {
                return raw;
            }
            return null;
        }

        public String getString() {
            return getString(false);
        }

        public String getString(boolean force) {
            if (value instanceof String) {
                return (String) value;
            }
            if (force && raw != null) {
                try {
                    return new String(raw, StandardCharsets.UTF_8);
                } catch (Exception e) {
                }
            }
            return null;
        }

        public Double getDouble() {
            if (value instanceof Long) {
                return Double.longBitsToDouble((Long) value);
            }
            return null;
        }

        public Integer getInteger() {
            if (value instanceof Integer) {
                return ((Integer) value).intValue();
            }
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString());
                } catch (Exception e) {
                }
            }
            return null;
        }

        @Override
        public String toString() {
            String v = value instanceof byte[] ? Arrays.toString((byte[]) value) : value.toString();
            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            for (int i = 0; i < level; i++) {
                sb.append("   ");
            }
            sb.append("[idx=").append(idx);
            sb.append(", type=").append(type);
            sb.append(", value=").append(v).append("]");
            return sb.toString();
        }
    }

    private int pos, level;
    private final byte[] bytes;

    public ProtoBufDecoder(byte[] bytes) {
        this.bytes = bytes;
    }

    private ProtoBufDecoder(byte[] bytes, int level) {
        this.bytes = bytes;
        this.level = level;
    }

    public List<Part> decode() {
        if (bytes == null) {
            return null;
        }
        try {
            pos = 0;
            skipHeader();
            List<Part> l = new ArrayList<Part>();
            int prev = -1;
            while (left() > 0) {
                int okPos = pos;
                int idxType = Integer.parseInt(readVarInt());
                int type = idxType & 0b111;
                int idx = idxType >> 3;
                if (idx < prev) {
                    return null;
                }
                prev = idx;
                Object value = null;
                byte[] raw = null;
                if (type == 0) {
                    value = readVarInt();
                } else if (type == 2) {
                    int len = Integer.parseInt(readVarInt());
                    if (pos + len > bytes.length) {
                        return null;
                    }
                    raw = Arrays.copyOfRange(bytes, pos, pos + len);
                    pos += len;
                    value = readBuffer(raw);
                } else if (type == 5) {
                    value = readInt32LE();
                } else if (type == 1) {
                    long v1 = readInt32LE() & lmask;
                    long v2 = readInt32LE() & lmask;
                    value = v1 | (v2 << 32);
                } else {
                    pos = okPos;
                    break;
                }
                Part part = new Part(idx, type, value, level, raw);
                l.add(part);
            }
            if (left() == 0) {
                return l;
            }
        } catch (Exception e) {
        }
        return null;
    }

    public Part decode(int idx) {
        List<Part> l = decode();
        if (l != null) {
            for (Part p : l) {
                if (p.getIdx() == idx) {
                    return p;
                }
            }
        }
        return null;
    }

    private Object readBuffer(byte[] raw) {
        try {
            List<Part> l = new ProtoBufDecoder(raw, level + 1).decode();
            if (l != null) {
                return l;
            }
        } catch (Exception e) {
        }
        try {
            String s = new String(raw, StandardCharsets.UTF_8);
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            if (Arrays.equals(b, raw)) {
                return s;
            }
        } catch (Exception e) {
        }
        return raw;
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

    public static String findString(List<Part> l, int idx) {
        if (l != null) {
            for (Part p : l) {
                if (p.getIdx() == idx) {
                    return p.getString(true);
                }
            }
        }
        return null;
    }

    public static List<Part> findChilds(List<Part> l, int idx) {
        if (l != null) {
            for (Part p : l) {
                if (p.getIdx() == idx) {
                    return p.getChilds();
                }
            }
        }
        return null;
    }
}
