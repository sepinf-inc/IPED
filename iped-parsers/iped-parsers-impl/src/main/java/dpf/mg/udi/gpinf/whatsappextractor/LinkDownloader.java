package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class LinkDownloader {
    private String urlStr;
    private String fileName;
    private byte[] cipherKey;
    private byte[] iv;
    private String ext;
    private String hash;



    public String getFileName() {
        return fileName;
    }


    public static String getSHA256(byte[] b) {
        if (b != null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] array = digest.digest(b);
                return getHex(array);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void downloadUsingStream( File tmp) throws IOException {
        
        URL url = new URL(urlStr);
        BufferedInputStream bis = new BufferedInputStream(url.openStream());

        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[1024];
            int count = 0;
            while ((count = bis.read(buffer, 0, 1024)) != -1) {
                out.write(buffer, 0, count);
            }
        }
        bis.close();

    }

    public LinkDownloader(String url, String ext, String hash, byte[] cipherKey, byte[] iv) {
        urlStr = url;
        this.hash = base64Decode(hash);
        setFileName(hash);
        this.ext = ext;
        this.cipherKey = cipherKey;
        this.iv = iv;
    }

    public String getHash() {
        return this.hash;
    }

    public void setFileName(String encHash) {
        String name = base64Decode(encHash);
        if (name == null) {
            fileName = null;
        } else {
            fileName = name.substring(0, 20);
        }
    }

    public static String getHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {

            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String base64Decode(String str) {
        try {
            byte[] b = Base64.getDecoder().decode(str);
            return getHex(b);
        } catch (Exception e) {
            return null;
        }

    }

    public void decript(File dest, Writer log) throws Exception {

        DecryptFile df = new DecryptFile(iv, cipherKey, dest);

        try {
            String hash = getSHA256(df.decrypt(ext));
        } catch (Exception e) {
            log.write(e.toString() + "\n");
            log.write("cippher key" + getHex(cipherKey) + "\n");
            log.write("IV" + getHex(iv) + "\n");
            throw new Exception(e.toString());
        }
        if (!this.hash.equals(hash)) {
            log.write("Erro hash do arquivo nao bate\n");
            log.write("banco:" + this.hash + "\n");
            log.write("Calculado:" + hash + "\n");
            throw new Exception("Hash invalido");

        }

    }

}