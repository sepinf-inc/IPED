package iped.parsers.android.backup;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.compress.PackageParser;

/**
 * Parses and extracts content from android backup files done with ADB. This
 * work was based on code from
 * https://github.com/nelenkov/android-backup-extractor.
 * 
 * @author Patrick Dalla Bernardina
 * 
 */
public class AndroidBackupParser extends PackageParser {
    private static final MediaType AB_MIMETYPE = MediaType.application("x-android-backup");

    public static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(AB_MIMETYPE);

    private static final Object MAGIC = "ANDROID BACKUP";

    private static final String AB_PREFIX = "androidBackup";
    private static final String ENC_METADATA = "encryption";

    private static final String COMPRESSION_METADATA = "compression";

    private static final String AB_VERSION_METADATA = "version";

    private static final int MASTER_KEY_SIZE = 256;

    private static final int PBKDF2_SALT_SIZE = 512;

    private static final String ENCRYPTION_MECHANISM = "AES/CBC/PKCS5Padding";

    private static final int PBKDF2_KEY_SIZE = 256;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream in, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try {
            String magic = readLine(in);
            if (!magic.equals(MAGIC)) {
                throw new TikaException("Invalid android backup magic.");
            }

            int version = Integer.parseInt(readLine(in));

            if (version < 1 || version > 5) {
                throw new TikaException("Invalid android backup version:" + version + ".");
            }
            metadata.set(AB_PREFIX + ":" + AB_VERSION_METADATA, Integer.toString(version));

            boolean isCompressed = Integer.parseInt(readLine(in)) == 1;
            
            String encryption = readLine(in);
            metadata.set(AB_PREFIX + ":" + ENC_METADATA, encryption);

            if (encryption.equals("SHA-256")) {
                try {
                    in = unencrypt(in, version, null);
                } catch (Exception e) {
                    throw new TikaException(e.getMessage(), e);
                }
                if (in == null) {
                    throw new TikaException("Invalid password or master key checksum.");
                }
            }

            Inflater inflater;
            if (isCompressed) {
                inflater = new Inflater();
                in = new InflaterInputStream(in, inflater);
            }
            
            try {
                super.parse(in, handler, metadata, context);
            }finally {
            }


        } catch (TikaException te) {
            throw te;
        } catch (Exception e) {
            new TikaException(e.getMessage(), e);
        }

    }

    private InputStream unencrypt(InputStream in, int version, String password)
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        if (Cipher.getMaxAllowedKeyLength("AES") < MASTER_KEY_SIZE) {
            System.err.println("WARNING: Maximum allowed key-length seems smaller than needed. "
                    + "Please check that unlimited strength cryptography is available, see README.md for details");
        }

        if (password == null || "".equals(password)) {
            Console console = System.console();
            if (console != null) {
                System.err.println("This backup is encrypted, please provide the password");
                password = new String(console.readPassword("Password: "));
            } else {
                throw new IllegalArgumentException("Backup encrypted but password not specified");
            }
        }

        String userSaltHex = readLine(in); // 5
        byte[] userSalt = hexToByteArray(userSaltHex);
        if (userSalt.length != PBKDF2_SALT_SIZE / 8) {
            throw new IllegalArgumentException("Invalid salt length: " + userSalt.length);
        }

        String ckSaltHex = readLine(in); // 6
        byte[] ckSalt = hexToByteArray(ckSaltHex);

        int rounds = Integer.parseInt(readLine(in)); // 7
        String userIvHex = readLine(in); // 8

        String masterKeyBlobHex = readLine(in); // 9

        // decrypt the master key blob
        Cipher c = Cipher.getInstance(ENCRYPTION_MECHANISM);
        // XXX we don't support non-ASCII passwords
        SecretKey userKey = buildPasswordKey(password, userSalt, rounds, false);
        byte[] IV = hexToByteArray(userIvHex);
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(userKey.getEncoded(), "AES"), ivSpec);
        byte[] mkCipher = hexToByteArray(masterKeyBlobHex);
        byte[] mkBlob = c.doFinal(mkCipher);

        // first, the master key IV
        int offset = 0;
        int len = mkBlob[offset++];
        IV = Arrays.copyOfRange(mkBlob, offset, offset + len);
        offset += len;
        // then the master key itself
        len = mkBlob[offset++];
        byte[] mk = Arrays.copyOfRange(mkBlob, offset, offset + len);
        offset += len;
        // and finally the master key checksum hash
        len = mkBlob[offset++];
        byte[] mkChecksum = Arrays.copyOfRange(mkBlob, offset, offset + len);

        // now validate the decrypted master key against the checksum
        // first try the algorithm matching the archive version
        boolean useUtf = version >= 2;
        byte[] calculatedCk = makeKeyChecksum(mk, ckSalt, rounds, useUtf);
        System.err.printf("Calculated MK checksum (use UTF-8: %s): %s\n", useUtf, toHex(calculatedCk));
        if (!Arrays.equals(calculatedCk, mkChecksum)) {
            System.err.println("Checksum does not match.");
            // try the reverse
            calculatedCk = makeKeyChecksum(mk, ckSalt, rounds, !useUtf);
            System.err.printf("Calculated MK checksum (use UTF-8: %s): %s\n", useUtf, toHex(calculatedCk));
        }

        if (Arrays.equals(calculatedCk, mkChecksum)) {
            ivSpec = new IvParameterSpec(IV);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(mk, "AES"), ivSpec);
            // Only if all of the above worked properly will 'result' be
            // assigned
            return new CipherInputStream(in, c);
        }
        return null;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder buff = new StringBuilder();
        for (byte b : bytes) {
            buff.append(String.format("%02X", b));
        }

        return buff.toString();
    }

    public static byte[] makeKeyChecksum(byte[] pwBytes, byte[] salt, int rounds, boolean useUtf8) {
        char[] mkAsChar = new char[pwBytes.length];
        for (int i = 0; i < pwBytes.length; i++) {
            mkAsChar[i] = (char) pwBytes[i];
        }

        Key checksum = buildCharArrayKey(mkAsChar, salt, rounds, useUtf8);
        return checksum.getEncoded();
    }

    public static byte[] hexToByteArray(String digits) {
        final int bytes = digits.length() / 2;
        if (2 * bytes != digits.length()) {
            throw new IllegalArgumentException("Hex string must have an even number of digits");
        }

        byte[] result = new byte[bytes];
        for (int i = 0; i < digits.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(digits.substring(i, i + 2), 16);
        }
        return result;
    }

    private String readLine(InputStream in) throws IOException {
        StringBuffer buff = new StringBuffer();
        char b = (char) in.read();
        while (b != '\n') {
            buff.append(b);
            b = (char) in.read();
        }

        return buff.toString();
    }

    public static SecretKey buildCharArrayKey(char[] pwArray, byte[] salt, int rounds, boolean useUtf8) {
        // Original code from BackupManagerService
        // this produces different results when run with Sun/Oracale Java SE
        // which apparently treats password bytes as UTF-8 (16?)
        // (the encoding is left unspecified in PKCS#5)

        // try {
        // SecretKeyFactory keyFactory = SecretKeyFactory
        // .getInstance("PBKDF2WithHmacSHA1");
        // KeySpec ks = new PBEKeySpec(pwArray, salt, rounds, PBKDF2_KEY_SIZE);
        // return keyFactory.generateSecret(ks);
        // } catch (InvalidKeySpecException e) {
        // throw new RuntimeException(e);
        // } catch (NoSuchAlgorithmException e) {
        // throw new RuntimeException(e);
        // } catch (NoSuchProviderException e) {
        // throw new RuntimeException(e);
        // }
        // return null;

        return androidPBKDF2(pwArray, salt, rounds, useUtf8);
    }

    public static SecretKey androidPBKDF2(char[] pwArray, byte[] salt, int rounds, boolean useUtf8) {
        PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
        // Android treats password bytes as ASCII, which is obviously
        // not the case when an AES key is used as a 'password'.
        // Use the same method for compatibility.

        // Android 4.4 however uses all char bytes
        // useUtf8 needs to be true for KitKat
        byte[] pwBytes = useUtf8 ? PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(pwArray)
                : PBEParametersGenerator.PKCS5PasswordToBytes(pwArray);
        generator.init(pwBytes, salt, rounds);
        KeyParameter params = (KeyParameter) generator.generateDerivedParameters(PBKDF2_KEY_SIZE);

        return new SecretKeySpec(params.getKey(), "AES");
    }

    private static SecretKey buildPasswordKey(String pw, byte[] salt, int rounds, boolean useUtf8) {
        return buildCharArrayKey(pw.toCharArray(), salt, rounds, useUtf8);
    }
}
