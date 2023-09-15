package iped.engine.task.dvr.hikvision;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;
import iped.data.IItem;
import javax.imageio.ImageIO;
import iped.utils.IOUtil;
import iped.engine.util.Util;
import iped.engine.task.index.IndexItem;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.data.Item;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.task.AbstractTask;
import iped.io.SeekableInputStream;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;
import org.apache.commons.io.IOUtils;
import iped.engine.task.carver.BaseCarveTask;
/**
 * HikvisionFSTask.
 *
 * @author guilherme.dutra
 */
public class HikvisionFSTask extends AbstractTask {

    public static final String enableParam = "enableHikvisionFS"; //$NON-NLS-1$

    public static final String IMAGE_FEATURES = "imageFeatures"; //$NON-NLS-1$

	private static final MediaType h264MediaType = MediaType.application("video/x-msvideo"); //$NON-NLS-1$
    private static final MediaType txtLogType = MediaType.parse("text/plain"); 

    private static boolean taskEnabled = false;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static final AtomicLong totalProcessed = new AtomicLong();
    private static final AtomicLong totalFailed = new AtomicLong();
    private static final AtomicLong totalTime = new AtomicLong();

    //private ImageSimilarity imageSimilarity;

    private static final Logger logger = LoggerFactory.getLogger(HikvisionFSTask.class);
	
	
	public static HikvisionFSExtractor HikvisionFSExtractor;

    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(enableParam));
    }

    public void init(ConfigurationManager configurationManager) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = configurationManager.getEnableTaskProperty(enableParam);

                if (!taskEnabled) {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                logger.info("Task enabled."); //$NON-NLS-1$
                init.set(true);
            }
        }

    }

    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                logger.info("Total images processed: " + totalProcessed); //$NON-NLS-1$
                logger.info("Total images not processed: " + totalFailed); //$NON-NLS-1$
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total != 0) {
                    logger.info("Average processing time (milliseconds): " + (totalTime.longValue() / total)); //$NON-NLS-1$
                }
            }
        }
    }

    protected void process(IItem evidence) throws Exception {

        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null || (evidence.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) != null)) {
            return;
        }

        try {
			
			SeekableInputStream is = evidence.getSeekableInputStream();
						
			long t = System.currentTimeMillis();
						
			HikvisionFSExtractor = new HikvisionFSExtractor();			
			
			HikvisionFSExtractor.init(is);
			
			for (DataBlockEntry objDBE : HikvisionFSExtractor.getDataBlockEntryList()) {
			
				// Just process a few datablocks for testing ...
				//if (objDBE.dataOffset > 28991029248L)
				//	continue;	


				for (VideoFileHeader objVFH : HikvisionFSExtractor.getVideoFileHeaderList(objDBE, HikvisionFSExtractor.getDataBlockSize() )) {
					
                    // Do not process full datablock entry, this option must be optional ...
                    if (objVFH.type == 0){
                        continue;
                    }

					Item offsetFile = new Item();
					offsetFile.setName(objVFH.name);
					offsetFile.setPath(evidence.getPath() + "/" + objVFH.name);
					offsetFile.setLength(objVFH.dataSize);
					offsetFile.setSumVolume(false);
					offsetFile.setParent(evidence);

					//offsetFile.setDeleted(parentEvidence.isDeleted());

					offsetFile.setMediaType(h264MediaType);

					offsetFile.setFileOffset(objVFH.dataOffset);


					if (evidence.getIdInDataSource() != null) {
						offsetFile.setIdInDataSource(evidence.getIdInDataSource());
						offsetFile.setInputStreamFactory(evidence.getInputStreamFactory());
					}

					// optimization to not create more temp files
					if (evidence.hasTmpFile()) {
						try {
							offsetFile.setParentTmpFile(evidence.getTempFile());
							offsetFile.setParentOffset(objVFH.dataOffset);
						} catch (IOException e) {
							// ignore
						}
					}
					evidence.setHasChildren(true);

					offsetFile.setExtraAttribute(IndexItem.PARENT_TRACK_ID, Util.getTrackID(evidence));
					totalProcessed.incrementAndGet();

					worker.processNewItem(offsetFile);
					
					
				}
				
				//Free some memory - ONFI8 table can allocate GIGAS of memory
				objDBE.clear();
                				
			}
			
            //Read System Logs
            for (SystemLogHeader objSLH : HikvisionFSExtractor.getSystemLogHeaderList()) {

                Item offsetFile = new Item();
                offsetFile.setName(objSLH.name);
                offsetFile.setPath(evidence.getPath() + "/" + objSLH.name);
                offsetFile.setLength(objSLH.dataSize);
                offsetFile.setSumVolume(false);
                offsetFile.setParent(evidence);

                //offsetFile.setDeleted(parentEvidence.isDeleted());

                offsetFile.setMediaType(txtLogType);
                offsetFile.setFileOffset(objSLH.dataOffset);                

                if (evidence.getIdInDataSource() != null) {
                    offsetFile.setIdInDataSource(evidence.getIdInDataSource());
                    offsetFile.setInputStreamFactory(evidence.getInputStreamFactory());
                }

                // optimization to not create more temp files
                if (evidence.hasTmpFile()) {
                    try {
                        offsetFile.setParentTmpFile(evidence.getTempFile());
                        offsetFile.setParentOffset(objSLH.dataOffset);
                    } catch (IOException e) {
                        // ignore
                    }
                }
                evidence.setHasChildren(true);

                offsetFile.setExtraAttribute(IndexItem.PARENT_TRACK_ID, Util.getTrackID(evidence));
                totalProcessed.incrementAndGet();

                worker.processNewItem(offsetFile);

            }


					
			HikvisionFSExtractor.clear();
			HikvisionFSExtractor = null;
						
            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);			
			
			
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }

    private static boolean isImageType(MediaType mediaType) {
		return mediaType.getBaseType().equals(MediaType.application("hikvisionfs"));
    }
}



