package iped.parsers.signal;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decryption of what Signal for Android keeps encrypted on the device: the SQLCipher
 * database and the attachment files under app_parts.
 */
public class SignalCrypto {

    // SqlCipherDatabaseHook in Signal-Android: cipher_compatibility = 3, kdf_iter = 1,
    // cipher_page_size = 4096. SQLCipher 3 uses PBKDF2-HMAC-SHA1 and HMAC-SHA1 per page.
    static final int PAGE_SIZE = 4096;
    private static final int KDF_ITERATIONS = 1;
    private static final int HMAC_KDF_ITERATIONS = 2;
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 16;
    private static final int HMAC_SIZE = 20;
    // IV and HMAC at the end of every page, rounded up to the AES block size
    private static final int RESERVE = (IV_SIZE + HMAC_SIZE + 15) / 16 * 16;
    private static final byte HMAC_SALT_MASK = 0x3a;
    // Pages tried before a key is taken for another extraction's: a key is not rejected
    // for one damaged page, as in a database recovered after deletion
    private static final int KEY_PROBE_PAGES = 16;

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private static final int WAL_HEADER_SIZE = 32;
    private static final int WAL_FRAME_HEADER_SIZE = 24;

    private SignalCrypto() {
    }

    /** True when the file starts with the plain SQLite header, i.e. it is not encrypted. */
    public static boolean isPlainSqlite(byte[] prefix) {
        return prefix.length >= SQLITE_HEADER.length
                && Arrays.equals(Arrays.copyOf(prefix, SQLITE_HEADER.length), SQLITE_HEADER);
    }

    /** Outcome of a database decryption. */
    public static class Result {
        private final int pages;
        private final int verified;

        Result(int pages, int verified) {
            this.pages = pages;
            this.verified = verified;
        }

        public int getPages() { return pages; }

        /** Pages whose HMAC matched; zero means the key does not belong to this database. */
        public int getVerified() { return verified; }
    }

    /**
     * Writes a plain SQLite copy of a Signal database. Committed frames of the write-ahead
     * log, which hold the most recent changes, are applied over the main file.
     *
     * @param databaseKey
     *            the "sqlite db key" of the extraction: the hex string Signal passes to
     *            SQLCipher as the passphrase
     * @param wal
     *            the encrypted -wal file, or null
     */
    public static Result decryptDatabase(File database, File wal, String databaseKey, File output)
            throws IOException {
        try (RandomAccessFile main = new RandomAccessFile(database, "r");
                RandomAccessFile log = wal != null && wal.length() > WAL_HEADER_SIZE ? new RandomAccessFile(wal, "r")
                        : null) {
            if (main.length() < PAGE_SIZE)
                return new Result(0, 0);

            byte[] salt = new byte[SALT_SIZE];
            main.readFully(salt);
            byte[] encryptionKey = pbkdf2(databaseKey.strip().getBytes(StandardCharsets.UTF_8), salt, KDF_ITERATIONS);
            byte[] hmacSalt = salt.clone();
            for (int i = 0; i < hmacSalt.length; i++)
                hmacSalt[i] ^= HMAC_SALT_MASK;
            byte[] hmacKey = pbkdf2(encryptionKey, hmacSalt, HMAC_KDF_ITERATIONS);

            long mainPages = main.length() / PAGE_SIZE;
            long pageCount = mainPages;
            // Offset in the log of the latest committed copy of each page
            Map<Integer, Long> walPages = new HashMap<>();
            if (log != null) {
                int walDatabasePages = readWal(log, walPages);
                if (walDatabasePages > 0)
                    pageCount = walDatabasePages;
            }

            int pages = 0, verified = 0;
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA1"));
            SecretKeySpec aesKey = new SecretKeySpec(encryptionKey, "AES");

            byte[] page = new byte[PAGE_SIZE];
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(output.toPath()))) {
                for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                    Long walOffset = walPages.get(pageNumber);
                    if (walOffset != null) {
                        log.seek(walOffset);
                        log.readFully(page);
                    } else if (pageNumber <= mainPages) {
                        main.seek((long) (pageNumber - 1) * PAGE_SIZE);
                        main.readFully(page);
                    } else {
                        out.write(new byte[PAGE_SIZE]);
                        continue;
                    }
                    pages++;
                    if (decryptPage(page, pageNumber, cipher, aesKey, mac, out))
                        verified++;
                    else if (verified == 0 && pages == KEY_PROBE_PAGES)
                        // A key from another extraction: no need to go through the rest
                        return new Result(pages, 0);
                }
            }
            return new Result(pages, verified);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static boolean decryptPage(byte[] page, int pageNumber, Cipher cipher, SecretKeySpec aesKey,
            Mac mac, OutputStream out) throws GeneralSecurityException, IOException {
        // The salt takes the place of the SQLite header on the first page
        int start = pageNumber == 1 ? SALT_SIZE : 0;
        int end = PAGE_SIZE - RESERVE;

        mac.update(page, start, end + IV_SIZE - start);
        mac.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pageNumber).array());
        byte[] expected = mac.doFinal();
        boolean ok = MessageDigest.isEqual(expected, Arrays.copyOfRange(page, end + IV_SIZE, end + IV_SIZE + HMAC_SIZE));

        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(page, end, IV_SIZE));
        byte[] body = cipher.doFinal(page, start, end - start);
        if (pageNumber == 1)
            out.write(SQLITE_HEADER);
        out.write(body);
        // The reserved area stays in the plain file too: the header still declares it
        out.write(new byte[RESERVE]);
        return ok;
    }

    /**
     * Finds the committed frames of a SQLCipher write-ahead log; frame headers are not
     * encrypted, only the pages. Returns the database size in pages after the last commit.
     */
    private static int readWal(RandomAccessFile wal, Map<Integer, Long> committed) throws IOException {
        byte[] header = new byte[WAL_HEADER_SIZE];
        wal.seek(0);
        wal.readFully(header);
        int magic = ByteBuffer.wrap(header).getInt(0);
        if (magic != 0x377F0682 && magic != 0x377F0683)
            return 0;
        byte[] salts = Arrays.copyOfRange(header, 16, 24);
        int frameSize = WAL_FRAME_HEADER_SIZE + PAGE_SIZE;
        Map<Integer, Long> pending = new HashMap<>();
        byte[] frameHeader = new byte[WAL_FRAME_HEADER_SIZE];
        int databasePages = 0;
        for (long offset = WAL_HEADER_SIZE; offset + frameSize <= wal.length(); offset += frameSize) {
            wal.seek(offset);
            wal.readFully(frameHeader);
            // A frame from an earlier generation of the log, left behind after a reset
            if (!Arrays.equals(Arrays.copyOfRange(frameHeader, 8, 16), salts))
                continue;
            ByteBuffer frame = ByteBuffer.wrap(frameHeader);
            int pageNumber = frame.getInt(0);
            int commitSize = frame.getInt(4);
            pending.put(pageNumber, offset + WAL_FRAME_HEADER_SIZE);
            // Only a commit frame makes the transaction durable; anything after the last
            // one was never committed and is left out
            if (commitSize != 0) {
                committed.putAll(pending);
                pending.clear();
                databasePages = commitSize;
            }
        }
        return databasePages;
    }

    /** PBKDF2 with HMAC-SHA1 producing a 32 byte key, over a raw byte password. */
    private static byte[] pbkdf2(byte[] password, byte[] salt, int iterations) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(password, "HmacSHA1"));
            ByteArrayOutputStream key = new ByteArrayOutputStream(40);
            for (int block = 1; key.size() < 32; block++) {
                mac.update(salt);
                byte[] u = mac.doFinal(ByteBuffer.allocate(4).putInt(block).array());
                byte[] t = u.clone();
                for (int i = 1; i < iterations; i++) {
                    u = mac.doFinal(u);
                    for (int j = 0; j < t.length; j++)
                        t[j] ^= u[j];
                }
                key.write(t);
            }
            return Arrays.copyOf(key.toByteArray(), 32);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /**
     * Decrypts an attachment file (ModernDecryptingPartInputStream in Signal-Android):
     * AES-CTR from a zero counter, with the key HMAC-SHA256(modernKey, data_random).
     */
    public static InputStream decryptAttachment(InputStream encrypted, byte[] modernKey, byte[] dataRandom)
            throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(modernKey, "HmacSHA256"));
            byte[] key = mac.doFinal(dataRandom);
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
            return new CipherInputStream(encrypted, cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
