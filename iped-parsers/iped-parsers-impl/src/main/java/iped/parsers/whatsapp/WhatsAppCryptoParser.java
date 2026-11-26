/*
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.whatsapp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.python.PythonParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.PhoneParsingConfig;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
import jep.JepException;

/**
 * Decrypts WhatsApp encrypted backups (crypt12, crypt14 and crypt15) when the
 * corresponding key file is available in the case. Besides the databases,
 * WhatsApp encrypts other backed up files with the same scheme, like stickers
 * and backup_settings.json, and all of them are decrypted.
 *
 * The decryption itself is delegated to wa-crypt-tools
 * (https://github.com/ElDavoo/wa-crypt-tools), whose python API is called
 * through JEP. The backup is read from the item stream and decrypted in chunks,
 * so it is neither written to disk nor fully loaded in memory. It is a python package that must be installed with pip, see
 * {@link #INSTALL_COMMAND}. The decrypted file is created as a subitem of the encrypted one,
 * keeping its name without the .cryptNN extension, so the signature detection
 * types it and, in the case of databases, {@link WhatsAppParser} decodes it.
 *
 */
public class WhatsAppCryptoParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(WhatsAppCryptoParser.class);

    public static final MediaType WA_CRYPT = MediaType.application("x-whatsapp-crypt");

    public static final MediaType WA_KEY = MediaType.application("x-whatsapp-key");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(WA_CRYPT);

    private static final String TOOL_NAME = "wa-crypt-tools";

    /** Version of wa-crypt-tools this parser was tested with. */
    private static final String TOOL_VERSION = "0.1.0";

    public static final String INSTALL_COMMAND = "pip install " + TOOL_NAME + "==" + TOOL_VERSION;

    public static final String WHATSAPP_METADATA_PREFIX = "whatsapp:";

    /** Flags the subitem as the decrypted content of its encrypted parent. */
    private static final String WHATSAPP_METADATA_IS_DECRYPTED = WHATSAPP_METADATA_PREFIX + "isDecrypted";
    private static final String WHATSAPP_METADATA_CRYPT_FORMAT = WHATSAPP_METADATA_PREFIX + "cryptFormat";
    private static final String WHATSAPP_METADATA_APP_VERSION = WHATSAPP_METADATA_PREFIX + "appVersion";
    private static final String WHATSAPP_METADATA_KEY_PATH = WHATSAPP_METADATA_PREFIX + "decryptionKeyPath";

    /** Tool used to decrypt the file and its version, e.g. "wa-crypt-tools 0.1.0". */
    private static final String WHATSAPP_METADATA_DECRYPTION_TOOL = WHATSAPP_METADATA_PREFIX + "decryptionTool";

    /** Result of the decryption attempt, one of the {@link DecryptionStatus} values. */
    private static final String WHATSAPP_METADATA_DECRYPTION_STATUS = WHATSAPP_METADATA_PREFIX + "decryptionStatus";

    /** Details about why the decryption failed, if it did. */
    private static final String WHATSAPP_METADATA_DECRYPTION_ERROR = WHATSAPP_METADATA_PREFIX + "decryptionError";

    /** Possible values of the {@link #WHATSAPP_METADATA_DECRYPTION_STATUS} metadata. */
    private enum DecryptionStatus {
        decrypted, alreadyDecrypted, keyNotFound, failed, toolUnavailable;
    }

    private static final String PY_MODULE = "wa_decrypt";
    private static final String PY_RESOURCE = PY_MODULE + ".py";

    private static final String CRYPT_EXT_PREFIX = "crypt";

    /** Enough to hold the header of any encrypted backup, they are smaller than 300 bytes. */
    private static final int HEAD_SIZE = 512;

    /** How many bytes of the encrypted backup are decrypted at a time. */
    private static final int CHUNK_SIZE = 1 << 20;

    /**
     * Key files are java serialized byte arrays of 32 (crypt15) or 131 (crypt12 and
     * crypt14) bytes, so anything bigger than this is surely a false positive.
     */
    private static final long MAX_KEY_LENGTH = 1024;

    // only read and written inside the synchronized extractPyModule(), so it needs
    // neither to be volatile nor atomic
    private static File pyModuleFolder;

    // lazily got by getDecryptionTool(), it is the same value for all threads
    private static volatile String decryptionTool;

    /** Tells if the helper module was already loaded in the interpreter of this thread. */
    private static final ThreadLocal<Boolean> moduleLoaded = ThreadLocal.withInitial(() -> false);

    /** Set if wa-crypt-tools could not be loaded, so nothing will be decrypted. */
    private static volatile boolean initFailed = false;

    /** Why wa-crypt-tools could not be loaded, recorded in the items not decrypted. */
    private static volatile String unavailableReason;

    // this parser may be instantiated more than once, possibly by concurrent threads,
    // but it is checked just once. Both are guarded by this class monitor.
    private static boolean initChecked = false;
    private static String initErrorMessage;

    public WhatsAppCryptoParser() {
        synchronized (WhatsAppCryptoParser.class) {
            if (!initChecked) {
                try {
                    // fail fast: a failure here means wa-crypt-tools will never work
                    getInitializedJep();

                } catch (TikaException e) {
                    // the reason is in the message, so the stack trace adds nothing here,
                    // unexpected errors are thrown as RuntimeException by getInitializedJep
                    logger.error("WhatsApp encrypted backups will not be decrypted. {}", e.getMessage());
                    unavailableReason = e.getMessage();
                    initFailed = true;

                } catch (RuntimeException e) {
                    // remembered so concurrent and later instantiations fail as well
                    initErrorMessage = e.getMessage();
                    initFailed = true;
                    throw e;

                } finally {
                    initChecked = true;
                }
            }
            if (initErrorMessage != null) {
                throw new RuntimeException(initErrorMessage);
            }
        }
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // still supported if wa-crypt-tools is not available, so that is recorded in
        // the items and not only in the log
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        if (metadata.get(WHATSAPP_METADATA_DECRYPTION_STATUS) != null) {
            // The file was already handled when the case was processed: the status is
            // restored from the index into the item metadata when the case is opened.
            // This is a later parsing done by the analysis app, e.g. to build the text
            // view, so decrypting again would just waste time (and would fail if
            // wa-crypt-tools is not installed in the machine doing the analysis).
            return;
        }

        IItemReader item = context.get(IItemReader.class);
        IItemSearcher searcher = context.get(IItemSearcher.class);

        if (item == null || searcher == null) {
            return;
        }

        if (initFailed) {
            // already logged once by the constructor
            metadata.set(StandardParser.ENCRYPTED_DOCUMENT, Boolean.toString(true));
            metadata.set(WHATSAPP_METADATA_DECRYPTION_STATUS, DecryptionStatus.toolUnavailable.toString());
            if (unavailableReason != null) {
                metadata.set(WHATSAPP_METADATA_DECRYPTION_ERROR, unavailableReason);
            }
            return;
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources()) {

            Jep jep;
            try {
                jep = getInitializedJep();
            } catch (TikaException e) {
                throw decryptionFailed(metadata, DecryptionStatus.toolUnavailable, e.getMessage(), e);
            }

            // the given stream is not used: the backup must be read from its start
            // again for each key tried, so its streams are got from the item. Direct
            // because that is how JEP shares a buffer with python without copying it
            int trailerSize = ((Number) jep.invoke(PY_MODULE + ".get_trailer_size")).intValue();
            ByteBuffer buffer = ByteBuffer.allocateDirect(Math.max(HEAD_SIZE, CHUNK_SIZE + trailerSize));

            fillHeaderInfo(jep, item, buffer, metadata);

            metadata.set(StandardParser.ENCRYPTED_DOCUMENT, Boolean.toString(true));

            if (isDecryptedByUfed(item, searcher)) {
                // UFED already decrypted it and the UFDR includes the decrypted file, which
                // is processed as any other item, so decrypting again would duplicate it
                logger.info("Skipping {}, it was already decrypted by UFED", item.getPath());
                metadata.set(WHATSAPP_METADATA_DECRYPTION_STATUS, DecryptionStatus.alreadyDecrypted.toString());
                return;
            }

            List<IItemReader> keys = getKeyCandidates(item, searcher);
            if (keys.isEmpty()) {
                throw decryptionFailed(metadata, DecryptionStatus.keyNotFound,
                        "No WhatsApp encryption key was found in the case to decrypt " + item.getPath(), null);
            }

            File decryptedFile = tmp.createTemporaryFile();
            IItemReader usedKey = null;
            String lastError = null;

            for (IItemReader key : keys) {
                try {
                    String warning = decrypt(jep, key.getTempFile(), item, buffer, trailerSize, decryptedFile);
                    if (warning != null) {
                        logger.warn("{}: {}", warning, item.getPath());
                        metadata.set(WHATSAPP_METADATA_DECRYPTION_ERROR, warning);
                    }
                    usedKey = key;
                    break;

                } catch (TikaException | IOException | JepException e) {
                    // just a wrong or unreadable key, try the next one
                    lastError = e.getMessage();
                    logger.debug("Could not decrypt {} with key {}: {}", item.getPath(), key.getPath(), lastError);
                }
            }

            if (usedKey == null) {
                throw decryptionFailed(metadata, DecryptionStatus.failed, "Could not decrypt " + item.getPath() + " with any "
                        + "of the " + keys.size() + " key(s) found in the case: " + lastError, null);
            }

            logger.info("Decrypted {} using key {}", item.getPath(), usedKey.getPath());
            metadata.set(WHATSAPP_METADATA_DECRYPTION_STATUS, DecryptionStatus.decrypted.toString());
            metadata.set(WHATSAPP_METADATA_KEY_PATH, usedKey.getPath());
            metadata.set(WHATSAPP_METADATA_DECRYPTION_TOOL, getDecryptionTool(jep));
            if (StringUtils.isNotBlank(usedKey.getHash())) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + usedKey.getHash());
            }

            createDecryptedSubItem(jep, item, decryptedFile, handler, metadata, context);

        } catch (JepException e) {
            throw new TikaException("Error calling wa-crypt-tools for " + item.getPath(), e);

        } finally {
            xhtml.endDocument();
        }
    }

    /**
     * Tells if the given encrypted file from an UFDR was already decrypted by UFED.
     * UFED exports the decrypted content below the path of the encrypted file, e.g.
     * ".../msgstore.db.crypt14/msgstore.db", so the UFDR reader creates a folder
     * with the same path of the encrypted file to hold it. The folder is searched
     * for instead of the decrypted file because the latter may not be indexed yet,
     * decrypted databases wait in a later processing queue.
     */
    private static boolean isDecryptedByUfed(IItemReader item, IItemSearcher searcher) {
        if (!PhoneParsingConfig.isFromUfdrDatasourceReader(item) || StringUtils.isBlank(item.getName())) {
            return false;
        }
        String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(item.getName()) + "\" AND " + BasicProps.ISDIR + ":true";
        for (IItemReader folder : iped.parsers.util.Util.getItems(query, searcher)) {
            if (item.getPath().equals(folder.getPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records why the decryption did not happen in the item metadata and builds the
     * exception to be thrown, so the failure is also visible to the examiner and
     * not only in the log.
     */
    private static TikaException decryptionFailed(Metadata metadata, DecryptionStatus status, String message, Throwable cause) {
        metadata.set(WHATSAPP_METADATA_DECRYPTION_STATUS, status.toString());
        metadata.set(WHATSAPP_METADATA_DECRYPTION_ERROR, message);
        return cause == null ? new TikaException(message) : new TikaException(message, cause);
    }

    /**
     * Creates the decrypted file as a child of the encrypted one. Its name is the
     * name of the encrypted file without the .cryptNN extension, so the signature
     * detection assigns it the proper mediaType and, for the databases,
     * QueuesProcessingOrder moves it to the queue where WhatsAppParser decodes it.
     */
    private void createDecryptedSubItem(Jep jep, IItemReader item, File decryptedFile, ContentHandler handler, Metadata metadata,
            ParseContext context) throws SAXException, IOException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (!extractor.shouldParseEmbedded(metadata)) {
            return;
        }

        Metadata dbMetadata = new Metadata();
        dbMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, getDecryptedName(item));
        dbMetadata.set(WHATSAPP_METADATA_IS_DECRYPTED, Boolean.toString(true));
        dbMetadata.set(WHATSAPP_METADATA_DECRYPTION_TOOL, getDecryptionTool(jep));
        dbMetadata.set(ExtraProperties.DECODED_DATA, Boolean.toString(true));
        if (item.getCreationDate() != null) {
            dbMetadata.set(TikaCoreProperties.CREATED, item.getCreationDate());
        }
        if (item.getModDate() != null) {
            dbMetadata.set(TikaCoreProperties.MODIFIED, item.getModDate());
        }

        try (InputStream is = new FileInputStream(decryptedFile)) {
            extractor.parseEmbedded(is, handler, dbMetadata, true);
        }
    }

    /**
     * "msgstore.db.crypt14" -> "msgstore.db",
     * "msgstore-2024-01-01.1.db.crypt15" -> "msgstore-2024-01-01.1.db",
     * "backup_settings.json.crypt14" -> "backup_settings.json"
     */
    private static String getDecryptedName(IItemReader item) {
        String name = item.getName();
        if (StringUtils.isBlank(name)) {
            return "decrypted";
        }
        if (StringUtils.startsWithIgnoreCase(StringUtils.substringAfterLast(name, "."), CRYPT_EXT_PREFIX)) {
            name = StringUtils.substringBeforeLast(name, ".");
        }
        return name;
    }

    private void fillHeaderInfo(Jep jep, IItemReader item, ByteBuffer buffer, Metadata metadata)
            throws JepException, IOException {
        byte[] head;
        try (InputStream is = item.getBufferedInputStream()) {
            head = is.readNBytes(HEAD_SIZE);
        }
        buffer.clear();
        buffer.put(head);
        Object result = jep.invoke(PY_MODULE + ".get_info", buffer, head.length);
        if (!(result instanceof Map)) {
            return;
        }
        Map<?, ?> info = (Map<?, ?>) result;
        setIfNotNull(metadata, WHATSAPP_METADATA_CRYPT_FORMAT, info.get("format"));
        setIfNotNull(metadata, WHATSAPP_METADATA_APP_VERSION, info.get("appVersion"));
    }

    private static void setIfNotNull(Metadata metadata, String property, Object value) {
        if (value != null) {
            metadata.set(property, value.toString());
        }
    }

    /**
     * Decrypts the given item into the given file, reading it in chunks that are
     * decrypted by python. Its last bytes, the trailer, are not encrypted data: they
     * are held until the end of the stream is reached, when they are used to check
     * if the key matches.
     *
     * @return a warning about the decrypted content, or null if there is none
     * @throws JepException
     *             with the reason reported by wa-crypt-tools if the key does not
     *             match or the backup is corrupted
     * @throws TikaException
     *             if nothing was decrypted
     */
    private String decrypt(Jep jep, File keyFile, IItemReader item, ByteBuffer buffer, int trailerSize, File decryptedFile)
            throws JepException, TikaException, IOException {

        // bytes read and not decrypted yet, always ending with the possible trailer
        byte[] pending = new byte[CHUNK_SIZE + trailerSize];
        long decryptedSize = 0;

        try (InputStream is = item.getBufferedInputStream(); OutputStream os = new FileOutputStream(decryptedFile)) {

            byte[] head = is.readNBytes(HEAD_SIZE);
            buffer.clear();
            buffer.put(head);
            int headerSize = ((Number) jep.invoke(PY_MODULE + ".begin", keyFile.getAbsolutePath(), buffer, head.length)).intValue();

            int pendingSize = head.length - headerSize;
            System.arraycopy(head, headerSize, pending, 0, pendingSize);

            boolean eof = false;
            while (!eof) {
                pendingSize += is.readNBytes(pending, pendingSize, pending.length - pendingSize);
                eof = pendingSize < pending.length;

                int encryptedSize = pendingSize - trailerSize;
                if (encryptedSize > 0) {
                    buffer.clear();
                    buffer.put(pending, 0, encryptedSize);
                    byte[] decrypted = (byte[]) jep.invoke(PY_MODULE + ".update", buffer, encryptedSize);
                    os.write(decrypted);
                    decryptedSize += decrypted.length;

                    System.arraycopy(pending, encryptedSize, pending, 0, trailerSize);
                    pendingSize = trailerSize;
                }
            }

            buffer.clear();
            buffer.put(pending, 0, pendingSize);
            List<?> result = (List<?>) jep.invoke(PY_MODULE + ".finish", buffer, pendingSize);
            byte[] decrypted = (byte[]) result.get(0);
            os.write(decrypted);
            decryptedSize += decrypted.length;

            if (decryptedSize == 0) {
                throw new TikaException("Decrypted data is empty");
            }
            return (String) result.get(1);
        }
    }

    /**
     * Searches the case for WhatsApp key files, the most likely key for the given
     * encrypted file first, i.e. the one sharing the longest path prefix with it.
     */
    private List<IItemReader> getKeyCandidates(IItemReader item, IItemSearcher searcher) {
        if (searcher == null) {
            return Collections.emptyList();
        }
        String query = BasicProps.CONTENTTYPE + ":\"" + WA_KEY + "\" AND NOT " + BasicProps.LENGTH + ":0";
        List<IItemReader> keys = new ArrayList<>();
        for (IItemReader key : iped.parsers.util.Util.getItems(query, searcher)) {
            if (key.getLength() != null && key.getLength() <= MAX_KEY_LENGTH) {
                keys.add(key);
            }
        }
        String path = item.getPath();
        // negated so the longest common prefix comes first
        keys.sort(Comparator.comparingInt(k -> -StringUtils.getCommonPrefix(k.getPath(), path).length()));
        return keys;
    }

    /**
     * Returns a JEP interpreter for the current thread with the helper module
     * loaded.
     *
     * @throws TikaException
     *             if python, JEP or wa-crypt-tools are not available
     */
    private static Jep getInitializedJep() throws TikaException {

        Jep jep;
        try {
            jep = PythonParser.getJep();
        } catch (JepException e) {
            throw new TikaException(PythonParser.JEP_NOT_FOUND + PythonParser.SEE_MANUAL, e);
        }
        if (jep == null) {
            throw new TikaException(PythonParser.JEP_NOT_FOUND + PythonParser.SEE_MANUAL);
        }

        if (moduleLoaded.get()) {
            return jep;
        }

        try {
            File moduleFolder = extractPyModule();
            jep.eval("import sys");
            jep.eval("sys.path.append(r'" + moduleFolder.getAbsolutePath() + "')");
            jep.eval("import " + PY_MODULE);
            jep.invoke(PY_MODULE + ".init");

        } catch (JepException | IOException e) {
            if (e.toString().contains("ModuleNotFoundError") || e.toString().contains("ImportError")) {
                // wa-crypt-tools or one of its dependencies is not installed or is an
                // incompatible version
                throw new TikaException("The " + TOOL_NAME + " python package could not be loaded: " + e.getMessage() //
                        + ". Please install it with '" + INSTALL_COMMAND + "'. " //
                        + PythonParser.SEE_MANUAL, e);
            }
            throw new RuntimeException(e);
        }
        moduleLoaded.set(true);
        return jep;
    }

    /**
     * Returns the name of the tool used to decrypt followed by its installed
     * version, or just its name if the version is unknown.
     */
    private static String getDecryptionTool(Jep jep) {
        if (decryptionTool == null) {
            String tool = TOOL_NAME;
            try {
                Object version = jep.invoke(PY_MODULE + ".get_version");
                if (version != null) {
                    tool += " " + version;
                }
            } catch (JepException e) {
                logger.warn("Could not get {} version: {}", TOOL_NAME, e.toString());
            }
            decryptionTool = tool;
        }
        return decryptionTool;
    }

    /** Extracts the helper module from the classpath once, returning its folder. */
    private static synchronized File extractPyModule() throws IOException {
        if (pyModuleFolder == null) {
            File folder = Files.createTempDirectory("iped-wa-crypt").toFile();
            folder.deleteOnExit();
            File script = new File(folder, PY_RESOURCE);
            script.deleteOnExit();
            try (InputStream is = WhatsAppCryptoParser.class.getResourceAsStream(PY_RESOURCE)) {
                if (is == null) {
                    throw new IOException(PY_RESOURCE + " not found in classpath");
                }
                Files.copy(is, script.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            pyModuleFolder = folder;
        }
        return pyModuleFolder;
    }
}
