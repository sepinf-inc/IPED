package iped.engine.task;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskData.TSK_FS_TYPE_ENUM;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;

public class IgnoreHardLinkTask extends AbstractTask {

    public static final String IGNORE_HARDLINK_ATTR = "ignoredHardLink"; //$NON-NLS-1$

    private static Map<Long, Map<HardLink, String>> fileSystemOrigMap = new HashMap<Long, Map<HardLink, String>>();
    private static Map<Long, Map<HardLink, String>> fileSystemSlackMap = new HashMap<Long, Map<HardLink, String>>();

    private static Object lock = new Object();
    private boolean taskEnabled = false;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        FileSystemConfig config = configurationManager.findObject(FileSystemConfig.class);
        taskEnabled = config.isIgnoreHardLinks();
    }

    @Override
    public void finish() throws Exception {
        fileSystemOrigMap = null;
        fileSystemSlackMap = null;
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (caseData.isIpedReport())
            return;

        if (!taskEnabled || evidence.getLength() == null || evidence.getLength() == 0 || evidence.isCarved()) {
            return;
        }

        if (!(evidence.getInputStreamFactory() instanceof SleuthkitInputStreamFactory)) {
            return;
        }

        SleuthkitInputStreamFactory factory = (SleuthkitInputStreamFactory) evidence.getInputStreamFactory();
        Content content = factory.getContentById(Long.valueOf(evidence.getIdInDataSource()));
        if (content != null && content instanceof FsContent) {
            FsContent fsContent = (FsContent) content;
            TSK_FS_TYPE_ENUM fsType = fsContent.getFileSystem().getFsType();
            if (!fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS)
                    && !fsType.equals(TSK_FS_TYPE_ENUM.TSK_FS_TYPE_HFS_DETECT))
                return;
            long fsId = fsContent.getFileSystemId();
            long metaAddr = fsContent.getMetaAddr();
            HardLink hardLink;
            // Testa se é AlternateDataStream ou ResourceFork
            if (!evidence.getName().contains(":")) { //$NON-NLS-1$
                hardLink = new HardLink(metaAddr);
            } else {
                hardLink = new DetailedHardLink(metaAddr, evidence.getLength(), evidence.getName());
            }

            Map<Long, Map<HardLink, String>> fileSystemMap = fileSystemOrigMap;
            if (fsContent instanceof SlackFile)
                fileSystemMap = fileSystemSlackMap;

            boolean ignore = false;

            synchronized (lock) {
                Map<HardLink, String> hardLinkMap = fileSystemMap.get(fsId);
                if (hardLinkMap == null) {
                    hardLinkMap = new HashMap<HardLink, String>();
                    fileSystemMap.put(fsId, hardLinkMap);
                }

                String id = hardLinkMap.get(hardLink);
                // test if it is not the same item from other processing queue
                if (id != null) {
                    if (!id.equals(evidence.getIdInDataSource()))
                        ignore = true;
                } else {
                    hardLinkMap.put(hardLink, evidence.getIdInDataSource());
                }
            }

            if (ignore) {
                evidence.setInputStreamFactory(new SleuthkitInputStreamFactory(factory.getSleuthkitCase(), null));
                evidence.setExtraAttribute(IGNORE_HARDLINK_ATTR, "true"); //$NON-NLS-1$
            }

        }

    }

    private class HardLink {

        long metaAddr;

        public HardLink(long meta) {
            metaAddr = meta;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DetailedHardLink) {
                return false;
            }
            return metaAddr == ((HardLink) o).metaAddr;
        }

        @Override
        public int hashCode() {
            return (int) metaAddr;
        }
    }

    // usa 2x mais RAM do que HardLink
    private class DetailedHardLink extends HardLink {

        String name;
        long size;

        public DetailedHardLink(long meta, long size, String name) {
            super(meta);
            this.size = size;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DetailedHardLink)) {
                return false;
            }
            DetailedHardLink dhl = (DetailedHardLink) o;
            return metaAddr == dhl.metaAddr && size == dhl.size && name.equals(dhl.name);
        }
    }

}
