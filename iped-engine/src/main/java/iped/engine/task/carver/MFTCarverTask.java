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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String ENABLE_PARAM = "enableMFTCarving";

    private static Logger logger = LoggerFactory.getLogger(MFTCarverTask.class);
    private static boolean taskEnabled = false;
    private static boolean extractResidentContent = true; // TODO: Parameter
    private static boolean extractNonResidentContent = true; // TODO: Parameter
    private static final long maxNonResidentLenToExtract = 1_000_000_000; // TODO: Parameter
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);
    private static final AtomicInteger numCarvedItems = new AtomicInteger();
    private static final AtomicInteger numResidentContent = new AtomicInteger();
    private static final AtomicInteger numNonResidentContent = new AtomicInteger();
    private static final AtomicLong totLengthResidentContent = new AtomicLong();
    private static final AtomicLong totLengthNonResidentContent = new AtomicLong();
    private static final Map<Integer, IItemReader> volumesMap = new HashMap<Integer, IItemReader>();

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
                taskEnabled = true;// TODO: configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                if (taskEnabled) {
                    logger.info("Task enabled.");
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
                logger.info("Carved Items: " + df.format(numCarvedItems.get()));
                logger.info("Resident Content: " + df.format(numResidentContent.get()) + " ("
                        + df.format(totLengthResidentContent.get()) + " bytes)");
                logger.info("Non Resident Content: " + df.format(numNonResidentContent.get()) + " ("
                        + df.format(totLengthNonResidentContent.get()) + " bytes)");
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
        
            numResidentContent.incrementAndGet();
            totLengthResidentContent.accumulateAndGet(entry.getLength(), null);

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
            List<Long> dataruns = entry.getDataruns();
            for (int i = 0; i < dataruns.size(); i += 2) {
                long pos = dataruns.get(i) * cluster;
                long len = dataruns.get(i + 1) * cluster;
                if (pos + len > volumeLen) {
                    return;
                }
            }

            // Write data runs to a temporary file
            File tmpFile = null;
            SeekableInputStream vis = null;
            FileOutputStream os = null;
            try {
                vis = volume.getSeekableInputStream();
                tmpFile = Files.createTempFile("mftcarved", ".tmp").toFile();
                os = new FileOutputStream(tmpFile);
                byte[] buf = new byte[1 << 16];
                long totPending = entry.getLength();
                for (int i = 0; i < dataruns.size(); i += 2) {
                    long runPos = dataruns.get(i) * cluster;
                    long runLen = dataruns.get(i + 1) * cluster;
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
            //TODO: When the temporary file can be deleted?

            // if (dataruns.size() == 2) {
            // A single pair {position, length}, i.e. a continuous (non-fragmented) file
            // }
            numNonResidentContent.incrementAndGet();
            totLengthNonResidentContent.accumulateAndGet(entry.getLength(), null);
        }

        numCarvedItems.incrementAndGet();
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

        ItemSearcher searcher = new ItemSearcher(output.getParentFile(), Manager.getInstance().getIndexWriter());
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
        List<IItemReader> volumes = searcher.search(sb.toString());
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
        return null;
    }
}
