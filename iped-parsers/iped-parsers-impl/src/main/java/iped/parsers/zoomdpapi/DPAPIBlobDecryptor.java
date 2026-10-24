package iped.parsers.zoomdpapi;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decrypts DPAPI blobs containing the Zoom OSKEY.
 * Supports 3DES and AES ciphers with SHA-1/256/384/512 HMAC.
 *
 * @author Calil Khalil (Hakal)
 */
public class DPAPIBlobDecryptor {

    public byte[] decryptBlobFromBase64(String base64Blob, String masterKeyHex) throws Exception {
        return decryptBlob(Base64.getDecoder().decode(base64Blob), CryptoUtil.hexToBytes(masterKeyHex));
    }

    public byte[] decryptBlob(byte[] blobData, byte[] masterKey) throws Exception {
        DataReader reader = new DataReader(blobData);

        long version = reader.readDword();
        if (version != 1) {
            throw new Exception("Unsupported DPAPI blob version: " + version);
        }

        reader.skip(16); // provider GUID
        reader.readDword(); // mk version
        reader.skip(16); // mk GUID
        reader.readDword(); // flags

        long descLen = reader.readDword();
        if (descLen > 0 && descLen < 10000) reader.skip((int) descLen);

        long cipherAlgId = reader.readDword();
        reader.readDword(); // cipher key len (bits)

        long saltLen = reader.readDword();
        byte[] salt = reader.readBytes((int) saltLen);

        reader.readDword(); // hmac key len (strong)

        long hashAlgId = reader.readDword();
        reader.readDword(); // hash len bits
        long hmacLen = reader.readDword();
        reader.readBytes((int) hmacLen); // hmac

        long dataLen = reader.readDword();
        byte[] encryptedData = reader.readBytes((int) dataLen);

        byte[] sessionKey = deriveKey(masterKey, salt, hashAlgId, cipherAlgId);
        return decryptData(encryptedData, sessionKey, cipherAlgId);
    }

    private byte[] deriveKey(byte[] masterKey, byte[] salt, long hashAlgId, long cipherAlgId) throws Exception {
        byte[] dgstMasterKey = MessageDigest.getInstance("SHA-1").digest(masterKey);

        String hmacAlgo = getHmacAlgo(hashAlgId);
        Mac hmac = Mac.getInstance(hmacAlgo);
        hmac.init(new SecretKeySpec(dgstMasterKey, hmacAlgo));

        byte[] derived = hmac.doFinal(salt);
        int keyLength = getKeyLength(cipherAlgId);

        return derived.length > keyLength
            ? Arrays.copyOf(derived, keyLength)
            : derived;
    }

    private byte[] decryptData(byte[] encryptedData, byte[] key, long cipherAlgId) throws Exception {
        String algorithm;
        int blockSize;

        switch ((int) cipherAlgId) {
            case CryptoUtil.CALG_3DES:
                algorithm = "DESede/CBC/NoPadding"; blockSize = 8; break;
            case CryptoUtil.CALG_AES_128:
            case CryptoUtil.CALG_AES_192:
            case CryptoUtil.CALG_AES_256:
                algorithm = "AES/CBC/NoPadding"; blockSize = 16; break;
            default:
                throw new Exception("Unsupported cipher: 0x" + Long.toHexString(cipherAlgId));
        }

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(key, algorithm.split("/")[0]),
            new IvParameterSpec(new byte[blockSize]));

        return removePadding(cipher.doFinal(encryptedData));
    }

    private byte[] removePadding(byte[] data) {
        if (data.length == 0) return data;

        int padLen = data[data.length - 1] & 0xFF;
        if (padLen > 0 && padLen <= 16) {
            for (int i = 1; i <= padLen; i++) {
                if ((data[data.length - i] & 0xFF) != padLen) return data;
            }
            return Arrays.copyOf(data, data.length - padLen);
        }
        return data;
    }

    private String getHmacAlgo(long algId) {
        switch ((int) algId) {
            case CryptoUtil.CALG_SHA256: return "HmacSHA256";
            case CryptoUtil.CALG_SHA384: return "HmacSHA384";
            case CryptoUtil.CALG_SHA512: return "HmacSHA512";
            default: return "HmacSHA1";
        }
    }

    private int getKeyLength(long algId) {
        switch ((int) algId) {
            case CryptoUtil.CALG_3DES: return 24;
            case CryptoUtil.CALG_AES_128: return 16;
            case CryptoUtil.CALG_AES_192: return 24;
            default: return 32;
        }
    }
}
