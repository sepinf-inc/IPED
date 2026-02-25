package iped.content;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

public class TikaManager {

    private static TikaConfig tikaConfig;

    public static synchronized void initializeTikaConfig() {
        initializeTikaConfig(true, true);
    }

    @SuppressWarnings("serial")
    public static class TikaConfigAlreadyInitializedException extends IllegalStateException {
    }

    public static synchronized void initializeTikaConfig(boolean checkCustomSignatures, boolean checkIpedParsers) {
        if (tikaConfig != null) {
            throw new TikaConfigAlreadyInitializedException();
        }

        tikaConfig = TikaConfig.getDefaultConfig();

        try {

            // check if CustomSignatures.xml was loaded correctly
            if (checkCustomSignatures) {
                checkCustomSignatures();
            }

            // check if any IPED parser was loaded
            if (checkIpedParsers) {
                checkIpedParsers();
            }

        } catch (Exception e) {
            tikaConfig = null;
            throw e;
        }
    }

    public static TikaConfig getTikaConfig() {

        if (tikaConfig == null) {
            synchronized (TikaManager.class) {
                if (tikaConfig == null) {
                    throw new IllegalStateException("TikaConfig not initialized. Call TikaManager.initializeTikaConfig() before.");
                }
            }
        }

        return tikaConfig;
    }

    private static void checkCustomSignatures() {
        if (tikaConfig.getMediaTypeRegistry().getSupertype(MediaType.parse("message/x-chat-message")).equals(MediaType.OCTET_STREAM)) {
            throw new IllegalStateException("Custom signature file was not loaded!");
        }
    }

    private static void checkIpedParsers() {
        boolean hasIpedParser = false;
        for (Parser parser : ((CompositeParser) tikaConfig.getParser()).getParsers().values()) {
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
