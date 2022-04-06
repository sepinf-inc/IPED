package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class SplitLargeBinaryConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String ENABLE_PARAM = "enableSplitLargeBinary";
    private static final String CONF_FILE = "SplitLargeBinaryConfig.txt";

    private long minItemSizeToFragment = 100 * 1024 * 1024;
    private int itemFragmentSize = 10 * 1024 * 1024;
    private int fragmentOverlapSize = 1024;

    public long getMinItemSizeToFragment() {
        return minItemSizeToFragment;
    }

    public int getItemFragmentSize() {
        return itemFragmentSize;
    }

    public int getFragmentOverlapSize() {
        return fragmentOverlapSize;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("minItemSizeToFragment"); //$NON-NLS-1$
        if (value != null) {
            minItemSizeToFragment = Long.valueOf(value.trim());
        }

        value = properties.getProperty("itemFragmentSize"); //$NON-NLS-1$
        if (value != null) {
            itemFragmentSize = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("fragmentOverlapSize"); //$NON-NLS-1$
        if (value != null) {
            fragmentOverlapSize = Integer.valueOf(value.trim());
        }

    }

}
