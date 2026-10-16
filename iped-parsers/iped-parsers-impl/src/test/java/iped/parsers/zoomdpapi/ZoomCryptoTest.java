package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Test;

/**
 * Unit tests for DataReader, LocalDataDecryptor, DPAPIBlobDecryptor,
 * and DPAPIMasterKeyDecryptor utility/crypto classes.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomCryptoTest {

    // --- DataReader tests ---

    @Test
    public void testDataReaderReadDword() {
        // 0x01000000 in little-endian = 1
        byte[] data = new byte[]{0x01, 0x00, 0x00, 0x00};
        DataReader reader = new DataReader(data);
        assertEquals(1L, reader.readDword());
        assertEquals(4, reader.getOffset());
    }

    @Test
    public void testDataReaderReadDwordLargeValue() {
        // 0xFF000000 in little-endian = 255
        byte[] data = new byte[]{(byte) 0xFF, 0x00, 0x00, 0x00};
        DataReader reader = new DataReader(data);
        assertEquals(255L, reader.readDword());
    }

    @Test
    public void testDataReaderReadQword() {
        byte[] data = new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        DataReader reader = new DataReader(data);
        assertEquals(1L, reader.readQword());
        assertEquals(8, reader.getOffset());
    }

    @Test
    public void testDataReaderReadBytes() {
        byte[] data = new byte[]{0x0A, 0x0B, 0x0C, 0x0D, 0x0E};
        DataReader reader = new DataReader(data);
        byte[] result = reader.readBytes(3);
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, result);
        assertEquals(3, reader.getOffset());
        assertEquals(2, reader.remaining());
    }

    @Test(expected = RuntimeException.class)
    public void testDataReaderBufferOverflow() {
        byte[] data = new byte[]{0x01, 0x02};
        DataReader reader = new DataReader(data);
        reader.readBytes(5);
    }

    @Test
    public void testDataReaderReadStringUtf16() {
        String test = "AB";
        byte[] utf16 = test.getBytes(StandardCharsets.UTF_16LE);
        DataReader reader = new DataReader(utf16);
        assertEquals("AB", reader.readStringUtf16(utf16.length));
    }

    @Test
    public void testDataReaderSkipAndRemaining() {
        byte[] data = new byte[20];
        DataReader reader = new DataReader(data);
        assertEquals(20, reader.remaining());
        assertTrue(reader.hasRemaining());

        reader.skip(10);
        assertEquals(10, reader.remaining());
        assertEquals(10, reader.getOffset());

        reader.skip(10);
        assertFalse(reader.hasRemaining());
    }

    // --- LocalDataDecryptor tests ---

    @Test
    public void testLocalDataDecryptorRoundTrip() throws Exception {
        String sid = "S-1-5-21-1234567890-1234567890-1234567890-1001";
        LocalDataDecryptor decryptor = new LocalDataDecryptor(sid);

        // Encrypt some data using the same key derivation
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] key = sha256.digest(sid.getBytes(StandardCharsets.UTF_8));
        byte[] iv = Arrays.copyOf(sha256.digest(key), 16);

        String plaintext = "Hello Zoom Forensics!";
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        String base64 = Base64.getEncoder().encodeToString(encrypted);

        assertEquals(plaintext, decryptor.decrypt(base64));
    }

    @Test
    public void testLocalDataDecryptorNullInput() throws Exception {
        LocalDataDecryptor decryptor = new LocalDataDecryptor("S-1-5-21-test");
        assertNull(decryptor.decrypt(null));
    }

    @Test
    public void testLocalDataDecryptorShortInput() throws Exception {
        LocalDataDecryptor decryptor = new LocalDataDecryptor("S-1-5-21-test");
        assertEquals("short", decryptor.decrypt("short"));
    }

    @Test
    public void testLocalDataDecryptorCanDecrypt() throws Exception {
        LocalDataDecryptor decryptor = new LocalDataDecryptor("S-1-5-21-test");
        assertFalse(decryptor.canDecrypt(null));
        assertFalse(decryptor.canDecrypt("short"));
        assertFalse(decryptor.canDecrypt("not-base64!!!@@@###"));
    }

    // --- DPAPIBlobDecryptor utility tests ---

    @Test
    public void testHexToBytes() {
        byte[] result = CryptoUtil.hexToBytes("0102ff");
        assertArrayEquals(new byte[]{0x01, 0x02, (byte) 0xFF}, result);
    }

    @Test
    public void testBytesToHex() {
        String hex = CryptoUtil.bytesToHex(new byte[]{0x01, 0x02, (byte) 0xFF});
        assertEquals("0102ff", hex);
    }

    @Test
    public void testHexRoundTrip() {
        byte[] original = new byte[]{0x00, 0x7F, (byte) 0x80, (byte) 0xFF};
        String hex = CryptoUtil.bytesToHex(original);
        byte[] restored = CryptoUtil.hexToBytes(hex);
        assertArrayEquals(original, restored);
    }

    @Test(expected = Exception.class)
    public void testDPAPIBlobDecryptorInvalidVersion() throws Exception {
        // version = 99 (invalid, should be 1)
        byte[] blob = new byte[100];
        blob[0] = 99;
        DPAPIBlobDecryptor decryptor = new DPAPIBlobDecryptor();
        decryptor.decryptBlob(blob, new byte[64]);
    }
}
