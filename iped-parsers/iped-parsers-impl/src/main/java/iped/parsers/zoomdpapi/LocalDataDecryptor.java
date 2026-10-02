package iped.parsers.zoomdpapi;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decrypts locally-stored Zoom data fields using AES/CBC
 * with a key derived from the Windows user SID.
 *
 * @author Calil Khalil (Hakal)
 */
public class LocalDataDecryptor {

    private final byte[] key;
    private final byte[] iv;

    public LocalDataDecryptor(String sid) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        this.key = sha256.digest(sid.getBytes(StandardCharsets.UTF_8));
        this.iv = Arrays.copyOf(sha256.digest(key), 16);
    }

    public String decrypt(String base64Data) {
        if (base64Data == null || base64Data.length() < 20) return base64Data;

        try {
            byte[] encrypted = Base64.getDecoder().decode(base64Data);
            if (encrypted.length % 16 != 0) return base64Data;

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return base64Data;
        }
    }

    public boolean canDecrypt(String base64Data) {
        if (base64Data == null || base64Data.length() < 20) return false;
        try {
            byte[] encrypted = Base64.getDecoder().decode(base64Data);
            return encrypted.length % 16 == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
