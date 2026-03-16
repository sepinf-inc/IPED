package iped.parsers.zoomdpapi;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Decrypts Windows DPAPI master keys using the user password and SID.
 * Implements PBKDF2 key derivation with SHA-1/256/384/512 and
 * supports 3DES/AES ciphers.
 *
 * @author Calil Khalil (Hakal)
 */
public class DPAPIMasterKeyDecryptor {


    public String decryptMasterKey(byte[] fileData, String sid, String password) throws Exception {
        DataReader reader = new DataReader(fileData);

        reader.readDword(); // version
        reader.skip(8);
        reader.readStringUtf16(72); // guid
        reader.skip(8);
        reader.readDword(); // policy

        long masterkeyLen = reader.readQword();
        reader.readQword(); // backupkeyLen
        reader.readQword(); // credhistLen
        reader.readQword(); // domainkeyLen

        if (masterkeyLen == 0) {
            throw new Exception("No master key data found in file");
        }

        DataReader mkReader = new DataReader(reader.readBytes((int) masterkeyLen));

        mkReader.skip(4); // version
        byte[] salt = mkReader.readBytes(16);
        long rounds = mkReader.readDword();
        long hashAlgId = mkReader.readDword();
        long cipherAlgId = mkReader.readDword();
        byte[] encryptedKey = mkReader.readBytes(mkReader.remaining());

        byte[] derivedKey = deriveKey(password, sid, salt, (int) rounds, hashAlgId, cipherAlgId);
        byte[] decryptedKey = decryptData(encryptedKey, derivedKey, cipherAlgId);

        if (decryptedKey.length < 100) {
            throw new Exception("Decrypted key is too short: " + decryptedKey.length + " bytes");
        }

        byte[] masterKey = Arrays.copyOfRange(decryptedKey, decryptedKey.length - 64, decryptedKey.length);
        return CryptoUtil.bytesToHex(masterKey);
    }

    private byte[] deriveKey(String password, String sid, byte[] salt, int rounds,
                            long hashAlgId, long cipherAlgId) throws Exception {

        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_16LE);
        byte[] pwdHash = MessageDigest.getInstance("SHA-1").digest(passwordBytes);

        String sidWithNull = sid + "\0";
        byte[] sidBytes = sidWithNull.getBytes(StandardCharsets.UTF_16LE);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(pwdHash, "HmacSHA1"));
        byte[] derivedKey = mac.doFinal(sidBytes);

        int keyLenBytes = getKeyLength(cipherAlgId) / 8;
        int ivLenBytes = getIVLength(cipherAlgId) / 8;

        return CryptoUtil.pbkdf2(derivedKey, salt, keyLenBytes + ivLenBytes, rounds, getHashAlgoName(hashAlgId));
    }

    private byte[] decryptData(byte[] encryptedData, byte[] derivedKey, long cipherAlgId) throws Exception {
        String algorithm;
        int keySize, ivSize;

        switch ((int) cipherAlgId) {
            case CryptoUtil.CALG_3DES:
                algorithm = "DESede/CBC/NoPadding"; keySize = 24; ivSize = 8; break;
            case CryptoUtil.CALG_AES_128:
                algorithm = "AES/CBC/NoPadding"; keySize = 16; ivSize = 16; break;
            case CryptoUtil.CALG_AES_192:
                algorithm = "AES/CBC/NoPadding"; keySize = 24; ivSize = 16; break;
            case CryptoUtil.CALG_AES_256:
                algorithm = "AES/CBC/NoPadding"; keySize = 32; ivSize = 16; break;
            default:
                throw new Exception("Unsupported cipher: 0x" + Long.toHexString(cipherAlgId));
        }

        byte[] encKey = Arrays.copyOfRange(derivedKey, 0, keySize);
        byte[] iv = Arrays.copyOfRange(derivedKey, keySize, keySize + ivSize);

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(encKey, algorithm.split("/")[0]),
            new IvParameterSpec(iv));
        return cipher.doFinal(encryptedData);
    }

    private int getKeyLength(long algId) {
        switch ((int) algId) {
            case CryptoUtil.CALG_3DES: return 192;
            case CryptoUtil.CALG_AES_128: return 128;
            case CryptoUtil.CALG_AES_192: return 192;
            default: return 256;
        }
    }

    private int getIVLength(long algId) {
        return (int) algId == CryptoUtil.CALG_3DES ? 64 : 128;
    }

    private String getHashAlgoName(long algId) {
        switch ((int) algId) {
            case CryptoUtil.CALG_SHA1: return "sha1";
            case CryptoUtil.CALG_SHA256: return "sha256";
            case CryptoUtil.CALG_SHA384: return "sha384";
            case CryptoUtil.CALG_SHA512: return "sha512";
            default: return "sha1";
        }
    }
}
