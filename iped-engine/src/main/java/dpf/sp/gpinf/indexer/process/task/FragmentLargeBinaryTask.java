package dpf.sp.gpinf.indexer.process.task;

import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.SplitLargeBinaryConfig;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.TextCache;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import macee.core.Configurable;

/**
 * Breaks large binary files (indexed by strings) into smaller pieces to be
 * indexed.
 * 
 * @author Nassif
 *
 */
public class FragmentLargeBinaryTask extends BaseCarveTask {

    private SplitLargeBinaryConfig splitConfig;
    private IndexerDefaultParser autoParser;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new SplitLargeBinaryConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        splitConfig = configurationManager.findObject(SplitLargeBinaryConfig.class);
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

        if (evidence.getLength() != null && evidence.getLength() >= splitConfig.getMinItemSizeToFragment()
                && (!ParsingTask.hasSpecificParser(autoParser, evidence) || evidence.isTimedOut())
                && (((evidence instanceof ISleuthKitItem) && ((ISleuthKitItem) evidence).getSleuthFile() != null)
                        || evidence.getFile() != null || evidence.getInputStreamFactory() != null)) {

            int fragNum = 0;
            int fragSize = splitConfig.getItemFragmentSize();
            int overlap = splitConfig.getFragmentOverlapSize();
            for (long offset = 0; offset < evidence.getLength(); offset += fragSize - overlap) {
                long len = offset + fragSize < evidence.getLength() ? fragSize : evidence.getLength() - offset;
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
