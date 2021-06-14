package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

import java.nio.file.Path;

public class FileSystemConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "FileSystemConfig.txt";

    private boolean toAddUnallocated = false;
    private boolean toAddFileSlacks = false;
    private boolean robustImageReading;
    private int numImageReaders = (int) Math.ceil((float) Runtime.getRuntime().availableProcessors() / 4);
    private long unallocatedFragSize = 1 << 30;
    private long minOrphanSizeToIgnore = -1;
    private boolean ignoreHardLinks = true;
    private String skipFolderRegex = "";

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
    public void processProperties(UTF8Properties properties) {

        String value = properties.getProperty("addUnallocated"); // $NON-NLS-1$
        if (value != null) {
            toAddUnallocated = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("addFileSlacks"); // $NON-NLS-1$
        if (value != null) {
            toAddFileSlacks = Boolean.valueOf(value.trim());
        }
        
        value = properties.getProperty("robustImageReading"); //$NON-NLS-1$
        if (value != null) {
            robustImageReading = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("numImageReaders"); //$NON-NLS-1$
        if (value != null && !(value = value.trim()).equalsIgnoreCase("auto")) {
            numImageReaders = Integer.valueOf(value);
        }

        value = properties.getProperty("unallocatedFragSize"); //$NON-NLS-1$
        if (value != null) {
            unallocatedFragSize = Long.valueOf(value.trim());
        }

        value = properties.getProperty("minOrphanSizeToIgnore"); //$NON-NLS-1$
        if (value != null) {
            minOrphanSizeToIgnore = Long.valueOf(value.trim());
        }

        value = properties.getProperty("ignoreHardLinks"); //$NON-NLS-1$
        if (value != null) {
            ignoreHardLinks = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("skipFolderRegex"); //$NON-NLS-1$
        if (value != null) {
            skipFolderRegex = value.trim();
        }

    }

    public String getSkipFolderRegex() {
        return skipFolderRegex;
    }

    public boolean isToAddUnallocated() {
        return toAddUnallocated;
    }

    public boolean isToAddFileSlacks() {
        return toAddFileSlacks;
    }

    public boolean isRobustImageReading() {
        return robustImageReading;
    }

    public int getNumImageReaders() {
        return numImageReaders;
    }

    public long getUnallocatedFragSize() {
        return unallocatedFragSize;
    }

    public long getMinOrphanSizeToIgnore() {
        return minOrphanSizeToIgnore;
    }

    public boolean isIgnoreHardLinks() {
        return ignoreHardLinks;
    }

}
