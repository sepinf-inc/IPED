package iped.parsers.zoomdpapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

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

    public String crack(String hashLine, Iterable<String> passwords) {
        DPAPIHash hash = DPAPIHash.parse(hashLine);
        if (hash == null)
            return null;

        for (String password : passwords) {
            if (tryPassword(hash, password))
                return password;
        }
        return null;
    }

    boolean tryPassword(DPAPIHash hash, String password) {
        try {
            byte[] pwdHash = derivePwdHash(hash, password);
            byte[] decryptionKey = deriveDecryptionKey(pwdHash, hash.sid);
            byte[] decrypted = decrypt(hash, decryptionKey);

            if (decrypted == null)
                return false;

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
            case 1:
                return MessageDigest.getInstance("SHA-1").digest(passwordBytes);
            case 2:
                return MessageDigest.getInstance("MD4").digest(passwordBytes);
            case 3:
                byte[] ntlm = MessageDigest.getInstance("MD4").digest(passwordBytes);
                byte[] sidBytes = hash.sid.getBytes(StandardCharsets.UTF_16LE);
                byte[] d1 = CryptoUtil.pbkdf2(ntlm, sidBytes, 32, 10000, "sha256");
                return CryptoUtil.pbkdf2(d1, sidBytes, 16, 1, "sha256");
            default:
                throw new Exception("Unknown context");
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

        byte[] derived = CryptoUtil.pbkdf2(derivedKey, hash.salt, keyLen + ivLen, (int) hash.rounds, hash.hashAlgo);
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

}
