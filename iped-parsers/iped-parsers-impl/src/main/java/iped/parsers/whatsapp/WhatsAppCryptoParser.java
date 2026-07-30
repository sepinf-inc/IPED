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
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.tika.io.TikaInputStream;
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
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
import jep.JepException;

/**
 * Decrypts WhatsApp encrypted database backups (crypt12, crypt14 and crypt15)
 * when the corresponding key file is available in the case.
 *
 * The decryption itself is delegated to wa-crypt-tools
 * (https://github.com/ElDavoo/wa-crypt-tools), whose python API is called
 * through JEP. The decrypted database is created as a subitem of the encrypted
 * one, so it is later decoded by {@link WhatsAppParser}.
 *
 */
public class WhatsAppCryptoParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(WhatsAppCryptoParser.class);

    public static final MediaType MSG_STORE_CRYPT = MediaType.application("x-whatsapp-db-crypt");

    public static final MediaType WA_KEY = MediaType.application("x-whatsapp-key");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(MSG_STORE_CRYPT);

    /** System property pointing to the folder where wa-crypt-tools was unpacked. */
    public static final String TOOL_PATH_PROP = "waCryptToolsPath";

    public static final String WHATSAPP_METADATA_PREFIX = "whatsapp:";

    /**
     * Metadata set on the decrypted subitem, holding the path of the encrypted
     * database it came from.
     */
    private static final String WHATSAPP_METADATA_DECRYPTED_FROM = WHATSAPP_METADATA_PREFIX + "decryptedFrom";
    private static final String WHATSAPP_METADATA_CRYPT_FORMAT = WHATSAPP_METADATA_PREFIX + "cryptFormat";
    private static final String WHATSAPP_METADATA_APP_VERSION = WHATSAPP_METADATA_PREFIX + "appVersion";
    private static final String WHATSAPP_METADATA_KEY_PATH = WHATSAPP_METADATA_PREFIX + "decryptionKeyPath";

    /** Result of the decryption attempt, one of the {@link DecryptionStatus} values. */
    private static final String WHATSAPP_METADATA_DECRYPTION_STATUS = WHATSAPP_METADATA_PREFIX + "decryptionStatus";

    /** Details about why the decryption failed, if it did. */
    private static final String WHATSAPP_METADATA_DECRYPTION_ERROR = WHATSAPP_METADATA_PREFIX + "decryptionError";

    /** Possible values of the {@link #WHATSAPP_METADATA_DECRYPTION_STATUS} metadata. */
    private enum DecryptionStatus {
        decrypted, toolNotAvailable, keyNotFound, failed;
    }

    private static final String PY_MODULE = "wa_decrypt";
    private static final String PY_RESOURCE = PY_MODULE + ".py";

    private static final String CRYPT_EXT_PREFIX = "crypt";

    /**
     * Key files are java serialized byte arrays of 32 (crypt15) or 131 (crypt12 and
     * crypt14) bytes, so anything bigger than this is surely a false positive.
     */
    private static final long MAX_KEY_LENGTH = 1024;

    private static final Object initLock = new Object();

    // both are only read and written inside the initLock monitor, so they need
    // neither to be volatile nor atomic
    private static boolean pyModuleExtracted = false;
    private static File pyModuleFolder;

    /** Tells if the helper module was already loaded in the interpreter of this thread. */
    private static final ThreadLocal<Boolean> moduleLoaded = ThreadLocal.withInitial(() -> false);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        if (metadata.get(WHATSAPP_METADATA_DECRYPTION_STATUS) != null) {
            // The DB was already handled when the case was processed: the status is
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

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources()) {

            Jep jep = getInitializedJep(metadata);

            File encryptedFile = TikaInputStream.get(stream, tmp).getFile();

            fillHeaderInfo(jep, encryptedFile, metadata);

            metadata.set(StandardParser.ENCRYPTED_DOCUMENT, Boolean.toString(true));

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
                    decrypt(jep, key.getTempFile(), encryptedFile, decryptedFile);
                    usedKey = key;
                    break;

                } catch (TikaException | IOException e) {
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
            if (StringUtils.isNotBlank(usedKey.getHash())) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + usedKey.getHash());
            }

            createDecryptedSubItem(item, decryptedFile, handler, metadata, context);

        } catch (JepException e) {
            throw new TikaException("Error calling wa-crypt-tools for " + item.getPath(), e);

        } finally {
            xhtml.endDocument();
        }
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
     * Creates the decrypted database as a child of the encrypted one. Its name is
     * the name of the encrypted DB without the .cryptNN extension, so the signature
     * detection assigns it the proper WhatsApp mediaType and QueuesProcessingOrder
     * moves it to the queue where WhatsAppParser expects to decode it.
     */
    private void createDecryptedSubItem(IItemReader item, File decryptedFile, ContentHandler handler, Metadata metadata,
            ParseContext context) throws SAXException, IOException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (!extractor.shouldParseEmbedded(metadata)) {
            return;
        }

        Metadata dbMetadata = new Metadata();
        dbMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, getDecryptedName(item));
        dbMetadata.set(WHATSAPP_METADATA_DECRYPTED_FROM, item.getPath());
        dbMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
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
     * "msgstore-2024-01-01.1.db.crypt15" -> "msgstore-2024-01-01.1.db"
     */
    private static String getDecryptedName(IItemReader item) {
        String name = item.getName();
        if (StringUtils.isBlank(name)) {
            return "msgstore.db";
        }
        if (StringUtils.startsWithIgnoreCase(StringUtils.substringAfterLast(name, "."), CRYPT_EXT_PREFIX)) {
            name = StringUtils.substringBeforeLast(name, ".");
        }
        return name;
    }

    private void fillHeaderInfo(Jep jep, File encryptedFile, Metadata metadata) throws JepException {
        Object result = jep.invoke(PY_MODULE + ".get_info", encryptedFile.getAbsolutePath());
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
     * @throws TikaException
     *             with the reason reported by wa-crypt-tools if the key does not
     *             match or the backup is corrupted
     */
    private void decrypt(Jep jep, File keyFile, File encryptedFile, File decryptedFile)
            throws JepException, TikaException {
        Object result = jep.invoke(PY_MODULE + ".decrypt", keyFile.getAbsolutePath(), encryptedFile.getAbsolutePath(),
                decryptedFile.getAbsolutePath());
        if (result != null) {
            throw new TikaException(result.toString());
        }
    }

    /**
     * Searches the case for WhatsApp key files, the most likely key for the given
     * encrypted DB first, i.e. the one sharing the longest path prefix with it.
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
    private static Jep getInitializedJep(Metadata metadata) throws TikaException {

        String toolPath = System.getProperty(TOOL_PATH_PROP);
        if (StringUtils.isBlank(toolPath) || !new File(toolPath).isDirectory()) {
            throw decryptionFailed(metadata, DecryptionStatus.toolNotAvailable,
                    "wa-crypt-tools was not found in " + toolPath, null);
        }

        Jep jep;
        try {
            jep = PythonParser.getJep();
        } catch (JepException e) {
            throw decryptionFailed(metadata, DecryptionStatus.toolNotAvailable,
                    PythonParser.JEP_NOT_FOUND + PythonParser.SEE_MANUAL, e);
        }
        if (jep == null) {
            throw decryptionFailed(metadata, DecryptionStatus.toolNotAvailable,
                    PythonParser.JEP_NOT_FOUND + PythonParser.SEE_MANUAL, null);
        }

        if (moduleLoaded.get()) {
            return jep;
        }

        synchronized (initLock) {
            try {
                extractPyModule();
                jep.eval("import sys");
                jep.eval("sys.path.append(r'" + pyModuleFolder.getAbsolutePath() + "')");
                jep.eval("import " + PY_MODULE);
                jep.invoke(PY_MODULE + ".init", toolPath);

            } catch (JepException | IOException e) {
                throw decryptionFailed(metadata, DecryptionStatus.toolNotAvailable,
                        "Could not load wa-crypt-tools. Please install the python dependencies listed in "
                                + new File(toolPath, "requirements.txt") + ". " + PythonParser.SEE_MANUAL,
                        e);
            }
        }
        moduleLoaded.set(true);
        return jep;
    }

    private static void extractPyModule() throws IOException {
        if (pyModuleExtracted) {
            return;
        }
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
        pyModuleExtracted = true;
    }
}
