package iped.engine.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.utils.UTF8Properties;

/**
 * Configuration for password cracking behaviour (e.g. DPAPI master key cracking
 * performed by the Zoom parser).
 */
public class PasswordCrackingConfig extends AbstractPropertiesConfigurable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(PasswordCrackingConfig.class);

    public static final String CONFIG_FILE = "PasswordCrackingConfig.txt";

    private static final String CRACK_TIMEOUT_SECONDS = "crackTimeoutSeconds";
    private static final String WARN_DICTIONARY_SIZE_MB = "warnDictionarySizeMB";
    private static final String CRACKING_THREADS = "crackingThreads";

    /** Value that selects the automatic parallelism (half of the available processors). */
    private static final String CRACKING_THREADS_AUTO = "default";
    private static final int AUTO = -1;

    private long crackTimeoutSeconds = 300;
    private long warnDictionarySizeMB = 100;
    /** {@link #AUTO} means "default": use half of the available processors. */
    private int crackingThreads = AUTO;

    public long getCrackTimeoutSeconds() {
        return crackTimeoutSeconds;
    }

    /**
     * @return the crack time limit in milliseconds, or 0 if there is no limit.
     */
    public long getCrackTimeoutMillis() {
        return crackTimeoutSeconds > 0 ? crackTimeoutSeconds * 1000L : 0;
    }

    /**
     * @return the wordlist size (in bytes) above which a warning is logged, or 0
     *         if the warning is disabled.
     */
    public long getWarnDictionaryBytes() {
        return warnDictionarySizeMB > 0 ? warnDictionarySizeMB * 1024L * 1024L : 0;
    }

    /**
     * @return the number of parallel threads to use when cracking. When set to
     *         "default", uses half of the available processors, with a minimum of
     *         1.
     */
    public int getCrackingThreads() {
        if (crackingThreads > 0) {
            return crackingThreads;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE);
            }
        };
    }

    @Override
    void processProperties(UTF8Properties properties) {
        crackTimeoutSeconds = parseLong(properties.getProperty(CRACK_TIMEOUT_SECONDS), crackTimeoutSeconds);
        warnDictionarySizeMB = parseLong(properties.getProperty(WARN_DICTIONARY_SIZE_MB), warnDictionarySizeMB);
        crackingThreads = parseCrackingThreads(properties.getProperty(CRACKING_THREADS));
    }

    private static long parseLong(String value, long defaultValue) {
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                // keep default
            }
        }
        return defaultValue;
    }

    /**
     * Parses the {@code crackingThreads} property. Accepts a positive integer or
     * the keyword "default" (half of the available processors). Any other value
     * (including 0 or a negative number) is rejected with an error log and falls
     * back to "default".
     */
    private static int parseCrackingThreads(String value) {
        if (value == null || value.trim().isEmpty() || CRACKING_THREADS_AUTO.equalsIgnoreCase(value.trim())) {
            return AUTO;
        }
        try {
            int threads = Integer.parseInt(value.trim());
            if (threads > 0) {
                return threads;
            }
        } catch (NumberFormatException e) {
            // fall through to the error log below
        }
        logger.error("Invalid '{}' value '{}' in {}: it must be a positive integer or '{}' "
                + "(half of the available processors). Using '{}'.", CRACKING_THREADS, value, CONFIG_FILE,
                CRACKING_THREADS_AUTO, CRACKING_THREADS_AUTO);
        return AUTO;
    }
}
