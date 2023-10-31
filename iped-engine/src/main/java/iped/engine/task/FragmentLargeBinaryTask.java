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

/**
 * Breaks large binary files (indexed by strings) into smaller pieces to be
 * indexed.
 * 
 * @author Nassif
 *
 */
public class FragmentLargeBinaryTask extends BaseCarveTask {

    // workaround for https://github.com/sepinf-inc/IPED/issues/1281
    private static final int TEXT_SPLIT_SIZE = 1 << 30;

    private SplitLargeBinaryConfig splitConfig;
    private StandardParser autoParser;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new SplitLargeBinaryConfig());
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
        // Always enabled, see https://github.com/sepinf-inc/IPED/issues/1281 and
        // https://github.com/sepinf-inc/IPED/issues/1676
        return true;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        
        boolean hasSpecificParser = ParsingTask.hasSpecificParser(autoParser, evidence);
        boolean hadParserException = Boolean.valueOf(evidence.getMetadata().get(StandardParser.PARSER_EXCEPTION));

        TextCache textCache = ((Item) evidence).getTextCache();

        if (evidence.getLength() != null && evidence.getLength() >= splitConfig.getMinItemSizeToFragment()
                && (evidence.isTimedOut()
                        || (!hasSpecificParser && (!EmbeddedDiskProcessTask.isSupported(evidence)
                                || !EmbeddedDiskProcessTask.isFirstOrUniqueImagePart(evidence) || hadParserException)))
                && evidence.getInputStreamFactory() != null
                && !evidence.getInputStreamFactory().returnsEmptyInputStream()) {

            int fragNum = 0;
            int fragSize = splitConfig.getItemFragmentSize();
            int overlap = splitConfig.getFragmentOverlapSize();
            for (long offset = 0; offset < evidence.getLength(); offset += fragSize - overlap) {
                long len = offset + fragSize < evidence.getLength() ? fragSize : evidence.getLength() - offset;
                this.addFragmentFile(evidence, offset, len, fragNum++, null);
                if (Thread.currentThread().isInterrupted())
                    return;
            }

            // set an empty text in parent
            ((Item) evidence).setParsedTextCache(new TextCache());

        } else if (textCache != null && textCache.getSize() > TEXT_SPLIT_SIZE) {
            int fragNum = 0;
            long totalTextSize = textCache.getSize();
            for (long textOffset = 0; textOffset < totalTextSize; textOffset += TEXT_SPLIT_SIZE - splitConfig.getFragmentOverlapSize()) {
                TextCache textCacheChunk = textCache.clone();
                int textSize = textOffset + TEXT_SPLIT_SIZE < totalTextSize ? TEXT_SPLIT_SIZE : (int) (totalTextSize - textOffset);
                textCacheChunk.setTextBounds(textOffset, textSize);
                this.addFragmentFile(evidence, 0, evidence.getLength(), fragNum++, textCacheChunk);
            }
            if (fragNum > 0) {
                // set an empty text in parent
                ((Item) evidence).setParsedTextCache(new TextCache());
            }
        }

    }

    private void addFragmentFile(IItem parentEvidence, long off, long len, int fragNum, TextCache textCache) {
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
        if (textCache != null) {
            fragFile.setParsedTextCache(textCache);
        }
        // avoid computing hash for fragments again
        if (parentEvidence.getHash() != null && !parentEvidence.getHash().isEmpty()) {
            fragFile.setHash("");
        }
        addOffsetFile(fragFile, parentEvidence);
    }

}
