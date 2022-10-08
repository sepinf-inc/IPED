package iped.engine.task.carver;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.task.ExportFileTask;
import iped.io.SeekableInputStream;
import iped.parsers.mft.MFTEntry;
import iped.properties.ExtraProperties;

public class MFTCarverTask extends BaseCarveTask {

    private static final String ENABLE_PARAM = "enableMFTCarving";

    private static Logger logger = LoggerFactory.getLogger(MFTCarverTask.class);
    private static boolean taskEnabled = false;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);
    private static final AtomicInteger numCarvedItems = new AtomicInteger();

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
                logger.info("Carved Items: " + numCarvedItems.get());
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
                    if (entry.hasResidentContent()) {
                        IItem item = evidence.createChildItem();

                        item.setName(entry.getName());
                        item.setPath(evidence.getPath() + ">>" + entry.getName());

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
                        evidence.setExtraAttribute(NUM_CARVED, 1);
                        evidence.setExtraAttribute(NUM_CARVED_AND_FRAGS, 1);
                        evidence.setHasChildren(true);

                        byte[] content = entry.getResidentContent(bytes);
                        ByteArrayInputStream bais = new ByteArrayInputStream(content);

                        ExportFileTask extractor = new ExportFileTask();
                        extractor.setWorker(worker);
                        extractor.extractFile(bais, item, evidence.getLength());

                        numCarvedItems.incrementAndGet();
                        worker.processNewItem(item);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }
}