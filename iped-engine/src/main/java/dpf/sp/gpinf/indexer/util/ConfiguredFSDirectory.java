package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;

public class ConfiguredFSDirectory {

    private static Logger LOGGER = LoggerFactory.getLogger(ConfiguredFSDirectory.class);

    public static FSDirectory open(File indexDir) throws IOException {
        AdvancedIPEDConfig advConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();

        FSDirectory result;
        if (advConfig.isUseNIOFSDirectory()) {
            result = new NIOFSDirectory(indexDir);
        } else {
            result = FSDirectory.open(indexDir);
        }
        LOGGER.info("Using " + result.getClass().getSimpleName() + " to open index...");
        return result;

    }

}
