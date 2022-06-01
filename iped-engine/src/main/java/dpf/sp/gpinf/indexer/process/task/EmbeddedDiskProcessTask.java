package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.EnableTaskProperty;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.search.ItemSearcher;
import dpf.sp.gpinf.indexer.util.FileInputStreamFactory;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.TextCache;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.IItemBase;
import iped3.configuration.Configurable;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.MediaTypes;

public class EmbeddedDiskProcessTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(EmbeddedDiskProcessTask.class);

    private static final int MIN_DD_SIZE = 1024;

    private static final String ENABLE_PARAM = "processEmbeddedDisks";

    private static final String outputFolder = "embeddedDisks";

    private static Set<MediaType> supportedMimes = MediaType.set(MediaTypes.VMDK, MediaTypes.VMDK_DATA,
            MediaTypes.VMDK_DESCRIPTOR, MediaTypes.VHD, MediaTypes.VHDX, MediaTypes.RAW_IMAGE, MediaTypes.EWF_IMAGE,
            MediaTypes.E01_IMAGE, MediaTypes.EWF2_IMAGE, MediaTypes.EX01_IMAGE);

    private static Set<File> exportedDisks = Collections.synchronizedSet(new HashSet<>());

    private boolean enabled = true;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        enabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
    }

    @Override
    public void finish() throws Exception {
        if (caseData.containsReport()) {
            exportedDisks.stream().forEach(f -> f.delete());
        }

    }

    public static boolean isSupported(IItem item) {
        return item.getLength() != null && item.getLength() > 0 && !item.isRoot()
                && supportedMimes.contains(item.getMediaType())
                && item.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) == null;
    }

    private static boolean isFirstOrUniqueImagePart(IItem item) {
        return MediaTypes.E01_IMAGE.equals(item.getMediaType())
                || MediaTypes.EX01_IMAGE.equals(item.getMediaType())
                || MediaTypes.RAW_IMAGE.equals(item.getMediaType())
                || MediaTypes.VMDK_DESCRIPTOR.equals(item.getMediaType());
    }

    @Override
    protected void process(IItem item) throws Exception {

        if (!isSupported(item)) {
            return;
        }

        if (isFirstOrUniqueImagePart(item)) {
            if (MediaTypes.RAW_IMAGE.equals(item.getMediaType())) {
                // skip some false positives like $MBR files
                if (item.getLength() < MIN_DD_SIZE) {
                    return;
                }
                // look up for DD parts
                ItemSearcher searcher = (ItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
                int dotIdx = item.getName().lastIndexOf(".");
                if (dotIdx == -1)
                    dotIdx = item.getName().length();
                String query = BasicProps.PARENTID + ":" + item.getParentId() + " && " + BasicProps.NAME + ":\""
                        + QueryParserUtil.escape(item.getName().substring(0, dotIdx)) + "\"";
                List<IItemBase> possibleParts = searcher.search(query);
                logger.info("Found {} possible image segments of {}", possibleParts.size(), item.getPath());
                for (IItemBase possiblePart : possibleParts) {
                    // export DD parts
                    exportItem(possiblePart);
                }
            }

        } else if (MediaTypes.EWF_IMAGE.equals(item.getMediaType())
                || MediaTypes.EWF2_IMAGE.equals(item.getMediaType())
                || MediaTypes.VMDK_DATA.equals(item.getMediaType())) {
            // export e01/vmdk parts to process them later
            exportItem(item);
            return;
        }

        // export first part if not done
        File imageFile = exportItem(item);

        try (SleuthkitReader reader = new SleuthkitReader(true, caseData, output)) {
            logger.info("Decoding embedded disk image {} -> {}", item.getPath(), imageFile.getAbsolutePath());
            reader.read(imageFile, (Item) item);
            int numSubitems = reader.getItemCount();
            if (numSubitems > 0) {
                item.setHasChildren(true);
                item.setExtraAttribute(ParsingTask.HAS_SUBITEM, Boolean.TRUE.toString());
                item.setExtraAttribute(ParsingTask.NUM_SUBITEMS, numSubitems);
            }
            if (reader.hasDecodingError()) {
                item.getMetadata().set(IndexerDefaultParser.PARSER_EXCEPTION, Boolean.TRUE.toString());
            } else {
                ((Item) item).setParsedTextCache(new TextCache());
            }
        }

    }

    private File exportItem(IItemBase item) throws IOException {
        File imageFile = null;
        if (item instanceof IItem && IOUtil.hasFile((IItem) item)) {
            imageFile = IOUtil.getFile((IItem) item);
        } else {
            File exportDir = new File(new File(this.output, outputFolder), item.getParentId().toString());
            exportDir.mkdirs();
            imageFile = new File(exportDir, item.getName());
            boolean alreadyExported = false;
            if (imageFile.exists()) {
                if (imageFile.length() != item.getLength()) {
                    logger.info("Deleting incomplete exported item {} -> {}", item.getPath(),
                            imageFile.getAbsolutePath());
                    Files.delete(imageFile.toPath());
                } else {
                    alreadyExported = true;
                }
            }
            if (!alreadyExported) {
                logger.info("Exporting item {} -> {}", item.getPath(), imageFile.getAbsolutePath());
                try (InputStream is = item.getBufferedInputStream()) {
                    Files.copy(is, imageFile.toPath());
                }
            }
            exportedDisks.add(imageFile);
        }
        return imageFile;
    }

}
