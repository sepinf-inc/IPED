package iped.engine.lucene;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;

public class ConfiguredFSDirectory {

    private static Logger LOGGER = LoggerFactory.getLogger(ConfiguredFSDirectory.class);

    public static FSDirectory open(File indexDir) throws IOException {
        IndexTaskConfig config = ConfigurationManager.get().findObject(IndexTaskConfig.class);

        FSDirectory result;
        if (config != null && config.isUseNIOFSDirectory()) {
            result = new NIOFSDirectory(indexDir.toPath());
        } else {
            result = FSDirectory.open(indexDir.toPath());
        }
        LOGGER.info("Using " + result.getClass().getSimpleName() + " to open index...");
        return result;

    }

}
