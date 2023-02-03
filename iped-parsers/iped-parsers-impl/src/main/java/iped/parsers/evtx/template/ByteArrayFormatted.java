package iped.parsers.evtx.template;

public class ByteArrayFormatted {
    byte[] b;

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public ByteArrayFormatted(byte[] b) {
        this.b = b;
    }

    public String toString() {
        char[] hexChars = new char[b.length * 2];
        for (int j = 0; j < b.length; j++) {
            int v = b[j] & 0xFF;
            hexChars[(hexChars.length - 2) - (j * 2 - 1)] = HEX_ARRAY[v & 0x0F];
            hexChars[(hexChars.length - 2) - (j * 2)] = HEX_ARRAY[v >>> 4];
        }
        return "0x" + (new String(hexChars));
    }
}