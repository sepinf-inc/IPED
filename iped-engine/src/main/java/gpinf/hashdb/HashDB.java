package gpinf.hashdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HashDB {
    public static final String[] hashTypes = new String[] {"MD5","SHA1","SHA256","SHA512","EDONKEY"};
    public static final int[] hashBytesLen = new int[] {16,20,32,64,16};

    public static byte[] hashStrToBytes(String s, int len) throws RuntimeException {
        if (s.length() == 0) {
            return null;
        }
        if (s.length() != len << 1) {
            throw new RuntimeException("Invalid hash length: " + s);
        }
        byte[] ret = new byte[s.length() >>> 1];
        for (int i = 0; i < s.length(); i += 2) {
            int a = val(s.charAt(i));
            int b = val(s.charAt(i + 1));
            if (a < 0) throw new RuntimeException("Invalid hash value: " + s);
            ret[i >>> 1] = (byte) ((a << 4) | b);
        }
        return ret;
    }

    public static final String hashBytesToStr(byte[] bytes) {
        final char[] tos = "0123456789ABCDEF".toCharArray();
        char[] c = new char[bytes.length << 1];
        int k = 0;
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            c[k++] = tos[b >>> 4];
            c[k++] = tos[b & 15];
        }
        return new String(c);
    }

    private static final int val(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c >= 'a' && c <= 'z') return c - 'a' + 10;
        return -1;
    }

    public static int hashType(String col) {
        if (col.indexOf("-") > 0) col = col.replace("-", "");
        for (int i = 0; i < hashTypes.length; i++) {
            if (col.equalsIgnoreCase(hashTypes[i])) return i;
        }
        return -1;
    }

    public static String toStr(Set<String> set) {
        String[] c = new String[set.size()];
        int i = 0;
        for (String a : set) {
            c[i++] = a;
        }
        Arrays.sort(c);
        StringBuilder sb = new StringBuilder();
        for (String a : c) {
            if (sb.length() > 0) sb.append("|");
            sb.append(a);
        }
        return sb.toString();
    }

    public static Set<String> toSet(String val) {
        Set<String> set = new HashSet<String>();
        String[] c = val.split("\\|");
        for (String a : c) {
            set.add(a);
        }
        return set;
    }

    public static List<String> toList(String val) {
        String[] c = val.split("\\|");
        List<String> l = new ArrayList<String>(c.length);
        for (String a : c) {
            l.add(a);
        }
        Collections.sort(l);
        return l;
    }

    public static boolean containsIgnoreCase(String propertyValue, String find) {
        if (propertyValue.equalsIgnoreCase(find)) return true;
        String[] c = propertyValue.split("\\|");
        for (String a : c) {
            if (a.equalsIgnoreCase(find)) return true;
        }
        return false;
    }

    public static String mergeProperties(String a, String b) {
        if (a.equals(b)) return a;
        Set<String> all = toSet(a);
        all.addAll(toSet(b));
        return toStr(all);
    }
}