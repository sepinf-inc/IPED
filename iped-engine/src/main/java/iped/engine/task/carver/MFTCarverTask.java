package iped.engine.task.carver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.carvers.api.CarverConfiguration;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.core.Manager;
import iped.engine.search.ItemSearcher;
import iped.engine.task.ExportFileTask;
import iped.io.SeekableInputStream;
import iped.parsers.mft.MFTEntry;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;

public class MFTCarverTask extends BaseCarveTask {

    protected static final String ENABLE_PARAM = "enableMFTCarving";

    private static Logger logger = LoggerFactory.getLogger(MFTCarverTask.class);
    private static boolean taskEnabled = false;
    private static boolean extractResidentContent = false;
    private static boolean extractNonResidentContent = false;
    private static long maxNonResidentLenToExtract = 0;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);
    private static int numCarvedItems;
    private static int numResidentContent;
    private static int numNonResidentContent;
    private static long totLengthResidentContent;
    private static long totLengthNonResidentContent;
    private static final Object statsLock = new Object();
    private static final Map<Integer, IItemReader> volumesMap = new HashMap<Integer, IItemReader>();
    private static final Set<String> recentQueries = new HashSet<String>();
    private static final LinkedList<String> recentQueriesOrder = new LinkedList<String>();
    private static final int maxRecentQueries = 1024;

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                if (taskEnabled) {
                    logger.info("Task enabled.");
                    CarverTaskConfig ctConfig = configurationManager.findObject(CarverTaskConfig.class);
                    CarverConfiguration config = ctConfig.getConfiguration();
                    extractResidentContent = config.isExtractResidentContent();
                    extractNonResidentContent = config.isExtractNonResidentContent();
                    maxNonResidentLenToExtract = config.getMaxNonResidentLenToExtract();
                    logger.info("extractResidentContent = " + extractResidentContent);
                    logger.info("extractNonResidentContent = " + extractNonResidentContent);
                    if (extractNonResidentContent) {
                        logger.info("maxNonResidentLenToExtract = " + maxNonResidentLenToExtract);
                    }

                } else {
                    logger.info("Task disabled.");
                }
                init.set(true);
            }
        }
    }

    @Override
    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                DecimalFormat df = new DecimalFormat("#,##0");
                logger.info("Carved Items: " + df.format(numCarvedItems));
                logger.info("Resident Content: " + df.format(numResidentContent) + " ("
                        + df.format(totLengthResidentContent) + " bytes)");
                logger.info("Non Resident Content: " + df.format(numNonResidentContent) + " ("
                        + df.format(totLengthNonResidentContent) + " bytes)");
            }
        }
    }

    @Override
    public void process(IItem evidence) {
        if (!taskEnabled || caseData.isIpedReport() || !evidence.getMediaType().toString().equals(MFTEntry.MIME_TYPE)
                || !evidence.isCarved())
            return;

        try (SeekableInputStream is = evidence.getSeekableInputStream()) {
            byte[] bytes = is.readAllBytes();
            if (bytes != null && bytes.length == MFTEntry.entryLength) {
                MFTEntry entry = MFTEntry.parse(bytes);
                if (entry != null && entry.isFile()) {
                    if ((entry.hasResidentContent() && extractResidentContent) || (entry.hasNonResidentContent()
                            && extractNonResidentContent
                            && (maxNonResidentLenToExtract <= 0 || entry.getLength() <= maxNonResidentLenToExtract))) {
                        extract(evidence, entry, bytes);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }

    private void extract(IItem parent, MFTEntry entry, byte[] bytes) throws Exception {
        IItem item = parent.createChildItem();

        item.setName(entry.getName());
        item.setPath(parent.getPath() + ">>" + entry.getName());

        item.setDeleted(!entry.isActive());
        item.setCreationDate(entry.getCreationDate());
        item.setModificationDate(entry.getLastModificationDate());
        item.setChangeDate(entry.getLastEntryModificationDate());
        item.setAccessDate(entry.getLastAccessDate());
        item.setLength(entry.getLength());

        item.setExtraAttribute(CARVED_ID, 0);
        item.setSumVolume(false);
        item.setCarved(true);

        item.getMetadata().add(ExtraProperties.CARVEDBY_METADATA_NAME, getClass().getName());
        parent.setExtraAttribute(NUM_CARVED, 1);
        parent.setExtraAttribute(NUM_CARVED_AND_FRAGS, 1);
        parent.setHasChildren(true);

        if (entry.hasResidentContent()) {
            byte[] content = entry.getResidentContent(bytes);
            InputStream is = new ByteArrayInputStream(content);

            ExportFileTask extractor = new ExportFileTask();
            extractor.setWorker(worker);
            extractor.extractFile(is, item, parent.getLength());

            synchronized (statsLock) {
                numCarvedItems++;
                numResidentContent++;
                totLengthResidentContent += entry.getLength();
            }
        } else {
            IItemReader volume = findVolume(parent);
            if (volume == null) {
                return;
            }
            Long volumeLen = volume.getLength();
            if (volumeLen == null) {
                return;
            }

            // Check if data runs are inside the volume
            int cluster = entry.getClusterLength();
            if (cluster <= 0) {
                return;
            }
            long[] dataruns = entry.getDataruns();
            for (int i = 0; i < dataruns.length; i += 2) {
                long pos = dataruns[i] * cluster;
                long len = dataruns[i + 1] * cluster;
                if (pos + len > volumeLen) {
                    return;
                }
            }

            if (dataruns.length == 2 && volume.getIdInDataSource() != null) {
                // A single pair {position, length} -> a continuous (non-fragmented) file
                // In this case, just point to volume's data source and set the file offset
                item.setDataSource(volume.getDataSource());
                item.setIdInDataSource(volume.getIdInDataSource());
                item.setInputStreamFactory(volume.getInputStreamFactory());
                item.setFileOffset(dataruns[0] * cluster);
            } else {
                // Fragmented -> Write data runs to a temporary file
                File tmpFile = null;
                SeekableInputStream vis = null;
                FileOutputStream os = null;
                try {
                    vis = volume.getSeekableInputStream();
                    tmpFile = Files.createTempFile("mftcarved", ".tmp").toFile();
                    os = new FileOutputStream(tmpFile);
                    byte[] buf = new byte[1 << 16];
                    long totPending = entry.getLength();
                    for (int i = 0; i < dataruns.length; i += 2) {
                        long runPos = dataruns[i] * cluster;
                        long runLen = dataruns[i + 1] * cluster;
                        vis.seek(runPos);
                        long runRead = 0;
                        while (runRead < runLen && totPending > 0) {
                            int cnt = Math.min(buf.length, (int) Math.min(runLen - runRead, totPending));
                            int read = vis.read(buf, 0, cnt);
                            if (read < 0) {
                                break;
                            }
                            os.write(buf, 0, read);
                            runRead += read;
                            totPending -= read;
                        }
                    }
                } finally {
                    IOUtil.closeQuietly(os);
                    IOUtil.closeQuietly(vis);
                }
                InputStream is = null;
                try {
                    is = new FileInputStream(tmpFile);
                    ExportFileTask extractor = new ExportFileTask();
                    extractor.setWorker(worker);
                    extractor.extractFile(is, item, parent.getLength());
                } finally {
                    IOUtil.closeQuietly(is);
                }

                // TODO: When / where the temporary file can be deleted?
            }

            synchronized (statsLock) {
                numCarvedItems++;
                numNonResidentContent++;
                totLengthNonResidentContent += entry.getLength();
            }
        }

        worker.processNewItem(item);
    }

    private IItemReader findVolume(IItem parent) throws Exception {
        List<Integer> ids = parent.getParentIds();
        synchronized (volumesMap) {
            for (int id : ids) {
                IItemReader volume = volumesMap.get(id);
                if (volume != null) {
                    return volume;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(BasicProps.ID).append(":(");
        for (int id : ids) {
            sb.append(id).append(" ");
        }
        sb.append(") && ");
        sb.append(BasicProps.CONTENTTYPE);
        sb.append(":\"");
        sb.append(QueryParserUtil.escape(MediaTypes.DISK_VOLUME.toString()));
        sb.append("\"");
        String query = sb.toString();
        synchronized (recentQueries) {
            if (recentQueries.contains(query)) {
                return null;
            }
        }
        try (ItemSearcher searcher = new ItemSearcher(output.getParentFile(), Manager.getInstance().getIndexWriter())) {
            List<IItemReader> volumes = searcher.search(query);
            if (!volumes.isEmpty()) {
                IItemReader volume = volumes.get(0);
                synchronized (volumesMap) {
                    if (volumesMap.put(volume.getId(), volume) == null) {
                        logger.info("Volume found: ID=" + volume.getId() + ", Path=" + volume.getPath());
                    }
                }
                return volume;
            }
            logger.info("No volume found for parentIds: " + ids);
            synchronized (recentQueries) {
                recentQueries.add(query);
                recentQueriesOrder.add(query);
                if (recentQueriesOrder.size() > maxRecentQueries) {
                    String oldest = recentQueriesOrder.poll();
                    recentQueries.remove(oldest);
                }
            }
        }
        return null;
    }
}
