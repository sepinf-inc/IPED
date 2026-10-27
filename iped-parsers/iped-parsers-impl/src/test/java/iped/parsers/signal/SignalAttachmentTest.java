package iped.parsers.signal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.BodyContentHandler;

import iped.data.IItemReader;
import iped.parsers.sqlite.detector.SQLiteContainerDetector;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.search.IItemSearcher;

/**
 * Encrypted databases and attachments.
 *
 * Fixtures, built outside this code base so that they do not share its reading of the
 * format: test_signal_encrypted.db was written by SQLCipher 4.12 with the PRAGMAs of
 * Signal-Android's SqlCipherDatabaseHook (cipher_compatibility 3, kdf_iter 1, 4096 byte
 * pages), from test_signal_attachments.db, which is test_signal.db plus an attachment
 * table. Its -wal holds message 30, which the main file does not have. The files in
 * test_signal_app_parts were encrypted as ModernEncryptingPartOutputStream does, and
 * the .plain files next to them are what they must decrypt to.
 *
 *   attachment 1, message 3 (sent):      part1001.mms, image
 *   attachment 2, message 16 (received): part1002.mms, contrato.pdf
 *   attachment 3, message 22 (received): voice note, part1003.mms not in the case
 *   attachment 4, message 23 (received): no file (never downloaded)
 *   attachments 5 and 6, messages 6 (received) and 7 (sent): the same part1004.mms
 *   attachment 7, message 21:            quoted media thumbnail, part1005.mms
 */
public class SignalAttachmentTest extends AbstractPkgTest {

    private static final String APP = "/case/data/data/org.thoughtcrime.securesms/";
    private static final String DB_PATH = APP + "databases/signal.db";

    private static final String[] PARTS = { "part1001.mms", "part1002.mms", "part1004.mms", "part1005.mms" };

    private final List<IItemReader> children = new ArrayList<>();
    private boolean withSecrets = true;
    // secrets.json files in the case, in the order the index returns them
    private final List<File> secrets = new ArrayList<>();
    private String walFixture = "test_signal_encrypted.db-wal";

    private static File resource(String name) throws URISyntaxException {
        return new File(SignalAttachmentTest.class.getClassLoader().getResource("test-files/" + name).toURI());
    }

    private static IItemReader item(String path, File file, String hash) {
        return item(path, path.substring(path.lastIndexOf('/') + 1), file, hash);
    }

    private static IItemReader item(String path, String name, File file, String hash) {
        IItemReader item = mock(IItemReader.class);
        when(item.getPath()).thenReturn(path);
        when(item.getName()).thenReturn(name);
        when(item.getHash()).thenReturn(hash);
        when(item.getId()).thenReturn(Math.abs(path.hashCode()));
        when(item.getMediaType()).thenReturn(MediaType.OCTET_STREAM);
        if (file != null) {
            try {
                when(item.getBufferedInputStream())
                        .thenAnswer(inv -> new BufferedInputStream(new FileInputStream(file)));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        return item;
    }

    /** Answers the queries the parser makes, as the case index would. */
    private IItemSearcher searcher() {
        return new IItemSearcher() {
            @Override
            public void close() {}

            @Override
            public Iterable<IItemReader> searchIterable(String luceneQuery) {
                return search(luceneQuery);
            }

            @Override
            public List<IItemReader> search(String query) {
                List<IItemReader> result = new ArrayList<>();
                try {
                    if (query.contains("secrets.json") && withSecrets) {
                        if (secrets.isEmpty())
                            secrets.add(resource("test_signal_secrets.json"));
                        for (int i = 0; i < secrets.size(); i++)
                            result.add(item("/case" + i + "/extra/Secrets/secrets.json", secrets.get(i), "s" + i));
                    }
                    else if (query.contains("signal.db-wal") && walFixture != null)
                        result.add(item(DB_PATH + "-wal", resource(walFixture), "w"));
                    else if (query.startsWith("parentId:"))
                        result.addAll(children);
                    else if (query.contains("app_parts"))
                        for (String part : PARTS)
                            if (query.contains(part))
                                result.add(item(APP + "app_parts/" + part,
                                        resource("test_signal_app_parts/" + part), "enc-" + part));
                } catch (URISyntaxException e) {
                    throw new AssertionError(e);
                }
                return result;
            }

            @Override
            public IItemReader searchFirst(String luceneQuery) {
                List<IItemReader> result = search(luceneQuery);
                return result.isEmpty() ? null : result.get(0);
            }

            @Override
            public String escapeQuery(String string) {
                return string;
            }
        };
    }

    private Metadata parse(String fixture, MediaType type, boolean inProcessing) throws Exception {
        return parse(resource(fixture), type, inProcessing);
    }

    private Metadata parse(File db, MediaType type, boolean inProcessing) throws Exception {
        signalContext.set(IItemSearcher.class, searcher());
        if (inProcessing)
            signalContext.set(IItemReader.class, item(DB_PATH, null, "db"));
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, type.toString());
        try (InputStream is = new FileInputStream(db)) {
            new SignalParser().parse(is, new BodyContentHandler(-1), metadata, signalContext);
        }
        return metadata;
    }

    /** What the first pass extracted, as the case index returns it to the second. */
    private void indexDecryptedChildren() {
        for (String name : signalTracker.decoded.keySet())
            children.add(item(DB_PATH + ">>" + name, name, null, "dec-" + name));
    }

    /** Runs the second pass over the same database, with a fresh tracker. */
    private void secondPass(File db, MediaType type) throws Exception {
        indexDecryptedChildren();
        signalTracker = new EmbeddedSignalParser();
        signalContext.set(org.apache.tika.parser.Parser.class, signalTracker);
        parse(db, type, true);
    }

    /** A copy of the plain fixture with the given SQL applied, e.g. the hash columns of newer Signal versions. */
    private static File plainCopy(String... sql) throws Exception {
        File copy = File.createTempFile("signal", ".db");
        copy.deleteOnExit();
        Files.copy(resource("test_signal_attachments.db").toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy.getAbsolutePath());
                Statement st = conn.createStatement()) {
            for (String s : sql)
                st.execute(s);
        }
        return copy;
    }

    private static String sha256(String plainFixture) throws Exception {
        byte[] content = Files.readAllBytes(resource("test_signal_app_parts/" + plainFixture).toPath());
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(content));
    }

    /** secrets.json of another extraction: a Signal entry with another attachment key. */
    private static File otherExtractionSecrets() throws IOException {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        String json = "[{\"app\": \"org.thoughtcrime.securesms\", \"script_output\": ["
                + "{\"payload\": {\"sqlite db key\": \"" + "11".repeat(32) + "\"}},"
                + "{\"payload\": {\"signal attachment key\": {\"modernKey\": \""
                + Base64.getEncoder().withoutPadding().encodeToString(key) + "\"}}}]}]";
        File file = File.createTempFile("secrets", ".json");
        file.deleteOnExit();
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static final String[] HASH_COLUMNS = {
            "ALTER TABLE attachment ADD COLUMN data_hash_start TEXT",
            "ALTER TABLE attachment ADD COLUMN data_hash_end TEXT" };

    private static String[] withHashes(String... sql) {
        List<String> all = new ArrayList<>(Arrays.asList(HASH_COLUMNS));
        all.addAll(Arrays.asList(sql));
        return all.toArray(new String[0]);
    }

    // ── Encrypted database ───────────────────────────────────────────────────

    public void testEncryptedDatabaseWithoutKeyIsFlagged() throws Exception {
        withSecrets = false;
        try {
            parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED, true);
            fail("An encrypted database with no key must be flagged as encrypted");
        } catch (EncryptedDocumentException e) {
            // expected: the processing marks the item as encrypted
        }
        assertTrue("Nothing can be extracted without the key", signalTracker.contentTypes.isEmpty());
    }

    public void testWrongKeyIsRejected() throws Exception {
        // The plain fixture stands in for a key that does not match: decrypting a file
        // with the wrong key fails every page HMAC
        File copy = File.createTempFile("signal", ".db");
        copy.deleteOnExit();
        byte[] data = Files.readAllBytes(resource("test_signal_encrypted.db").toPath());
        data[100] ^= 1;
        Files.write(copy.toPath(), data);
        File out = File.createTempFile("signal_plain", ".db");
        out.deleteOnExit();
        SignalCrypto.Result result = SignalCrypto.decryptDatabase(copy, null, "00".repeat(32), out);
        assertEquals("No page may authenticate with a wrong key", 0, result.getVerified());
    }

    public void testDamagedFirstPageDoesNotRejectTheKey() throws Exception {
        // As in a database recovered after deletion: page 1 damaged, past the salt
        File copy = File.createTempFile("signal", ".db");
        copy.deleteOnExit();
        byte[] data = Files.readAllBytes(resource("test_signal_encrypted.db").toPath());
        data[100] ^= 1;
        Files.write(copy.toPath(), data);
        File out = File.createTempFile("signal_plain", ".db");
        out.deleteOnExit();
        SignalCrypto.Result result = SignalCrypto.decryptDatabase(copy, null,
                "6a1f0e9b3c5d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f", out);
        assertTrue(result.getPages() > 1);
        assertEquals("Only the damaged page fails", result.getPages() - 1, result.getVerified());
    }

    public void testDamagedDatabaseIsReportedNotSkipped() throws Exception {
        // The key still opens it, but page 1 holds the schema: unreadable, and an error
        // instead of a case that looks like it has no Signal data
        walFixture = null;
        File copy = File.createTempFile("signal", ".db");
        copy.deleteOnExit();
        byte[] data = Files.readAllBytes(resource("test_signal_encrypted.db").toPath());
        data[100] ^= 1;
        Files.write(copy.toPath(), data);
        try {
            parse(copy, SignalParser.SIGNAL_DB_ENCRYPTED, true);
            fail("A damaged database must be reported");
        } catch (EncryptedDocumentException e) {
            fail("The key opens it: it is damaged, not encrypted");
        } catch (TikaException e) {
            // expected: a parsing error on the item
        }
    }

    /** Random bytes that are neither SQLite nor a whole number of SQLCipher pages. */
    private static File notADatabase() throws IOException {
        byte[] data = new byte[5000];
        new java.util.Random(1).nextBytes(data);
        File other = File.createTempFile("signal", ".db");
        other.deleteOnExit();
        Files.write(other.toPath(), data);
        return other;
    }

    public void testOtherFileNamedSignalDbIsNotFlaggedAsEncrypted() throws Exception {
        withSecrets = false;
        parse(notADatabase(), SignalParser.SIGNAL_DB_ENCRYPTED, true);
        assertTrue(signalTracker.contentTypes.isEmpty());
    }

    public void testOtherFileNamedSignalDbIsNotFlaggedWithKeysInTheCase() throws Exception {
        parse(notADatabase(), SignalParser.SIGNAL_DB_ENCRYPTED, true);
        assertTrue(signalTracker.contentTypes.isEmpty());
    }

    public void testTruncatedDatabaseInSignalsFolderIsFlagged() throws Exception {
        withSecrets = false;
        signalContext.set(ItemInfo.class, new ItemInfo(0, "db", null, null, DB_PATH, false));
        try {
            parse(notADatabase(), SignalParser.SIGNAL_DB_ENCRYPTED, true);
            fail("A cut-short signal.db in Signal's folder is still an encrypted database");
        } catch (EncryptedDocumentException e) {
            // expected
        }
    }

    public void testAttachmentKeyOfAnotherExtractionIsNotTriedOnAnEncryptedDatabase() throws Exception {
        // The extraction that opens the database captured no attachment key; another one did
        File databaseKeyOnly = File.createTempFile("secrets", ".json");
        databaseKeyOnly.deleteOnExit();
        Files.write(databaseKeyOnly.toPath(), ("[{\"app\": \"org.thoughtcrime.securesms\", \"script_output\": ["
                + "{\"payload\": {\"sqlite db key\": \"6a1f0e9b3c5d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f\"}}]}]")
                .getBytes(StandardCharsets.UTF_8));
        secrets.add(databaseKeyOnly);
        secrets.add(otherExtractionSecrets());
        parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED, true);
        assertTrue(signalTracker.decoded.isEmpty());

        secondPass(resource("test_signal_encrypted.db"), SignalParser.SIGNAL_DB_ENCRYPTED_F);
        String html = String.join("\n", signalTracker.chatHtml);
        assertTrue(html.contains("the Signal attachment key was not found in the case"));
        assertFalse(html.contains("could not be decrypted"));
    }

    public void testUnverifiedKeyWithoutStoredHashIsNotTakenForTheFile() throws Exception {
        // A plain copy, no hash columns (older Signal), and only another device's key
        walFixture = null;
        secrets.add(otherExtractionSecrets());
        parse("test_signal_attachments.db", SignalParser.SIGNAL_DB, true);
        assertTrue("Random bytes from a wrong key are not the file", signalTracker.decoded.isEmpty());
    }

    public void testEveryPageAuthenticatesWithTheRightKey() throws Exception {
        File out = File.createTempFile("signal_plain", ".db");
        out.deleteOnExit();
        SignalCrypto.Result result = SignalCrypto.decryptDatabase(resource("test_signal_encrypted.db"),
                resource("test_signal_encrypted.db-wal"),
                "6a1f0e9b3c5d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f", out);
        assertTrue(result.getPages() > 0);
        assertEquals("Every page, main file and log, must pass its HMAC", result.getPages(), result.getVerified());
    }

    public void testEncryptedDatabaseIsReadWithTheKey() throws Exception {
        parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED, false);
        assertTrue("Conversations must be read from the decrypted database",
                signalTracker.contentTypes.contains(SignalParser.SIGNAL_CHAT.toString()));
        assertTrue(signalTracker.messageBodies.contains("Hello from Alice!"));
    }

    public void testWriteAheadLogIsApplied() throws Exception {
        parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED_F, true);
        assertTrue("A message only in the -wal must be read",
                signalTracker.messageBodies.contains("only in the write-ahead log"));
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    public void testFirstPassDecryptsAttachments() throws Exception {
        Metadata metadata = parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED, true);

        // Its own type, never a subtype of SQLite: the file is still SQLCipher
        assertEquals("The conversations come in the second pass", SignalParser.SIGNAL_DB_ENCRYPTED_F.toString(),
                metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
        assertFalse(signalTracker.contentTypes.contains(SignalParser.SIGNAL_CHAT.toString()));

        // In message order; part1004 is shared by two rows and must come out once
        assertEquals(List.of("part1001", "part1004", "part1002_contrato.pdf", "part1005"),
                new ArrayList<>(signalTracker.decoded.keySet()));
        String[] plain = { "part1001.mms", "part1004.mms", "part1002.mms", "part1005.mms" };
        int i = 0;
        for (byte[] content : signalTracker.decoded.values()) {
            byte[] expected = Files.readAllBytes(resource("test_signal_app_parts/" + plain[i++] + ".plain").toPath());
            assertTrue("Attachment must decrypt to the original content", java.util.Arrays.equals(expected, content));
        }
    }

    public void testSecondPassLinksDecryptedAttachments() throws Exception {
        parse("test_signal_encrypted.db", SignalParser.SIGNAL_DB_ENCRYPTED, true);
        secondPass(resource("test_signal_encrypted.db"), SignalParser.SIGNAL_DB_ENCRYPTED_F);

        // Messages 3, 6, 7, 16 and 21 link a file; 22 (file not in the case) and 23 (never
        // downloaded) have nothing for the attachment viewer to show
        assertEquals(5, signalTracker.contentTypes.stream()
                .filter(SignalParser.SIGNAL_ATTACHMENT.toString()::equals).count());
        assertTrue(signalTracker.linkedItems.contains("hash:dec-part1001"));
        assertTrue(signalTracker.linkedItems.contains("hash:dec-part1002_contrato.pdf"));
        // Sent by the owner: the image of message 3 and the media of message 7
        assertTrue(signalTracker.sharedHashes.contains("hash:dec-part1001"));
        assertTrue(signalTracker.sharedHashes.contains("hash:dec-part1004"));
        // Received, and a quoted thumbnail: not shared by the owner
        assertFalse(signalTracker.sharedHashes.contains("hash:dec-part1002_contrato.pdf"));
        assertFalse(signalTracker.sharedHashes.contains("hash:dec-part1005"));

        assertTrue(signalTracker.messageBodies.contains("[Attachment: image/png]"));
        // Messages 16 and 22 were deleted for everyone, but their attachment rows remain
        assertTrue(signalTracker.messageBodies.contains("[Message deleted] [Attachment: contrato.pdf]"));
        assertTrue(signalTracker.messageBodies.stream()
                .anyMatch(b -> b.startsWith("[Message deleted by") && b.endsWith("[Attachment: Voice message]")));
        assertTrue("Text and attachment are both kept", signalTracker.messageBodies
                .contains("🔒 Evidência & <prova> \"anexada\" [Attachment: image/png]"));

        String html = String.join("\n", signalTracker.chatHtml);
        assertTrue(html.contains("app.open('hash:dec-part1001')"));
        assertTrue(html.contains("foto da prova"));
        assertTrue(html.contains("file not found in the case"));
        assertTrue(html.contains("no file on the device"));
        assertFalse(html.contains("encrypted file"));
    }

    public void testAttachmentsStayEncryptedWithoutTheKey() throws Exception {
        // A plain copy of the database, but no secrets.json in the case
        withSecrets = false;
        walFixture = null;
        Metadata metadata = parse("test_signal_attachments.db", SignalParser.SIGNAL_DB, true);
        assertTrue("Nothing can be decrypted without the key", signalTracker.decoded.isEmpty());
        assertEquals(SignalParser.SIGNAL_DB_F.toString(), metadata.get(StandardParser.INDEXER_CONTENT_TYPE));

        parse("test_signal_attachments.db", SignalParser.SIGNAL_DB_F, true);
        // Linked to the encrypted file itself, which is still the evidence
        assertTrue(signalTracker.linkedItems.contains("hash:enc-part1001.mms"));
        assertTrue("The hash of an encrypted copy is not a shared file", signalTracker.sharedHashes.isEmpty());
        String html = String.join("\n", signalTracker.chatHtml);
        assertTrue(html.contains("encrypted file, the Signal attachment key was not found in the case"));
    }

    public void testAttachmentNotMatchingTheStoredHashIsNotExtracted() throws Exception {
        walFixture = null;
        File db = plainCopy(withHashes(
                "UPDATE attachment SET data_hash_end = '" + sha256("part1001.mms.plain") + "' WHERE _id = 1",
                // What a wrong key or a damaged file would decrypt to
                "UPDATE attachment SET data_hash_start = 'bm90IHRoZSBzYW1lIGNvbnRlbnQ=' WHERE _id = 2"));
        parse(db, SignalParser.SIGNAL_DB, true);
        assertTrue(signalTracker.decoded.containsKey("part1001"));
        assertFalse("Content that does not match Signal's hash is not the file",
                signalTracker.decoded.containsKey("part1002_contrato.pdf"));

        secondPass(db, SignalParser.SIGNAL_DB_F);
        assertFalse(signalTracker.sharedHashes.contains("hash:dec-part1002_contrato.pdf"));
        String html = String.join("\n", signalTracker.chatHtml);
        assertTrue(html.contains("could not be decrypted with the Signal keys found in the case"));
        assertFalse(html.contains("key was not found"));
    }

    public void testKeysOfSeveralExtractionsAreToldApartByHash() throws Exception {
        walFixture = null;
        // The other extraction's key comes first
        secrets.add(otherExtractionSecrets());
        secrets.add(resource("test_signal_secrets.json"));
        File db = plainCopy(withHashes(
                "UPDATE attachment SET data_hash_end = '" + sha256("part1001.mms.plain") + "' WHERE _id = 1"));
        parse(db, SignalParser.SIGNAL_DB, true);
        assertEquals(List.of("part1001", "part1004", "part1002_contrato.pdf", "part1005"),
                new ArrayList<>(signalTracker.decoded.keySet()));
        byte[] expected = Files.readAllBytes(resource("test_signal_app_parts/part1002.mms.plain").toPath());
        assertTrue(Arrays.equals(expected, signalTracker.decoded.get("part1002_contrato.pdf")));
    }

    public void testKeysOfSeveralExtractionsWithoutHashesAreNotGuessed() throws Exception {
        walFixture = null;
        secrets.add(otherExtractionSecrets());
        secrets.add(resource("test_signal_secrets.json"));
        parse("test_signal_attachments.db", SignalParser.SIGNAL_DB, true);
        assertTrue("With no way to tell the keys apart, none is taken", signalTracker.decoded.isEmpty());

        secondPass(resource("test_signal_attachments.db"), SignalParser.SIGNAL_DB_F);
        String html = String.join("\n", signalTracker.chatHtml);
        assertFalse("Keys were found, just not one that could be told apart", html.contains("key was not found"));
        assertTrue(html.contains("could not be decrypted with the Signal keys found in the case"));
    }

    public void testSharedFileWithTwoNamesIsExtractedOnce() throws Exception {
        walFixture = null;
        // Attachments 5 and 6 share part1004.mms
        File db = plainCopy("UPDATE attachment SET file_name = 'a.png' WHERE _id = 5",
                "UPDATE attachment SET file_name = 'b.png' WHERE _id = 6");
        parse(db, SignalParser.SIGNAL_DB, true);
        assertEquals(List.of("part1001", "part1004_a.png", "part1002_contrato.pdf", "part1005"),
                new ArrayList<>(signalTracker.decoded.keySet()));

        secondPass(db, SignalParser.SIGNAL_DB_F);
        // Both messages link the one sub-item; message 7 was sent by the owner
        assertTrue(signalTracker.linkedItems.contains("hash:dec-part1004_a.png"));
        assertFalse(signalTracker.linkedItems.stream().anyMatch(l -> l.contains("part1004_b")));
        assertTrue(signalTracker.sharedHashes.contains("hash:dec-part1004_a.png"));
        String html = String.join("\n", signalTracker.chatHtml);
        assertFalse("Neither row is left without its file", html.contains("encrypted file"));
    }

    public void testPlainDatabaseWithoutAttachmentsIsUnchanged() throws Exception {
        // Outside the processing, both passes run at once
        signalContext.set(IItemSearcher.class, searcher());
        Metadata metadata = new Metadata();
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, SignalParser.SIGNAL_DB.toString());
        try (InputStream is = new FileInputStream(resource("test_signal.db"))) {
            new SignalParser().parse(is, new BodyContentHandler(-1), metadata, signalContext);
        }
        assertTrue(signalTracker.decoded.isEmpty());
        assertTrue(signalTracker.linkedItems.isEmpty());
        assertEquals(Collections.emptyList(), new ArrayList<>(signalTracker.sharedHashes));
    }

    // ── Replies and reactions ────────────────────────────────────────────────

    private static final String[] QUOTE_COLUMNS = {
            "ALTER TABLE message ADD COLUMN quote_id INTEGER DEFAULT 0",
            "ALTER TABLE message ADD COLUMN quote_author INTEGER DEFAULT 0",
            "ALTER TABLE message ADD COLUMN quote_body TEXT DEFAULT NULL",
            "ALTER TABLE message ADD COLUMN quote_missing INTEGER DEFAULT 0" };

    private static String[] withQuotes(String... sql) {
        List<String> all = new ArrayList<>(Arrays.asList(QUOTE_COLUMNS));
        all.addAll(Arrays.asList(sql));
        return all.toArray(new String[0]);
    }

    private String chatHtmlOf(File db) throws Exception {
        walFixture = null;
        // Outside the processing: the conversations are built at once
        parse(db, SignalParser.SIGNAL_DB, false);
        return String.join("\n", signalTracker.chatHtml);
    }

    public void testReplyShowsTheQuotedMessage() throws Exception {
        // Message 2, from the owner, replies to Alice's message 1
        String html = chatHtmlOf(plainCopy(withQuotes(
                "UPDATE message SET quote_id = 1700000010000, quote_author = 1, quote_body = 'Hello from Alice!' WHERE _id = 2")));
        assertTrue(html.contains("<div class=\"quote\" data-target=\"m1\" onclick=\"goToMessage('m1')\"><div class=\"quote-author\">Alice Walker</div>"
                + "<div class=\"body\">Hello from Alice!</div></div><div class=\"body\">Hi Alice, how are you?</div>"));
        assertTrue("The original is the anchor the reply jumps to", html.contains("id=\"m1\""));
    }

    public void testReplyToAMessageNoLongerOnTheDevice() throws Exception {
        String html = chatHtmlOf(plainCopy(withQuotes(
                "UPDATE message SET quote_id = 1699999999999, quote_author = 1, quote_body = 'gone', quote_missing = 1 WHERE _id = 2")));
        assertTrue(html.contains("<div class=\"quote\"><div class=\"quote-author\">Alice Walker</div><div class=\"body\">gone</div>"));
        assertTrue(html.contains("original message not found in the database"));
    }

    public void testOriginalFoundLaterIsNotReportedMissing() throws Exception {
        // quote_missing is what Signal knew when the reply arrived; the original is here now
        String html = chatHtmlOf(plainCopy(withQuotes(
                "UPDATE message SET quote_id = 1700000010000, quote_author = 1, quote_body = 'Hello from Alice!', quote_missing = 1 WHERE _id = 2")));
        assertTrue(html.contains("goToMessage('m1')"));
        assertFalse(html.contains("original message not found"));
    }

    public void testQuoteWithoutAuthorOrTextIsNotAReply() throws Exception {
        // Signal's own rule: a quote id, an author, and text or a quoted attachment
        String html = chatHtmlOf(plainCopy(withQuotes(
                "UPDATE message SET quote_id = 1700000010000, quote_author = 0, quote_body = 'x' WHERE _id = 2",
                "UPDATE message SET quote_id = 1700000010000, quote_author = 1, quote_body = '' WHERE _id = 14")));
        assertFalse(html.contains("class=\"quote\""));
    }

    public void testQuotedAttachmentIsShownInsideTheQuote() throws Exception {
        // Attachment 7 is the thumbnail of the media message 21 quotes
        String html = chatHtmlOf(plainCopy(withQuotes(
                "UPDATE message SET quote_id = 1700000010000, quote_author = 1 WHERE _id = 21")));
        int quote = html.indexOf("<div class=\"quote\"");
        // Message 21 is also a scheduled one, hence the prefix of its body
        int replyBody = html.indexOf("see you tomorrow</div>");
        assertTrue(quote >= 0 && replyBody > quote);
        assertTrue("The thumbnail belongs to the quote, above the reply",
                html.substring(quote, replyBody).contains("Quoted: "));
        assertFalse("And is not shown again below it",
                html.startsWith("see you tomorrow</div><div class=\"attachment\">", replyBody));
    }

    public void testReactionsAreShownWithTheirAuthor() throws Exception {
        String html = chatHtmlOf(plainCopy(
                "CREATE TABLE reaction (_id INTEGER PRIMARY KEY, message_id INTEGER NOT NULL, author_id INTEGER NOT NULL,"
                        + " emoji TEXT NOT NULL, date_sent INTEGER NOT NULL, date_received INTEGER NOT NULL)",
                "INSERT INTO reaction VALUES (1, 1, 5, '\uD83D\uDE00', 1700000025000, 1700000025000)",
                "INSERT INTO reaction VALUES (2, 1, 1, '\u2764\uFE0F', 1700000026000, 1700000026000)"));
        assertTrue(html.contains("<div class=\"reactions\"><span class=\"reaction\""));
        assertTrue(html.contains("\uD83D\uDE00 Device Owner</span>"));
        assertTrue(html.contains("\u2764\uFE0F Alice Walker</span>"));
        assertTrue("The tooltip has who and when, the date once",
                html.matches("(?s).*title=\"Device Owner \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}[^\"\\d]*[^\"]*\">\uD83D\uDE00 Device Owner</span>.*"));
    }

    // ── Detection ────────────────────────────────────────────────────────────

    public void testPlainDatabaseIsDetectedByItsTables() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(resource("test_signal_attachments.db").toPath())) {
            assertEquals(SignalParser.SIGNAL_DB, new SQLiteContainerDetector().detect(tis, new Metadata()));
        }
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    public void testKeysAreReadFromTheSignalEntry() throws Exception {
        SignalKeys keys;
        try (InputStream is = new FileInputStream(resource("test_signal_secrets.json"))) {
            keys = SignalKeys.read(is);
        }
        // Another app's entry, listed first, must not be taken
        assertEquals("6a1f0e9b3c5d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f", keys.getDatabaseKey());
        assertEquals(32, keys.getAttachmentKey().length);
        assertEquals(31, keys.getAttachmentKey()[31]);
    }

    public void testUnreadableAttachmentKeyKeepsTheDatabaseKey() throws Exception {
        String json = "[{\"app\": \"org.thoughtcrime.securesms\", \"script_output\": ["
                + "{\"payload\": {\"sqlite db key\": \"abcd\"}},"
                + "{\"payload\": {\"signal attachment key\": \"not json\"}}]}]";
        SignalKeys keys = SignalKeys.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("abcd", keys.getDatabaseKey());
        assertNull(keys.getAttachmentKey());
    }
}
