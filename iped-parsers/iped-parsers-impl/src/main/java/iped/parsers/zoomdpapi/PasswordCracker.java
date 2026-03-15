package iped.parsers.zoomdpapi;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Dictionary-based password cracker for DPAPI master key hashes.
 * Accepts a list of passwords (instead of a file path) for
 * compatibility with IPED's evidence tree model.
 *
 * Supports local (context=1), domain pre-1607 (context=2),
 * and domain post-1607 (context=3) DPAPI contexts.
 *
 * @author Calil Khalil (Hakal)
 */
public class PasswordCracker {

    public String crack(String hashLine, List<String> passwords) {
        DPAPIHash hash = DPAPIHash.parse(hashLine);
        if (hash == null) return null;

        for (String password : passwords) {
            if (tryPassword(hash, password)) return password;
        }
        return null;
    }

    boolean tryPassword(DPAPIHash hash, String password) {
        try {
            byte[] pwdHash = derivePwdHash(hash, password);
            byte[] decryptionKey = deriveDecryptionKey(pwdHash, hash.sid);
            byte[] decrypted = decrypt(hash, decryptionKey);

            if (decrypted == null) return false;

            byte[] hmacSalt = Arrays.copyOfRange(decrypted, 0, 16);
            byte[] hmacExpected = Arrays.copyOfRange(decrypted, 16, 16 + hash.getHashDigestLength());
            byte[] key = Arrays.copyOfRange(decrypted, decrypted.length - 64, decrypted.length);

            return Arrays.equals(hmacExpected, computeDPAPIHmac(hash, decryptionKey, hmacSalt, key));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] derivePwdHash(DPAPIHash hash, String password) throws Exception {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_16LE);

        switch (hash.context) {
            case 1: return MessageDigest.getInstance("SHA-1").digest(passwordBytes);
            case 2: return md4(passwordBytes);
            case 3:
                byte[] ntlm = md4(passwordBytes);
                byte[] sidBytes = hash.sid.getBytes(StandardCharsets.UTF_16LE);
                byte[] d1 = pbkdf2(ntlm, sidBytes, 32, 10000, "sha256");
                return pbkdf2(d1, sidBytes, 16, 1, "sha256");
            default: throw new Exception("Unknown context");
        }
    }

    private byte[] deriveDecryptionKey(byte[] pwdHash, String sid) throws Exception {
        byte[] sidBytes = (sid + "\0").getBytes(StandardCharsets.UTF_16LE);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(pwdHash, "HmacSHA1"));
        return mac.doFinal(sidBytes);
    }

    private byte[] decrypt(DPAPIHash hash, byte[] derivedKey) throws Exception {
        int keyLen = hash.getCipherKeyLength() / 8;
        int ivLen = hash.getIVLength() / 8;

        byte[] derived = pbkdf2(derivedKey, hash.salt, keyLen + ivLen, (int) hash.rounds, hash.hashAlgo);
        byte[] encKey = Arrays.copyOfRange(derived, 0, keyLen);
        byte[] iv = Arrays.copyOfRange(derived, keyLen, keyLen + ivLen);

        Cipher cipher = hash.cipherAlgo.startsWith("aes")
            ? Cipher.getInstance("AES/CBC/NoPadding")
            : Cipher.getInstance("DESede/CBC/NoPadding");

        String alg = hash.cipherAlgo.startsWith("aes") ? "AES" : "DESede";
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, alg), new IvParameterSpec(iv));
        return cipher.doFinal(hash.ciphertext);
    }

    private byte[] computeDPAPIHmac(DPAPIHash hash, byte[] pwdHash, byte[] hmacSalt, byte[] key) throws Exception {
        String hmacAlgo = "Hmac" + hash.hashAlgo.substring(0, 1).toUpperCase() + hash.hashAlgo.substring(1);

        Mac mac1 = Mac.getInstance(hmacAlgo);
        mac1.init(new SecretKeySpec(pwdHash, hmacAlgo));

        Mac mac2 = Mac.getInstance(hmacAlgo);
        mac2.init(new SecretKeySpec(mac1.doFinal(hmacSalt), hmacAlgo));
        return mac2.doFinal(key);
    }

    private byte[] pbkdf2(byte[] password, byte[] salt, int keyLength, int iterations, String hashAlgo) throws Exception {
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

    static byte[] md4(byte[] input) {
        int[] state = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476};
        int msgLen = input.length;
        int padLen = (msgLen % 64 < 56) ? (56 - msgLen % 64) : (120 - msgLen % 64);
        byte[] padded = new byte[msgLen + padLen + 8];
        System.arraycopy(input, 0, padded, 0, msgLen);
        padded[msgLen] = (byte) 0x80;

        long bitLen = (long) msgLen * 8;
        for (int i = 0; i < 8; i++) padded[padded.length - 8 + i] = (byte) (bitLen >>> (i * 8));

        int[] X = new int[16];
        for (int offset = 0; offset < padded.length; offset += 64) {
            for (int i = 0; i < 16; i++) {
                X[i] = (padded[offset + i * 4] & 0xFF) |
                       ((padded[offset + i * 4 + 1] & 0xFF) << 8) |
                       ((padded[offset + i * 4 + 2] & 0xFF) << 16) |
                       ((padded[offset + i * 4 + 3] & 0xFF) << 24);
            }

            int A = state[0], B = state[1], C = state[2], D = state[3];

            A = r1(A,B,C,D,X[0],3);  D = r1(D,A,B,C,X[1],7);  C = r1(C,D,A,B,X[2],11);  B = r1(B,C,D,A,X[3],19);
            A = r1(A,B,C,D,X[4],3);  D = r1(D,A,B,C,X[5],7);  C = r1(C,D,A,B,X[6],11);  B = r1(B,C,D,A,X[7],19);
            A = r1(A,B,C,D,X[8],3);  D = r1(D,A,B,C,X[9],7);  C = r1(C,D,A,B,X[10],11); B = r1(B,C,D,A,X[11],19);
            A = r1(A,B,C,D,X[12],3); D = r1(D,A,B,C,X[13],7); C = r1(C,D,A,B,X[14],11); B = r1(B,C,D,A,X[15],19);

            A = r2(A,B,C,D,X[0],3);  D = r2(D,A,B,C,X[4],5);  C = r2(C,D,A,B,X[8],9);   B = r2(B,C,D,A,X[12],13);
            A = r2(A,B,C,D,X[1],3);  D = r2(D,A,B,C,X[5],5);  C = r2(C,D,A,B,X[9],9);   B = r2(B,C,D,A,X[13],13);
            A = r2(A,B,C,D,X[2],3);  D = r2(D,A,B,C,X[6],5);  C = r2(C,D,A,B,X[10],9);  B = r2(B,C,D,A,X[14],13);
            A = r2(A,B,C,D,X[3],3);  D = r2(D,A,B,C,X[7],5);  C = r2(C,D,A,B,X[11],9);  B = r2(B,C,D,A,X[15],13);

            A = r3(A,B,C,D,X[0],3);  D = r3(D,A,B,C,X[8],9);  C = r3(C,D,A,B,X[4],11);  B = r3(B,C,D,A,X[12],15);
            A = r3(A,B,C,D,X[2],3);  D = r3(D,A,B,C,X[10],9); C = r3(C,D,A,B,X[6],11);  B = r3(B,C,D,A,X[14],15);
            A = r3(A,B,C,D,X[1],3);  D = r3(D,A,B,C,X[9],9);  C = r3(C,D,A,B,X[5],11);  B = r3(B,C,D,A,X[13],15);
            A = r3(A,B,C,D,X[3],3);  D = r3(D,A,B,C,X[11],9); C = r3(C,D,A,B,X[7],11);  B = r3(B,C,D,A,X[15],15);

            state[0] += A; state[1] += B; state[2] += C; state[3] += D;
        }

        byte[] result = new byte[16];
        for (int i = 0; i < 4; i++) {
            result[i*4]   = (byte) state[i];
            result[i*4+1] = (byte) (state[i] >>> 8);
            result[i*4+2] = (byte) (state[i] >>> 16);
            result[i*4+3] = (byte) (state[i] >>> 24);
        }
        return result;
    }

    private static int r1(int a, int b, int c, int d, int x, int s) {
        a += ((b & c) | (~b & d)) + x;
        return (a << s) | (a >>> (32 - s));
    }

    private static int r2(int a, int b, int c, int d, int x, int s) {
        a += ((b & c) | (b & d) | (c & d)) + x + 0x5A827999;
        return (a << s) | (a >>> (32 - s));
    }

    private static int r3(int a, int b, int c, int d, int x, int s) {
        a += (b ^ c ^ d) + x + 0x6ED9EBA1;
        return (a << s) | (a >>> (32 - s));
    }
}
