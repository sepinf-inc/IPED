package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.search.SaveStateThread;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IOUtil.ExternalOpenEnum;

public class AnalysisConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "AnalysisConfig.txt"; //$NON-NLS-1$

    private boolean embedLibreOffice = true;
    private int searchThreads = 1;
    private boolean autoManageCols = true;
    private boolean preOpenImagesOnSleuth = false;
    private boolean openImagesCacheWarmUpEnabled = false;
    private int openImagesCacheWarmUpThreads = 255;

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

        String value = properties.getProperty("embedLibreOffice"); //$NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            embedLibreOffice = Boolean.valueOf(value);
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

    public boolean isEmbedLibreOffice() {
        return embedLibreOffice;
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
