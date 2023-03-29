package iped.parsers.evtx.windows;

public class SID {
    byte[] b;
    String sid;

    public SID(byte[] b) {
        this.b = b;
    }

    @Override
    public String toString() {
        if (sid == null) {
            sid = binarySidToStringSid(this.b);
        }
        return sid;
    }

    public static String binarySidToStringSid(byte[] SID) {
        StringBuilder strSID = new StringBuilder("S-");

        // bytes[0] : in the array is the version (must be 1 but might
        // change in the future)
        strSID.append(SID[0]).append('-');

        // bytes[2..7] : the Authority
        StringBuilder tmpBuff = new StringBuilder();
        for (int t = 2; t <= 7; t++) {
            String hexString = Integer.toHexString(SID[t] & 0xFF);
            tmpBuff.append(hexString);
        }
        strSID.append(Long.parseLong(tmpBuff.toString(), 16));

        // bytes[1] : the sub authorities count
        int count = SID[1];

        // bytes[8..end] : the sub authorities (these are Integers - notice
        // the endian)
        for (int i = 0; i < count; i++) {
            int currSubAuthOffset = i * 4;
            tmpBuff.setLength(0);
            tmpBuff.append(String.format("%02X%02X%02X%02X", (SID[11 + currSubAuthOffset] & 0xFF), (SID[10 + currSubAuthOffset] & 0xFF), (SID[9 + currSubAuthOffset] & 0xFF), (SID[8 + currSubAuthOffset] & 0xFF)));

            strSID.append('-').append(Long.parseLong(tmpBuff.toString(), 16));
        }

        return strSID.toString();
    }

}
