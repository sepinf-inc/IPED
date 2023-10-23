package iped.parsers.whatsapp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author PCF HAUCK
 */
public class DecryptFile {
    private byte[] iv;
    private byte[] cipherKey;

    private File f;

    public DecryptFile(byte[] iv, byte[] cipherKey, File f) {
        this.iv = iv;
        this.cipherKey = cipherKey;
        this.f = f;
    }



    public void decrypt(OutputStream fout) throws Exception {

        IvParameterSpec iv = new IvParameterSpec(this.iv);
        SecretKeySpec skeySpec = new SecretKeySpec(cipherKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
        byte file[] = FileUtils.readFileToByteArray(f);
        file = Arrays.copyOfRange(file, 0, file.length - 10);
        byte b[] = cipher.doFinal(file);

        IOUtils.copy(new ByteArrayInputStream(b), fout);


    }

    public void decryptStream(OutputStream fout) throws Exception {

        IvParameterSpec iv = new IvParameterSpec(this.iv);
        SecretKeySpec skeySpec = new SecretKeySpec(cipherKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

        try (FileInputStream fin = new FileInputStream(f)) {
            byte[] buff = new byte[8 * 1024];
            int cont = 0, tot = 0;
            while ((cont = fin.read(buff)) >= 0) {

                if (tot + cont >= f.length() - 10) {
                    cont = (int) (f.length() - 10 - tot);
                }

                tot += cont;

                byte[] b = cipher.update(buff, 0, cont);
                fout.write(b);
                if (tot >= f.length() - 10) {
                    break;
                }
            }
            fout.write(cipher.doFinal());
        }



    }

}
