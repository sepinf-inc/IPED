package dpf.sp.gpinf.indexer.util;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import iped3.IHashValue;

public class HashValue extends IHashValue {

    private static final long serialVersionUID = 1L;

    private final byte[] bytes;

    public HashValue(byte[] bytes) {
        this.bytes = bytes;
    }

    public HashValue(String hash) {
        try {
            this.bytes = Hex.decodeHex(hash.toCharArray());

        } catch (DecoderException e) {
            throw new IllegalArgumentException("Invalid hash string " + hash, e);
        }
    }

    public byte[] getBytes() {
        return bytes;
    }

}
