package iped.content;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

public class TikaManager {

    private static volatile TikaConfig tikaConfig;

    public static void initializeTikaConfig() {
        initializeTikaConfig(true, true);
    }

    /**
     * Creates the global TikaConfig. It must be called after the system properties
     * used by Tika (custom signatures and parsers configuration) were set. It does
     * nothing if the TikaConfig was already initialized, e.g. when the analysis UI
     * is opened in the same process that is running the processing.
     */
    public static synchronized void initializeTikaConfig(boolean checkCustomSignatures, boolean checkIpedParsers) {
        if (tikaConfig != null) {
            return;
        }

        TikaConfig config = TikaConfig.getDefaultConfig();

        // check if CustomSignatures.xml was loaded correctly
        if (checkCustomSignatures) {
            checkCustomSignatures(config);
        }

        // check if any IPED parser was loaded
        if (checkIpedParsers) {
            checkIpedParsers(config);
        }

        // only publish the config after it was validated
        tikaConfig = config;
    }

    public static TikaConfig getTikaConfig() {
        TikaConfig config = tikaConfig;
        if (config == null) {
            throw new IllegalStateException("TikaConfig not initialized. Call TikaManager.initializeTikaConfig() before.");
        }
        return config;
    }

    private static void checkCustomSignatures(TikaConfig config) {
        if (config.getMediaTypeRegistry().getSupertype(MediaType.parse("message/x-chat-message")).equals(MediaType.OCTET_STREAM)) {
            throw new IllegalStateException("Custom signature file was not loaded!");
        }
    }

    private static void checkIpedParsers(TikaConfig config) {
        boolean hasIpedParser = false;
        for (Parser parser : ((CompositeParser) config.getParser()).getParsers().values()) {
            if (parser.getClass().getName().startsWith("iped.parsers.")) {
                hasIpedParser = true;
                break;
            }
        }
        if (!hasIpedParser) {
            throw new IllegalStateException("No IPED Parser was loaded!");
        }
    }

}
