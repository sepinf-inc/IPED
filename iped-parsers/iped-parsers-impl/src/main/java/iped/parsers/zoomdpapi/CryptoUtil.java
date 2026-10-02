package iped.parsers.zoomdpapi;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared cryptographic utilities for DPAPI operations.
 *
 * @author Calil Khalil (Hakal)
 */
class CryptoUtil {

    static final int CALG_SHA1 = 0x8004;
    static final int CALG_SHA256 = 0x800c;
    static final int CALG_SHA384 = 0x800d;
    static final int CALG_SHA512 = 0x800e;

    static final int CALG_3DES = 0x6603;
    static final int CALG_AES_128 = 0x660e;
    static final int CALG_AES_192 = 0x660f;
    static final int CALG_AES_256 = 0x6610;

    static byte[] pbkdf2(byte[] password, byte[] salt, int keyLength, int iterations, String hashAlgo) throws Exception {
        String hmacAlgo = "Hmac" + hashAlgo.substring(0, 1).toUpperCase() + hashAlgo.substring(1);
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int block = 1;

        while (buff.size() < keyLength) {
            ByteBuffer counter = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(block++);
            byte[] U = new byte[salt.length + 4];
            System.arraycopy(salt, 0, U, 0, salt.length);
            System.arraycopy(counter.array(), 0, U, salt.length, 4);

            Mac mac = Mac.getInstance(hmacAlgo);
            mac.init(new SecretKeySpec(password, hmacAlgo));
            byte[] derived = mac.doFinal(U);

            for (int r = 0; r < iterations - 1; r++) {
                mac.init(new SecretKeySpec(password, hmacAlgo));
                byte[] next = mac.doFinal(derived);
                for (int j = 0; j < derived.length; j++) derived[j] ^= next[j];
            }
            buff.write(derived);
        }
        return Arrays.copyOf(buff.toByteArray(), keyLength);
    }

    static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
