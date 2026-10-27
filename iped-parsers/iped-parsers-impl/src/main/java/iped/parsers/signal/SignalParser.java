package iped.parsers.signal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.EmptyInputStream;

/**
 * Parser for Signal Messenger Android databases (signal.db).
 *
 * Extracts individual and group conversations with messages, calls and attachments.
 * Signal keeps the database encrypted with SQLCipher and the attachment files under
 * app_parts encrypted too; both are read with the keys of secrets.json when the
 * extraction captured them, and a plain copy of the database is read as is.
 *
 * Inside the processing the database is parsed twice, as the WhatsApp and Threema
 * parsers do: the first pass extracts the decrypted attachments as sub-items, and the
 * second, in a later queue, builds the conversations once those are indexed and can be
 * linked from the messages.
 */
public class SignalParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalParser.class);

    // Service name used in USER_ACCOUNT_TYPE, as TelegramParser and ThreemaParser do:
    // it becomes the service of the account nodes in link analysis. Note this keeps
    // Signal accounts on their own nodes instead of merging them into phone nodes,
    // which is what WhatsAppParser opts into (see GraphTask.getAccountNodeValues).
    public static final String SIGNAL = "Signal";

    private static final String SIGNAL_PACKAGE = "org.thoughtcrime.securesms";

    // A plain database, detected by its tables
    public static final MediaType SIGNAL_DB      = MediaType.application("x-signal-db");
    // signal.db as found on the device: SQLCipher, matched by file name
    public static final MediaType SIGNAL_DB_ENCRYPTED = MediaType.application("x-signal-db-encrypted");
    // Each of the above once its attachments were extracted. The encrypted one keeps a
    // type of its own, so it is never taken for a SQLite file
    public static final MediaType SIGNAL_DB_F    = MediaType.application("x-signal-db-f");
    public static final MediaType SIGNAL_DB_ENCRYPTED_F = MediaType.application("x-signal-db-encrypted-f");
    public static final MediaType SIGNAL_CHAT    = MediaType.application("x-signal-chat");
    public static final MediaType SIGNAL_MESSAGE = MediaType.parse("message/x-signal-message");
    public static final MediaType SIGNAL_ATTACHMENT = MediaType.parse("message/x-signal-attachment");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(SIGNAL_DB, SIGNAL_DB_ENCRYPTED, SIGNAL_DB_F,
            SIGNAL_DB_ENCRYPTED_F);

    // Attachment files sit in app_parts, next to the databases folder of the app
    private static final String DATABASES_DIR = "/databases/";
    private static final String PARTS_DIR = "app_parts/";
    private static final int FILE_SEARCH_BATCH_SIZE = 200;
    // Attachments decrypted to tell apart the keys of several extractions
    private static final int MAX_KEY_PROBES = 3;

    // ReportGenerator uses ThreadLocal formatters; singleton is safe
    private static final ReportGenerator REPORT_GEN = new ReportGenerator();

    private final SQLite3Parser sqliteParser = new SQLite3Parser();

    private boolean extractMessages = true;
    private int minChatSplitSize = 6000000;

    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    @Field
    public void setMinChatSplitSize(int minChatSplitSize) {
        this.minChatSplitSize = minChatSplitSize;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        if (!extractor.shouldParseEmbedded(metadata))
            return;

        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemReader dbItem = context.get(IItemReader.class);
        String contentType = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
        boolean attachmentsPass = !SIGNAL_DB_F.toString().equals(contentType)
                && !SIGNAL_DB_ENCRYPTED_F.toString().equals(contentType);
        // Outside the processing there is no case to find the attachment files in: the
        // conversations are built at once, with attachments listed but not extracted
        boolean twoPasses = dbItem != null && searcher != null;

        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            ItemInfo itemInfo = context.get(ItemInfo.class);
            String itemPath = itemInfo != null ? itemInfo.getPath() : "";

            byte[] prefix = new byte[16];
            int read = tis.peek(prefix);
            // A deleted signal.db overwritten with zeros, or a truncated one, is not an
            // encrypted database and must not be flagged as one
            if (read < prefix.length || Arrays.equals(prefix, new byte[prefix.length])) {
                LOGGER.debug("{} has no database content, skipping it", itemPath);
                return;
            }
            boolean encrypted = !SignalCrypto.isPlainSqlite(prefix);

            // Signal's SQLCipher file is a whole number of pages. Outside the app's folder,
            // anything else named signal.db is another app's file, not an encrypted database;
            // inside it, a truncated copy is still tried, and flagged if no key opens it
            if (encrypted && tis.getFile().length() % SignalCrypto.PAGE_SIZE != 0
                    && !itemPath.replace('\\', '/').contains(SIGNAL_PACKAGE + "/")) {
                LOGGER.debug("{} is neither SQLite nor a SQLCipher database, skipping it", itemPath);
                return;
            }

            List<SignalKeys> candidates = SignalKeys.findAll(searcher);
            SignalKeys keys = SignalKeys.none();
            // Pages of the decrypted database that failed authentication
            boolean damaged = false;
            Connection connection;
            if (encrypted) {
                File plain = tmp.createTemporaryFile();
                boolean anyDatabaseKey = candidates.stream().anyMatch(k -> k.getDatabaseKey() != null);
                File wal = anyDatabaseKey ? findWal(searcher, dbItem, tmp) : null;
                SignalCrypto.Result[] opened = new SignalCrypto.Result[1];
                keys = decryptDatabase(tis.getFile(), wal, candidates, plain, itemPath, opened);
                if (keys == null) {
                    // Flagged as an encrypted item by the processing, instead of a case that
                    // looks like it has no Signal data at all
                    throw new EncryptedDocumentException(candidates.isEmpty()
                            ? "Signal database is encrypted and its key was not found in the case"
                            : "No Signal key found in the case opens this database");
                }
                damaged = opened[0].getVerified() < opened[0].getPages();
                try {
                    connection = DriverManager.getConnection("jdbc:sqlite:" + plain.getAbsolutePath());
                } catch (SQLException e) {
                    throw new IOException(e);
                }
            } else {
                try {
                    connection = getConnection(tis, metadata, context);
                } catch (IOException e) {
                    LOGGER.warn("Could not open {} as a SQLite database: {}", itemPath, e.getMessage());
                    return;
                }
            }

            boolean unsupported;
            try {
                SignalExtractor signalExtractor = new SignalExtractor(connection, itemPath);
                unsupported = !signalExtractor.isValidSignalDatabase();
                if (unsupported) {
                    // Opened by its key but unreadable: reported as a parsing error, instead of
                    // a case that looks like it has no Signal data at all
                    if (damaged)
                        throw new TikaException("Signal database " + itemPath
                                + " was decrypted, but its damaged pages leave it unreadable");
                    // A decrypted file is not something the generic parser could read
                    if (encrypted || !signalExtractor.isReadableDatabase()) {
                        LOGGER.warn("DB at {} is not a supported Signal database, skipping it", itemPath);
                        unsupported = false;
                    }
                } else {
                    List<SignalAttachment> attachments = signalExtractor.extractAttachments();
                    findAttachmentFiles(attachments, searcher, dbItem);
                    // The key that opened the database belongs with it; a plain copy holds
                    // no hint of the extraction it came from, so any key in the case is tried
                    boolean keyFound = encrypted ? keys.getAttachmentKey() != null
                            : candidates.stream().anyMatch(k -> k.getAttachmentKey() != null);
                    // Outside the processing there are no attachment files to decrypt
                    if (attachmentsPass && twoPasses) {
                        byte[] attachmentKey = encrypted ? keys.getAttachmentKey()
                                : chooseAttachmentKey(candidates, attachments, itemPath);
                        extractAttachments(attachments, attachmentKey, encrypted, handler, extractor, tmp, itemPath);
                        // Conversations come in a later queue, once the attachments just
                        // extracted are indexed and can be linked
                        metadata.set(StandardParser.INDEXER_CONTENT_TYPE,
                                (encrypted ? SIGNAL_DB_ENCRYPTED_F : SIGNAL_DB_F).toString());
                        return;
                    }
                    if (!attachmentsPass)
                        linkDecryptedAttachments(attachments, keyFound, searcher, dbItem);

                    List<SignalChat> chats = signalExtractor.extractChats();
                    SignalContact selfContact = signalExtractor.findSelfContact();
                    markOwnMessagesAsOutgoing(chats, selfContact);
                    if (selfContact == null)
                        LOGGER.warn("Could not identify the device owner in {}: outgoing messages will have no sender",
                                itemPath);
                    createReports(chats, selfContact, handler, extractor);
                }
            } finally {
                // Closed before any fallback: the generic parser opens the same temporary
                // file and would rewrite and delete its -wal/-shm under this connection
                try { connection.close(); } catch (SQLException e) { /* ignore */ }
            }

            if (unsupported) {
                // An older Signal database or an unrelated file: the generic SQLite parser
                // still gives the examiner a table preview. A failure here is not a Signal
                // parsing error, so it is logged instead of thrown.
                LOGGER.debug("DB at {} is not a supported Signal database, parsing it as plain SQLite", itemPath);
                try {
                    sqliteParser.parse(tis, handler, metadata, context);
                } catch (SAXException e) {
                    throw e;
                } catch (Exception e) {
                    LOGGER.warn("Could not parse {} as a plain SQLite database: {}", itemPath, e.getMessage());
                }
            }

        } catch (SAXException | EncryptedDocumentException e) {
            // SAXException (WriteLimitReachedException among them) stops Tika's pipeline,
            // and EncryptedDocumentException is what flags the item as encrypted
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Error parsing Signal database", e);
            throw new TikaException("SignalParser error", e);
        }
    }

    /**
     * Tries each key found in the case and returns the one that opens the database, or null.
     * Every page carries an HMAC, so a key from another extraction is told apart at once.
     */
    private static SignalKeys decryptDatabase(File database, File wal, List<SignalKeys> candidates, File output,
            String itemPath, SignalCrypto.Result[] opened) throws IOException {
        for (SignalKeys keys : candidates) {
            if (keys.getDatabaseKey() == null)
                continue;
            SignalCrypto.Result result = SignalCrypto.decryptDatabase(database, wal, keys.getDatabaseKey(), output);
            if (result.getVerified() == 0)
                continue;
            if (result.getVerified() < result.getPages())
                LOGGER.warn("{} of {} pages of the Signal database {} failed authentication; the data read from it may be incomplete",
                        result.getPages() - result.getVerified(), result.getPages(), itemPath);
            opened[0] = result;
            return keys;
        }
        return null;
    }

    /** The encrypted -wal of the database, copied to a temporary file, or null. */
    private static File findWal(IItemSearcher searcher, IItemReader dbItem, TemporaryResources tmp) throws IOException {
        if (searcher == null || dbItem == null)
            return null;
        String walName = dbItem.getName() + "-wal";
        String query = BasicProps.PATH + ":\"" + searcher.escapeQuery(dbItem.getPath() + "-wal") + "\"";
        IItemReader wal = null;
        for (IItemReader item : searcher.search(query)) {
            if (item.isDir() || !walName.equalsIgnoreCase(item.getName()))
                continue;
            // Same deleted status as the database, when there is a choice
            if (wal == null || item.isDeleted() == dbItem.isDeleted())
                wal = item;
        }
        if (wal == null)
            return null;
        File file = tmp.createTemporaryFile();
        try (InputStream in = wal.getBufferedInputStream()) {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    /**
     * Looks up the stored file of each attachment under app_parts, next to the databases
     * folder the database is in. Files found are marked ENCRYPTED until decrypted.
     */
    private static void findAttachmentFiles(List<SignalAttachment> attachments, IItemSearcher searcher,
            IItemReader dbItem) {
        if (searcher == null || dbItem == null || attachments.isEmpty())
            return;
        String dbPath = dbItem.getPath().replace('\\', '/');
        int dir = dbPath.lastIndexOf(DATABASES_DIR);
        if (dir < 0)
            return;
        String partsDir = dbPath.substring(0, dir + 1) + PARTS_DIR;

        Map<String, List<SignalAttachment>> byPath = new HashMap<>();
        for (SignalAttachment a : attachments) {
            String stored = a.getStoredFileName();
            if (stored != null)
                byPath.computeIfAbsent(partsDir + stored, k -> new ArrayList<>()).add(a);
        }
        List<String> paths = new ArrayList<>(byPath.keySet());
        for (int i = 0; i < paths.size(); i += FILE_SEARCH_BATCH_SIZE) {
            StringBuilder query = new StringBuilder();
            for (String path : paths.subList(i, Math.min(paths.size(), i + FILE_SEARCH_BATCH_SIZE)))
                query.append(BasicProps.PATH).append(":\"").append(searcher.escapeQuery(path)).append("\" ");
            for (IItemReader item : searcher.search(query.toString())) {
                List<SignalAttachment> list = byPath.get(item.getPath().replace('\\', '/'));
                if (list == null || item.isDir())
                    continue;
                for (SignalAttachment a : list) {
                    // A deleted copy only when nothing better was found
                    if (a.getItem() == null || (a.getItem().isDeleted() && !item.isDeleted())) {
                        a.setItem(item);
                        a.setState(SignalAttachment.State.ENCRYPTED);
                    }
                }
            }
        }
        for (List<SignalAttachment> list : byPath.values())
            for (SignalAttachment a : list)
                if (a.getItem() == null)
                    a.setState(SignalAttachment.State.MISSING);
    }

    /** Name of the decrypted sub-item: the stored file, plus the original name if any. */
    static String decryptedName(SignalAttachment a) {
        String stored = a.getStoredFileName();
        int dot = stored.lastIndexOf('.');
        String name = dot > 0 ? stored.substring(0, dot) : stored;
        if (a.getFileName() != null && !a.getFileName().isBlank())
            name += "_" + a.getFileName().strip().replaceAll("[/\\\\]", "_");
        return name;
    }

    /**
     * The attachment key for a plain copy of the database. With keys from more than one
     * extraction in the case, the right one is the key whose output matches the hash
     * Signal stored for an attachment: AES-CTR has no authentication, so a wrong key
     * would yield random bytes taken for the file.
     */
    private static byte[] chooseAttachmentKey(List<SignalKeys> candidates, List<SignalAttachment> attachments,
            String itemPath) {
        List<byte[]> keys = new ArrayList<>();
        for (SignalKeys k : candidates) {
            byte[] key = k.getAttachmentKey();
            if (key != null && keys.stream().noneMatch(other -> Arrays.equals(other, key)))
                keys.add(key);
        }
        if (keys.size() <= 1)
            return keys.isEmpty() ? null : keys.get(0);

        int tried = 0;
        for (SignalAttachment a : attachments) {
            if (a.getState() != SignalAttachment.State.ENCRYPTED || a.getDataRandom() == null || !a.hasDataHash())
                continue;
            for (byte[] key : keys) {
                try {
                    if (a.matchesDataHash(decryptAttachment(a, key, OutputStream.nullOutputStream())))
                        return key;
                } catch (IOException e) {
                    break;
                }
            }
            if (++tried == MAX_KEY_PROBES)
                break;
        }
        LOGGER.warn("Signal keys of {} extractions found in the case, and none could be matched to the attachments of {}: they stay encrypted",
                keys.size(), itemPath);
        return null;
    }

    /** Decrypts the stored file of the attachment into out and returns the SHA-256 of the plain content. */
    private static byte[] decryptAttachment(SignalAttachment a, byte[] key, OutputStream out) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new DigestInputStream(
                SignalCrypto.decryptAttachment(a.getItem().getBufferedInputStream(), key, a.getDataRandom()), digest)) {
            IOUtils.copy(in, out);
        }
        return digest.digest();
    }

    /** Whether the file starts like some known format or is text, as decrypted media does. */
    private static boolean hasKnownFormat(File file) throws IOException {
        // Taken when needed, as the other parsers do: the custom signatures of the
        // processing must be loaded before Tika's types are
        try (TikaInputStream in = TikaInputStream.get(file.toPath())) {
            return !MediaType.OCTET_STREAM.equals(TikaConfig.getDefaultConfig().getDetector().detect(in, new Metadata()));
        }
    }

    /**
     * Encrypted attachments by the name of their decrypted sub-item. Rows can share a stored
     * file (Signal deduplicates identical media), which becomes a single sub-item named
     * after the first row; both passes group the same rows in the same order.
     */
    private static Map<String, List<SignalAttachment>> groupByStoredFile(List<SignalAttachment> attachments) {
        Map<String, List<SignalAttachment>> byFile = new LinkedHashMap<>();
        for (SignalAttachment a : attachments)
            if (a.getState() == SignalAttachment.State.ENCRYPTED)
                byFile.computeIfAbsent(a.getStoredFileName(), k -> new ArrayList<>()).add(a);
        Map<String, List<SignalAttachment>> byName = new LinkedHashMap<>();
        for (List<SignalAttachment> rows : byFile.values())
            byName.put(decryptedName(rows.get(0)), rows);
        return byName;
    }

    /**
     * Decrypts each stored file once and extracts it as a sub-item of the database. A file
     * whose decrypted content does not match the hash Signal stored is not extracted.
     */
    private static void extractAttachments(List<SignalAttachment> attachments, byte[] attachmentKey,
            boolean keyOfTheDatabase, ContentHandler handler, EmbeddedDocumentExtractor extractor,
            TemporaryResources tmp, String itemPath) throws SAXException, IOException {
        Map<String, List<SignalAttachment>> byName = groupByStoredFile(attachments);
        if (byName.isEmpty())
            return;
        if (attachmentKey == null) {
            LOGGER.info("Signal attachment key not found in the case: attachments of {} stay encrypted", itemPath);
            return;
        }

        for (Map.Entry<String, List<SignalAttachment>> entry : byName.entrySet()) {
            String name = entry.getKey();
            List<SignalAttachment> rows = entry.getValue();
            SignalAttachment a = rows.stream().filter(r -> r.getDataRandom() != null).findFirst().orElse(null);
            if (a == null)
                continue;

            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            meta.set(TikaCoreProperties.TITLE, name);
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
            if (!extractor.shouldParseEmbedded(meta))
                continue;

            File plain = tmp.createTemporaryFile();
            try {
                byte[] hash;
                try (OutputStream out = Files.newOutputStream(plain.toPath())) {
                    hash = decryptAttachment(a, attachmentKey, out);
                } catch (IOException e) {
                    LOGGER.warn("Could not decrypt Signal attachment {} of {}: {}", a.getStoredFileName(), itemPath,
                            e.getMessage());
                    continue;
                }
                if (rows.stream().anyMatch(SignalAttachment::hasDataHash)) {
                    if (rows.stream().noneMatch(r -> r.matchesDataHash(hash))) {
                        LOGGER.warn("Signal attachment {} of {} does not match the hash stored by Signal after decryption, not extracting it",
                                a.getStoredFileName(), itemPath);
                        continue;
                    }
                } else if (!keyOfTheDatabase && !hasKnownFormat(plain)) {
                    // Older databases store no hash. A key from another extraction would yield
                    // random bytes, which have no recognizable format
                    LOGGER.warn("Signal attachment {} of {} has no stored hash and decrypts to no known format, not extracting it",
                            a.getStoredFileName(), itemPath);
                    continue;
                }
                try (InputStream is = Files.newInputStream(plain.toPath())) {
                    extractor.parseEmbedded(is, handler, meta, false);
                }
            } finally {
                Files.deleteIfExists(plain.toPath());
            }
        }
    }

    /**
     * Points each attachment at the sub-item the first pass decrypted it into. Those left
     * encrypted although a key was found could not be decrypted with it.
     */
    private static void linkDecryptedAttachments(List<SignalAttachment> attachments, boolean keyFound,
            IItemSearcher searcher, IItemReader dbItem) {
        if (searcher == null || dbItem == null || attachments.isEmpty())
            return;
        Map<String, IItemReader> children = new HashMap<>();
        for (IItemReader child : searcher.search(BasicProps.PARENTID + ":" + dbItem.getId()))
            children.put(child.getName(), child);
        for (Map.Entry<String, List<SignalAttachment>> entry : groupByStoredFile(attachments).entrySet()) {
            IItemReader child = children.get(entry.getKey());
            for (SignalAttachment a : entry.getValue()) {
                if (child != null) {
                    a.setItem(child);
                    a.setState(SignalAttachment.State.DECRYPTED);
                } else if (keyFound) {
                    a.setState(SignalAttachment.State.NOT_DECRYPTED);
                }
            }
        }
    }

    /**
     * Base types carry no direction for some rows, group calls among them, so a message
     * whose sender is the device owner is marked as outgoing here, where the owner is known.
     */
    private static void markOwnMessagesAsOutgoing(List<SignalChat> chats, SignalContact selfContact) {
        if (selfContact == null)
            return;
        for (SignalChat chat : chats) {
            for (SignalMessage m : chat.getMessages()) {
                // Only where the base type carries no direction: the owner is found by a
                // heuristic, and an incoming row already knows it is incoming
                if (m.getMessageType() != SignalMessage.MessageType.INCOMING
                        && m.getSender() != null && m.getSender().getId() == selfContact.getId())
                    m.setFromMe(true);
            }
        }
    }

    private void createReports(List<SignalChat> chats, SignalContact selfContact,
            ContentHandler handler, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        int chatVirtualId = 0;
        for (SignalChat chat : chats) {
            String chatTitle = chat.getTitle();
            List<SignalMessage> allMessages = chat.getMessages();

            int firstMessage = 0;
            int fragmentNum = 0;
            boolean hasMore;

            // A long conversation becomes several items, as the WhatsApp, Telegram and
            // Threema parsers do, instead of one huge preview held whole in memory
            do {
                ReportGenerator.Fragment fragment =
                        REPORT_GEN.generateChatFragment(chat, firstMessage, minChatSplitSize);
                List<SignalMessage> fragmentMessages =
                        allMessages.subList(firstMessage, fragment.getNextMessage());
                hasMore = fragment.getNextMessage() < allMessages.size();

                String fragmentTitle = (fragmentNum > 0 || hasMore)
                        ? chatTitle + "_" + fragmentNum
                        : chatTitle;

                // Count only indexable (non-system) messages for HASCHILD
                long indexableCount = fragmentMessages.stream()
                        .filter(m -> m.getMessageType() != SignalMessage.MessageType.SYSTEM)
                        .count();

                Metadata chatMeta = new Metadata();
                chatMeta.set(TikaCoreProperties.TITLE, fragmentTitle);
                chatMeta.set(StandardParser.INDEXER_CONTENT_TYPE, SIGNAL_CHAT.toString());
                chatMeta.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(chatVirtualId));
                chatMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                if (chat.isGroupChat()) {
                    chatMeta.add(ExtraProperties.GROUP_ID, chat.getContact().getGroupId());
                    // Self first (device owner); then members, excluding self to avoid duplication
                    if (selfContact != null)
                        chatMeta.add(ExtraProperties.PARTICIPANTS, selfContact.getFullId());
                    for (SignalContact member : chat.getParticipants()) {
                        if (selfContact == null || member.getId() != selfContact.getId())
                            chatMeta.add(ExtraProperties.PARTICIPANTS, member.getFullId());
                    }
                } else if (chat.getContact() != null) {
                    if (selfContact != null)
                        chatMeta.add(ExtraProperties.PARTICIPANTS, selfContact.getFullId());
                    // Note to Self has the owner as the chat contact: do not list them twice
                    if (selfContact == null || chat.getContact().getId() != selfContact.getId())
                        chatMeta.add(ExtraProperties.PARTICIPANTS, chat.getContact().getFullId());
                }

                // Period covered by this fragment, so split conversations are readable
                // on the timeline without opening each one
                Date firstDate = messageDate(fragmentMessages, true);
                Date lastDate = messageDate(fragmentMessages, false);
                if (firstDate != null)
                    chatMeta.set(TikaCoreProperties.CREATED, firstDate);
                if (lastDate != null)
                    chatMeta.set(TikaCoreProperties.MODIFIED, lastDate);

                if (extractMessages && indexableCount > 0)
                    chatMeta.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

                storeLinkedItems(fragmentMessages, chatMeta);

                if (extractor.shouldParseEmbedded(chatMeta)) {
                    extractor.parseEmbedded(new ByteArrayInputStream(fragment.getHtml()), handler,
                            chatMeta, false);

                    if (extractMessages) {
                        extractMessages(fragmentTitle, chat, fragmentMessages, selfContact,
                                chatVirtualId, handler, extractor);
                    }
                }

                firstMessage = fragment.getNextMessage();
                fragmentNum++;
                chatVirtualId++;
            } while (hasMore);
        }
    }

    private void extractMessages(String chatTitle, SignalChat chat, List<SignalMessage> messages,
            SignalContact selfContact, int parentVirtualId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        String selfId = selfContact != null ? selfContact.getFullId() : null;

        int msgCount = 0;
        for (SignalMessage m : messages) {
            // System messages are rendered only in the HTML report; not indexed individually
            if (m.getMessageType() == SignalMessage.MessageType.SYSTEM)
                continue;

            Metadata msgMeta = new Metadata();
            msgMeta.set(TikaCoreProperties.TITLE, chatTitle + "_message_" + msgCount++);
            msgMeta.set(StandardParser.INDEXER_CONTENT_TYPE, SIGNAL_MESSAGE.toString());
            msgMeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentVirtualId));
            msgMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            // fall back to dateReceived like ReportGenerator does, so a message
            // with no dateSent (e.g. a locally-originated draft/failed send)
            // doesn't silently drop out of date-based search/filtering while
            // still showing a date in the HTML report
            Date msgDate = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
            if (msgDate != null) {
                msgMeta.set(ExtraProperties.MESSAGE_DATE, msgDate);
                msgMeta.set(TikaCoreProperties.CREATED, msgDate);
            }

            msgMeta.set(ExtraProperties.MESSAGE_BODY, resolveBody(m));
            msgMeta.set(ExtraProperties.USER_ACCOUNT_TYPE, SIGNAL);

            // Only when a file in the case is linked: the viewer of this type shows it
            if (storeLinkedItems(List.of(m), msgMeta))
                msgMeta.set(StandardParser.INDEXER_CONTENT_TYPE, SIGNAL_ATTACHMENT.toString());

            if (chat.isGroupChat())
                msgMeta.set(ExtraProperties.IS_GROUP_MESSAGE, "true");
            // Sub-items have no content of their own: an empty length becomes null,
            // instead of being indexed as 0 bytes (same as WhatsApp/Telegram/Threema)
            msgMeta.set(BasicProps.LENGTH, "");

            SignalContact contact = chat.getContact();
            // Built from the conversation title, not the fragment one, so splitting a long
            // conversation does not split its group node; the Signal group_id keeps
            // untitled or same-named groups apart
            String groupTo = chat.isGroupChat()
                    ? chat.getTitle() + " (id:" + chat.getContact().getGroupId() + ")"
                    : null;
            if (m.isFromMe()) {
                // The row's own sender is more precise than the global heuristic, which
                // picks a single recipient row even when the owner changed numbers
                String fromId = m.getSender() != null ? m.getSender().getFullId() : selfId;
                if (fromId != null)
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, fromId);
                // For groups, TO is the chat title; for individual, TO is the contact
                if (chat.isGroupChat()) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, groupTo);
                } else if (contact != null) {
                    msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, contact.getFullId());
                }
            } else {
                if (chat.isGroupChat()) {
                    // Resolved from the recipient table, so former members are named too
                    // Left unset when the sender cannot be resolved: an "Unknown" literal
                    // would merge unrelated people into one node in link analysis
                    SignalContact sender = m.getSender();
                    if (sender != null)
                        msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, sender.getFullId());
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_TO, groupTo);
                } else if (contact != null) {
                    msgMeta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, contact.getFullId());
                    if (selfId != null)
                        msgMeta.add(org.apache.tika.metadata.Message.MESSAGE_TO, selfId);
                }
            }

            if (!extractor.shouldParseEmbedded(msgMeta))
                continue;

            extractor.parseEmbedded(new EmptyInputStream(), handler, msgMeta, false);
        }
    }

    /**
     * Links the attachment files found in the case, so they are listed as referenced by
     * the message or the conversation. Only decrypted files count as shared: the hash of
     * an encrypted copy is unique to the device. Returns whether any file was linked.
     */
    private static boolean storeLinkedItems(List<SignalMessage> messages, Metadata metadata) {
        Set<String> linked = new LinkedHashSet<>();
        Set<String> shared = new LinkedHashSet<>();
        for (SignalMessage m : messages) {
            for (SignalAttachment a : m.getAttachments()) {
                if (a.getItem() == null || a.getItem().getHash() == null)
                    continue;
                String query = BasicProps.HASH + ":" + a.getItem().getHash();
                linked.add(query);
                if (m.isFromMe() && !a.isQuote() && a.getState() == SignalAttachment.State.DECRYPTED)
                    shared.add(query);
            }
        }
        for (String query : linked)
            metadata.add(ExtraProperties.LINKED_ITEMS, query);
        for (String query : shared)
            metadata.add(ExtraProperties.SHARED_HASHES, query);
        return !linked.isEmpty();
    }

    private static Date messageDate(List<SignalMessage> messages, boolean first) {
        for (int i = 0; i < messages.size(); i++) {
            SignalMessage m = messages.get(first ? i : messages.size() - 1 - i);
            Date date = m.getDateSent() != null ? m.getDateSent() : m.getDateReceived();
            if (date != null)
                return date;
        }
        return null;
    }

    private static String resolveBody(SignalMessage m) {
        // Also for a deleted message: an attachment row that outlived the deletion is
        // evidence of what was sent
        String attachments = attachmentsLabel(m);
        String text = resolveText(m, attachments == null);
        if (attachments == null)
            return text;
        return text != null ? text + " " + attachments : attachments;
    }

    private static String resolveText(SignalMessage m, boolean noAttachments) {
        // Calls read from the call table describe their own type and outcome
        if (m.isRemoteDeleted())
            return m.getDeletedBy() != null
                    ? "[Message deleted by " + m.getDeletedBy().getFullId() + "]"
                    : "[Message deleted]";
        if (m.isViewOnce() && m.getBody() == null)
            return "[View-once media]";
        if (m.getCallDetail() != null)
            return "[" + m.getCallDetail() + "]";
        if (m.isEarlierRevision())
            return "[Edited, earlier version] " + (m.getBody() != null ? m.getBody() : "[Empty message]");
        if (m.isScheduled())
            return "[Scheduled, never sent] " + (m.getBody() != null ? m.getBody() : "[Empty message]");
        switch (m.getMessageType()) {
            case CALL_OUTGOING: return "[Outgoing Call]";
            case CALL_INCOMING: return "[Incoming Call]";
            case CALL_MISSED:   return "[Missed Call]";
            case CALL_GROUP:    return "[Group Call]";
            default:
                // Body is also null for deleted, expired and reaction-only rows
                if (m.getBody() != null)
                    return m.getBody();
                return noAttachments ? "[Empty message]" : null;
        }
    }

    /** E.g. "[Attachment: photo.jpg]", or null when the message has none. */
    static String attachmentsLabel(SignalMessage m) {
        StringBuilder sb = new StringBuilder();
        for (SignalAttachment a : m.getAttachments()) {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(a.isQuote() ? "[Quoted attachment: " : "[Attachment: ").append(a.getLabel()).append("]");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
