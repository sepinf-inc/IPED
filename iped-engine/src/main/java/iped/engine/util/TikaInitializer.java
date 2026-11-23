package iped.engine.util;

import iped.content.TikaManager;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.ParsingTask;
import iped.engine.task.SignatureTask;

/**
 * Single entry point to initialize the global TikaConfig in any process that
 * has the IPED configuration loaded (processing, analysis UI, web API).
 */
public class TikaInitializer {

    private static boolean initialized = false;

    /**
     * Sets the system properties read by Tika when it is created (custom signatures
     * and parsing options) and then creates the global TikaConfig, in this order.
     * It runs only once, later calls do nothing.
     */
    public static synchronized void init(ConfigurationManager configurationManager) {
        if (!initialized) {
            SignatureTask.installCustomSignatures(configurationManager);
            ParsingTask.setupParsingOptions(configurationManager);
            TikaManager.initializeTikaConfig();
            initialized = true;
        }
    }

}
