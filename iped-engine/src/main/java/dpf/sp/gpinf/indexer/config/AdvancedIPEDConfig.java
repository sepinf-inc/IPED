package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.search.SaveStateThread;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IOUtil.ExternalOpenEnum;

public class AdvancedIPEDConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    long minItemSizeToFragment = 100 * 1024 * 1024;

    boolean embutirLibreOffice = true;
    int searchThreads = 1;
    boolean autoManageCols = true;

    boolean preOpenImagesOnSleuth = false;
    boolean openImagesCacheWarmUpEnabled = false;
    int openImagesCacheWarmUpThreads = 255;
    int commitIntervalSeconds = 1800;

    public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = null;

        value = properties.getProperty("minItemSizeToFragment"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            minItemSizeToFragment = Long.valueOf(value);
        }

        value = properties.getProperty("embutirLibreOffice"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            embutirLibreOffice = Boolean.valueOf(value);
        }

        value = properties.getProperty("searchThreads"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            searchThreads = Integer.valueOf(value);
        }

        value = properties.getProperty("maxBackups"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            SaveStateThread.MAX_BACKUPS = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("backupInterval"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            SaveStateThread.BKP_INTERVAL = Long.valueOf(value.trim());
        }

        value = properties.getProperty("autoManageCols"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            autoManageCols = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("preOpenImagesOnSleuth"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            preOpenImagesOnSleuth = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("openImagesCacheWarmUpEnabled"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            openImagesCacheWarmUpEnabled = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("openImagesCacheWarmUpThreads"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            openImagesCacheWarmUpThreads = Integer.parseInt(value.trim());
        }

        value = properties.getProperty("commitIntervalSeconds"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            commitIntervalSeconds = Integer.parseInt(value.trim());
        }

        value = properties.getProperty("openWithDoubleClick"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            value = value.trim();
            if (ExternalOpenEnum.ALWAYS.toString().equalsIgnoreCase(value))
                IOUtil.setExternalOpenConfig(ExternalOpenEnum.ALWAYS);
            else if (ExternalOpenEnum.ASK_ALWAYS.toString().equalsIgnoreCase(value))
                IOUtil.setExternalOpenConfig(ExternalOpenEnum.ASK_ALWAYS);
            else if (ExternalOpenEnum.ASK_IF_EXE.toString().equalsIgnoreCase(value))
                IOUtil.setExternalOpenConfig(ExternalOpenEnum.ASK_IF_EXE);
            else if (ExternalOpenEnum.NEVER.toString().equalsIgnoreCase(value))
                IOUtil.setExternalOpenConfig(ExternalOpenEnum.NEVER);
        }

    }

    public int getCommitIntervalSeconds() {
        return commitIntervalSeconds;
    }

    public long getMinItemSizeToFragment() {
        return minItemSizeToFragment;
    }

    public boolean isEmbutirLibreOffice() {
        return embutirLibreOffice;
    }

    public int getSearchThreads() {
        return searchThreads;
    }

    public boolean isAutoManageCols() {
        return autoManageCols;
    }

    public boolean isPreOpenImagesOnSleuth() {
        return preOpenImagesOnSleuth;
    }

    public boolean isOpenImagesCacheWarmUpEnabled() {
        return openImagesCacheWarmUpEnabled;
    }

    public int getOpenImagesCacheWarmUpThreads() {
        return openImagesCacheWarmUpThreads;
    }

}
