package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.TextCache;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;

/**
 * Breaks large binary files (indexed by strings) into smaller pieces to be
 * indexed.
 * 
 * @author Nassif
 *
 */
public class FragmentLargeBinaryTask extends BaseCarveTask {

    private static final int FRAG_SIZE = 10 * 1024 * 1024;
    private static final int OVERLAP = 1024;

    private IndexerDefaultParser autoParser;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        autoParser = new IndexerDefaultParser();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isEnabled() {
        return !caseData.isIpedReport();
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        AdvancedIPEDConfig advancedConfig = ConfigurationManager.findObject(AdvancedIPEDConfig.class);

        if (evidence.getLength() != null && evidence.getLength() >= advancedConfig.getMinItemSizeToFragment()
                && (!ParsingTask.hasSpecificParser(autoParser, evidence) || evidence.isTimedOut())
                && (((evidence instanceof ISleuthKitItem) && ((ISleuthKitItem) evidence).getSleuthFile() != null)
                        || evidence.getFile() != null || evidence.getInputStreamFactory() != null)) {

            int fragNum = 0;
            for (long offset = 0; offset < evidence.getLength(); offset += FRAG_SIZE - OVERLAP) {
                long len = offset + FRAG_SIZE < evidence.getLength() ? FRAG_SIZE : evidence.getLength() - offset;
                this.addFragmentFile(evidence, offset, len, fragNum++);
                if (Thread.currentThread().isInterrupted())
                    return;
            }

            // set an empty text in parent
            TextCache textCache = new TextCache();
            ((Item) evidence).setParsedTextCache(textCache);
        }

    }

    private void addFragmentFile(IItem parentEvidence, long off, long len, int fragNum) {
        String name = parentEvidence.getName() + "_" + fragNum; //$NON-NLS-1$
        Item fragFile = getOffsetFile(parentEvidence, off, len, name, parentEvidence.getMediaType());
        configureOffsetItem(parentEvidence, fragFile, off);
        fragFile.setExtension(parentEvidence.getExt());
        fragFile.setAccessDate(parentEvidence.getAccessDate());
        if (parentEvidence.getExtraAttribute(SleuthkitReader.IN_FAT_FS) != null)
            fragFile.setExtraAttribute(SleuthkitReader.IN_FAT_FS, true);
        fragFile.setCreationDate(parentEvidence.getCreationDate());
        fragFile.setModificationDate(parentEvidence.getModDate());
        fragFile.setRecordDate(parentEvidence.getRecordDate());
        fragFile.setExtraAttribute(FILE_FRAGMENT, true);
        addOffsetFile(fragFile, parentEvidence);
    }

}
