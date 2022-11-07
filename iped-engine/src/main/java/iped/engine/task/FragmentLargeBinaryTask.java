package iped.engine.task;

import java.util.Arrays;
import java.util.List;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SplitLargeBinaryConfig;
import iped.engine.data.Item;
import iped.engine.datasource.SleuthkitReader;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.TextCache;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.MetadataUtil;

/**
 * Breaks large binary files (indexed by strings) into smaller pieces to be
 * indexed.
 * 
 * @author Nassif
 *
 */
public class FragmentLargeBinaryTask extends BaseCarveTask {

    // workaround for https://github.com/sepinf-inc/IPED/issues/1281
    private static final long MIN_SPLIT_SIZE_IF_XHTML_OR_ERROR = 1 << 30;

    private SplitLargeBinaryConfig splitConfig;
    private StandardParser autoParser;

    @Override
    public List<Configurable<?>> getConfigurables() {
        SplitLargeBinaryConfig result = ConfigurationManager.get().findObject(SplitLargeBinaryConfig.class);
        if(result == null) {
            result = new SplitLargeBinaryConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        splitConfig = configurationManager.findObject(SplitLargeBinaryConfig.class);
        autoParser = new StandardParser();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isEnabled() {
        return !caseData.isIpedReport();
    }

    public static boolean isXHtmlToSplit(IItem evidence) {
        return evidence.getLength() != null && evidence.getLength() >= MIN_SPLIT_SIZE_IF_XHTML_OR_ERROR
                && (MetadataUtil.isHtmlMediaType(evidence.getMediaType())
                        || MetadataUtil.isHtmlSubType(evidence.getMediaType()));
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        
        boolean hasSpecificParser = ParsingTask.hasSpecificParser(autoParser, evidence);
        boolean hadParserException = Boolean.valueOf(evidence.getMetadata().get(StandardParser.PARSER_EXCEPTION));

        if (evidence.getLength() != null && evidence.getLength() >= splitConfig.getMinItemSizeToFragment()
                && (evidence.isTimedOut() || isXHtmlToSplit(evidence)
                        || (hasSpecificParser && hadParserException && evidence.getLength() >= MIN_SPLIT_SIZE_IF_XHTML_OR_ERROR)
                        || (!hasSpecificParser && (!EmbeddedDiskProcessTask.isSupported(evidence)
                                || !EmbeddedDiskProcessTask.isFirstOrUniqueImagePart(evidence) || hadParserException)))
                && evidence.getInputStreamFactory() != null
                && !evidence.getInputStreamFactory().returnsEmptyInputStream()) {

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
        fragFile.setChangeDate(parentEvidence.getChangeDate());
        fragFile.setExtraAttribute(FILE_FRAGMENT, true);
        addOffsetFile(fragFile, parentEvidence);
    }

}
