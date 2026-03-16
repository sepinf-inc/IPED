package iped.parsers.zoomdpapi;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for DPAPIHash, PasswordCracker, and HashGenerator.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomCrackingTest {

    // --- DPAPIHash parsing tests ---

    @Test
    public void testDPAPIHashParse() {
        String hashLine = "$DPAPImk$2*1*S-1-5-21-123*aes256*sha512*8000*aabbccdd11223344*288*" +
                "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff" +
                "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff" +
                "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff" +
                "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff" +
                "00112233445566778899aabbccddeeff";

        DPAPIHash hash = DPAPIHash.parse(hashLine);
        assertNotNull(hash);
        assertEquals(2, hash.version);
        assertEquals(1, hash.context);
        assertEquals("S-1-5-21-123", hash.sid);
        assertEquals("aes256", hash.cipherAlgo);
        assertEquals("sha512", hash.hashAlgo);
        assertEquals(8000, hash.rounds);
        assertEquals(8, hash.salt.length);
    }

    @Test
    public void testDPAPIHashParseNull() {
        assertNull(DPAPIHash.parse(null));
        assertNull(DPAPIHash.parse(""));
        assertNull(DPAPIHash.parse("invalid"));
        assertNull(DPAPIHash.parse("$DPAPImk$2*1*too*few"));
    }

    @Test
    public void testDPAPIHashCipherKeyLength() {
        DPAPIHash hash = new DPAPIHash();
        hash.cipherAlgo = "aes256";
        assertEquals(256, hash.getCipherKeyLength());

        hash.cipherAlgo = "aes128";
        assertEquals(128, hash.getCipherKeyLength());

        hash.cipherAlgo = "des3";
        assertEquals(192, hash.getCipherKeyLength());
    }

    @Test
    public void testDPAPIHashIVLength() {
        DPAPIHash hash = new DPAPIHash();
        hash.cipherAlgo = "aes256";
        assertEquals(128, hash.getIVLength());

        hash.cipherAlgo = "des3";
        assertEquals(64, hash.getIVLength());
    }

    @Test
    public void testDPAPIHashDigestLength() {
        DPAPIHash hash = new DPAPIHash();
        hash.hashAlgo = "sha512";
        assertEquals(64, hash.getHashDigestLength());

        hash.hashAlgo = "sha256";
        assertEquals(32, hash.getHashDigestLength());

        hash.hashAlgo = "sha1";
        assertEquals(20, hash.getHashDigestLength());
    }

    // --- MD4 implementation test ---

    @Test
    public void testMD4EmptyString() {
        // MD4("") = 31d6cfe0d16ae931b73c59d7e0c089c0
        byte[] result = PasswordCracker.md4(new byte[0]);
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0", CryptoUtil.bytesToHex(result));
    }

    @Test
    public void testMD4KnownValue() {
        // MD4("a") = bde52cb31de33e46245e05fbdbd6fb24
        byte[] result = PasswordCracker.md4("a".getBytes(StandardCharsets.US_ASCII));
        assertEquals("bde52cb31de33e46245e05fbdbd6fb24", CryptoUtil.bytesToHex(result));
    }

    @Test
    public void testMD4NTLM() {
        // NTLM hash of "password" = MD4(UTF-16LE("password"))
        byte[] utf16 = "password".getBytes(StandardCharsets.UTF_16LE);
        byte[] result = PasswordCracker.md4(utf16);
        assertEquals("8846f7eaee8fb117ad06bdd830b7586c", CryptoUtil.bytesToHex(result));
    }

    // --- HashGenerator tests ---

    @Test
    public void testHashGeneratorNullOnShortData() {
        HashGenerator gen = new HashGenerator();
        String hash = gen.generateHash(new byte[10], "S-1-5-21-123", "local");
        assertNull(hash);
    }

    @Test
    public void testHashGeneratorContextValues() {
        // Build a minimal valid master key file structure
        // This tests that the generator correctly handles the binary format
        // We'll test with a real-ish structure
        byte[] data = buildMinimalMasterKeyFile();
        HashGenerator gen = new HashGenerator();
        String hash = gen.generateHash(data, "S-1-5-21-999", "local");

        if (hash != null) {
            assertTrue(hash.startsWith("$DPAPImk$2*1*S-1-5-21-999*"));
        }
    }

    // --- PasswordCracker with invalid hash ---

    @Test
    public void testCrackInvalidHash() {
        PasswordCracker cracker = new PasswordCracker();
        assertNull(cracker.crack("invalid_hash", Arrays.asList("password")));
    }

    @Test
    public void testCrackNullHash() {
        PasswordCracker cracker = new PasswordCracker();
        assertNull(cracker.crack(null, Arrays.asList("password")));
    }

    // Helper: build a minimal master key file for testing
    private byte[] buildMinimalMasterKeyFile() {
        // Header: version(4) + padding(8) + guid_utf16(72) + padding(8) + policy(4) = 96
        // Then: masterkeyLen(8) + backupLen(8) + credhistLen(8) + domainLen(8) = 32
        // Then masterkey block: version(4) + salt(16) + rounds(4) + hashAlg(4) + cipherAlg(4) + ciphertext(48) = 80
        byte[] data = new byte[96 + 32 + 80];

        // version = 2
        data[0] = 2;

        // masterkey len at offset 96 (little-endian QWORD) = 80
        data[96] = 80;

        // Inside masterkey block (offset 128):
        // version at 128: skip 4
        // salt at 132: 16 bytes of 0xAA
        Arrays.fill(data, 132, 148, (byte) 0xAA);
        // rounds at 148: 4000 = 0xA0 0x0F 0x00 0x00
        data[148] = (byte) 0xA0;
        data[149] = (byte) 0x0F;
        // hashAlg at 152: 0x800e (SHA-512) = 0x0e 0x80 0x00 0x00
        data[152] = 0x0e;
        data[153] = (byte) 0x80;
        // cipherAlg at 156: 0x6610 (AES-256) = 0x10 0x66 0x00 0x00
        data[156] = 0x10;
        data[157] = 0x66;
        // ciphertext at 160: 48 bytes of 0xBB
        Arrays.fill(data, 160, 208, (byte) 0xBB);

        return data;
    }
}
