package iped.engine.dictionary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.dictionary.PasswordCrackAttempt;
import iped.dictionary.PasswordDictionaryFactory;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PasswordCrackingConfig;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class PasswordDictionaryFactoryImpl implements PasswordDictionaryFactory {

    private static final Logger logger = LoggerFactory.getLogger(PasswordDictionaryFactoryImpl.class);

    /**
     * System property holding the path of the wordlist file passed via command
     * line (-dictionary/-dict).
     */
    public static final String DICTIONARY_PATH_PROP = "iped.passwordDictionaryPath";

    /** Content types of case items known to store passwords. */
    private static final String[] PASSWORD_CONTENT_TYPES = { "application/x-ufed-password" };

    /** Metadata fields (on the items above) that hold the password value. */
    private static final String[] PASSWORD_METADATA_FIELDS = { ExtraProperties.UFED_META_PREFIX + "Data",
            ExtraProperties.UFED_META_PREFIX + "Password" };

    @Override
    public Iterable<String> createPasswordDictionary(IItemSearcher searcher) {
        List<String> prependedPasswords = collectKnownPasswords(searcher);

        PasswordCrackingConfig config = getConfig();
        long warnSizeBytes = config != null ? config.getWarnDictionaryBytes() : 0;

        return new PasswordDictionary(getWordlistPath(), prependedPasswords, warnSizeBytes);
    }

    @Override
    public <T> T crack(Iterable<String> passwords, PasswordCrackAttempt<T> attempt) {
        ParallelPasswordCracker cracker = new ParallelPasswordCracker(getConfig());
        return cracker.crack(passwords, attempt);
    }

    private PasswordCrackingConfig getConfig() {
        ConfigurationManager cm = ConfigurationManager.get();
        return cm != null ? cm.findObject(PasswordCrackingConfig.class) : null;
    }

    private Path getWordlistPath() {
        String path = System.getProperty(DICTIONARY_PATH_PROP);
        if (path == null || path.isBlank()) {
            return null;
        }
        return Path.of(path);
    }

    /**
     * Collects passwords already known from the case (e.g. UFED password items),
     * so they are tried before the wordlist.
     */
    private List<String> collectKnownPasswords(IItemSearcher searcher) {
        Set<String> passwords = new LinkedHashSet<>();
        if (searcher != null) {
            for (String contentType : PASSWORD_CONTENT_TYPES) {
                String query = BasicProps.CONTENTTYPE + ":\"" + contentType + "\"";
                try {
                    List<IItemReader> items = searcher.search(query);
                    for (IItemReader item : items) {
                        for (String field : PASSWORD_METADATA_FIELDS) {
                            String[] values = item.getMetadata().getValues(field);
                            if (values != null) {
                                for (String value : values) {
                                    if (value != null && !value.isEmpty()) {
                                        passwords.add(value);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to collect known passwords for query: {}", query, e);
                }
            }
        }
        if (!passwords.isEmpty()) {
            logger.info("Collected {} known password(s) from the case to try before the dictionary.", passwords.size());
        }
        return new ArrayList<>(passwords);
    }
}
