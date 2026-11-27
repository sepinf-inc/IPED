package iped.parsers.whatsapp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class LinkDownloader {
    // signed URLs expire about 30 days after the message, so a larger value is not valid
    private static final long MAX_EXPIRATION_AHEAD = 90L * 24 * 60 * 60 * 1000;

    private static final Pattern EXPIRATION_PATTERN = Pattern.compile("[?&]oe=([0-9A-Fa-f]{1,15})(&|$)");

    private String urlStr;
    private String fileName;
    private byte[] cipherKey;
    private byte[] iv;
    private String hash;
    private int connTimeout;
    private int readTimeout;

    public String getUrl() {
        return urlStr;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * Signed media URLs have an expiration time (about 30 days after the message),
     * stored as seconds since epoch, in hexadecimal, in the "oe" parameter.
     * 
     * @return the expiration time in milliseconds, or null if the URL has none.
     */
    public Long getExpirationTime() {
        Matcher matcher = EXPIRATION_PATTERN.matcher(urlStr);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1), 16) * 1000L;
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Checks if the URL would be refused by the server because of its expiration
     * time: it is in the past ("URL signature expired") or too far in the future
     * ("URL Expiry out of range"), which happens in direct paths of old medias.
     */
    public boolean isExpired() {
        Long expiration = getExpirationTime();
        if (expiration == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return expiration < now || expiration > now + MAX_EXPIRATION_AHEAD;
    }

    public static String getSHA256(InputStream input) {
        if (input != null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                DigestInputStream digestInput = new DigestInputStream(new BufferedInputStream(input), digest);
                byte[] buffer = new byte[1024 * 8];
                while (digestInput.read(buffer) >= 0)
                    ;
                return getHex(digest.digest());
            } catch (NoSuchAlgorithmException | IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    class URLnotFound extends IOException {
        private static final long serialVersionUID = -6150655171948292830L;

        public URLnotFound() {
            super("URL not found");
        }
    }

    public void downloadUsingStream(File tmp) throws IOException {
        int status = -1;
        URL url = new URL(urlStr);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(connTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("HEAD");
            status = connection.getResponseCode();
        } catch (IOException e) {
            status = -1;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        if (status == 200) {
            FileUtils.copyURLToFile(url, tmp, connTimeout, readTimeout);
        } else {
            throw new URLnotFound();
        }
    }

    public LinkDownloader(String url, String hash, int connTimeout, int readTimeout, byte[] cipherKey, byte[] iv) {
        this.urlStr = url;
        this.hash = base64Decode(hash);
        this.connTimeout = connTimeout;
        this.readTimeout = readTimeout;
        setFileName(hash);

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

    public void decript(File Input, File dest) throws Exception {

        DecryptFile df = new DecryptFile(iv, cipherKey, Input);
        try (FileOutputStream out = new FileOutputStream(dest)) {

            df.decryptStream(out);

        } catch (Exception e) {
            throw new Exception("cipher error " + urlStr);
        }

        try (InputStream is = new FileInputStream(dest)) {
            String hash = getSHA256(is);
            if (!this.hash.equals(hash)) {
                throw new Exception("Invalid Hash");
            }
        }

    }

}