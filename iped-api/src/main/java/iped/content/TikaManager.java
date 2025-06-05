package iped.content;

import org.apache.tika.config.TikaConfig;

public class TikaManager {

    private static TikaConfig tikaConfig;

    public static synchronized void initializeTikaConfig() {
        if (tikaConfig != null) {
            throw new IllegalStateException("TikaConfig already initialized");
        }

        tikaConfig = TikaConfig.getDefaultConfig();
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
}
