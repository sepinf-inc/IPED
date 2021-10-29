package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.FileUtils;

/**
 *
 * @author PCF HAUCK
 */
public class DecryptFile {
    private byte[] iv;
    private byte[] cipherKey;
    private byte[] hmacKey;
    private byte[] file;
    private byte[] decripted;
    private File f;

    public DecryptFile(byte[] iv, byte[] cipherKey, File f) {
        this.iv = iv;
        this.cipherKey = cipherKey;
        this.f = f;
    }

    public void readEncFile() throws Exception {
        file = FileUtils.readFileToByteArray(f);
        if (file.length == 0) {
            throw new Exception("Empty File");
        }
        file = Arrays.copyOfRange(file, 0, file.length - 10);
    }

    public byte[] decrypt(String ext) throws Exception {
        readEncFile();
        IvParameterSpec iv = new IvParameterSpec(this.iv);
        SecretKeySpec skeySpec = new SecretKeySpec(cipherKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
        decripted = cipher.doFinal(file);

        FileUtils.writeByteArrayToFile(f, decripted);
        return decripted;

    }

}
