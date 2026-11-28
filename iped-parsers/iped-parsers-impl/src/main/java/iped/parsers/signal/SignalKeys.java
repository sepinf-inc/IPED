package iped.parsers.signal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * Signal keys captured by the extraction tool. Extractions that capture app secrets
 * (UFED full file system, for one) include extra/Secrets/secrets.json, where the entry
 * of org.thoughtcrime.securesms holds the database key and the attachment secret.
 */
public class SignalKeys {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalKeys.class);

    private static final String SIGNAL_PACKAGE = "org.thoughtcrime.securesms";
    private static final String SECRETS_FILE = "secrets.json";
    private static final String DATABASE_KEY = "sqlite db key";
    private static final String ATTACHMENT_KEY = "signal attachment key";
    private static final String MODERN_KEY = "modernKey";

    private static final SignalKeys NONE = new SignalKeys(null, null);

    private final String databaseKey;
    private final byte[] attachmentKey;

    SignalKeys(String databaseKey, byte[] attachmentKey) {
        this.databaseKey = databaseKey;
        this.attachmentKey = attachmentKey;
    }

    /** Passphrase of the SQLCipher database, or null. */
    public String getDatabaseKey() { return databaseKey; }

    /** The modernKey of Signal's AttachmentSecret, or null. */
    public byte[] getAttachmentKey() { return attachmentKey; }

    public static SignalKeys none() { return NONE; }

    /**
     * Every set of Signal keys in the case. A case may hold more than one extraction, so
     * the caller checks which key actually opens the database at hand.
     */
    public static List<SignalKeys> findAll(IItemSearcher searcher) {
        List<SignalKeys> found = new ArrayList<>();
        if (searcher == null)
            return found;
        String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(SECRETS_FILE) + "\"";
        for (IItemReader item : searcher.search(query)) {
            if (item.isDir() || !SECRETS_FILE.equalsIgnoreCase(item.getName()))
                continue;
            try (InputStream is = item.getBufferedInputStream()) {
                SignalKeys keys = read(is);
                if (keys.databaseKey != null || keys.attachmentKey != null)
                    found.add(keys);
            } catch (Exception e) {
                LOGGER.warn("Could not read Signal keys from {}: {}", item.getPath(), e.getMessage());
            }
        }
        return found;
    }

    /**
     * Reads a secrets.json: a list of entries, one per app, each with the "payload" objects
     * its extraction script produced.
     */
    static SignalKeys read(InputStream secrets) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(secrets);
        String databaseKey = null;
        byte[] attachmentKey = null;
        if (root == null || !root.isArray())
            return NONE;
        for (JsonNode entry : root) {
            if (!SIGNAL_PACKAGE.equals(entry.path("app").asText()))
                continue;
            for (JsonNode output : entry.path("script_output")) {
                JsonNode payload = output.path("payload");
                JsonNode dbKey = payload.path(DATABASE_KEY);
                if (dbKey.isTextual() && !dbKey.asText().isBlank())
                    databaseKey = dbKey.asText().strip();
                JsonNode secret = payload.path(ATTACHMENT_KEY);
                // Signal serializes its AttachmentSecret as JSON; accept it kept as text too
                if (secret.isTextual()) {
                    try {
                        secret = mapper.readTree(secret.asText());
                    } catch (IOException e) {
                        LOGGER.warn("Signal attachment key in secrets.json is not valid JSON");
                        secret = null;
                    }
                }
                JsonNode modernKey = secret == null ? null : secret.path(MODERN_KEY);
                if (modernKey != null && modernKey.isTextual() && !modernKey.asText().isBlank()) {
                    // Android's Base64 without padding
                    String encoded = modernKey.asText().strip().replace("=", "");
                    try {
                        attachmentKey = Base64.getDecoder().decode(encoded);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Signal attachment key in secrets.json is not valid base64");
                    }
                }
            }
        }
        return new SignalKeys(databaseKey, attachmentKey);
    }
}
