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
import iped.engine.task.ExportFileTask;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.config.HikvisionFSConfig;

/**
 * HikvisionFSTask.
 *
 * @author guilherme.dutra
 */
public class HikvisionFSTask extends AbstractTask {

	private static final MediaType h264MediaType = MediaType.application("video/x-msvideo"); //$NON-NLS-1$
    private static final MediaType txtLogType = MediaType.parse("text/plain"); 

    private static boolean taskEnabled = false;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static final AtomicLong totalProcessed = new AtomicLong();
    private static final AtomicLong totalFailed = new AtomicLong();
    private static final AtomicLong totalTime = new AtomicLong();

    private HikvisionFSConfig hikvisionFSConfig;

    private static final Logger logger = LoggerFactory.getLogger(HikvisionFSTask.class);
		
	public static HikvisionFSExtractor HikvisionFSExtractor;

    @Override
    public boolean isEnabled() {
        return hikvisionFSConfig.isEnabled();
    }

    public boolean getExtractDataBlock(){
        return hikvisionFSConfig.getExtractDataBlock();
    }

    public boolean getExtractSystemLog(){
        return hikvisionFSConfig.getExtractSystemLog();
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new HikvisionFSConfig());
    }

    public void init(ConfigurationManager configurationManager) throws Exception {
        
        hikvisionFSConfig = configurationManager.findObject(HikvisionFSConfig.class);
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = hikvisionFSConfig.isEnabled();

                if (!taskEnabled) {
                    logger.info("Task disabled.");
                    init.set(true);
                    return;
                }

                logger.info("Task enabled.");
                init.set(true);
            }
        }

    }

    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                logger.info("Total images processed: " + totalProcessed);
                logger.info("Total images not processed: " + totalFailed);
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total != 0) {
                    logger.info("Average processing time (milliseconds): " + (totalTime.longValue() / total));
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

            boolean extractDataBlock = getExtractDataBlock();
			
			for (DataBlockEntry objDBE : HikvisionFSExtractor.getDataBlockEntryList()) {

				for (VideoFileHeader objVFH : HikvisionFSExtractor.getVideoFileHeaderList(objDBE, HikvisionFSExtractor.getDataBlockSize() )) {
					
                    if (!extractDataBlock && objVFH.getType() == VideoFileHeader.DATA_BLOCK){
                        continue;
                    }

					Item offsetFile = new Item();
					offsetFile.setName(objVFH.getName());
					offsetFile.setPath(evidence.getPath() + "/" + objVFH.getName());
					offsetFile.setLength(objVFH.getDataSize());
					offsetFile.setSumVolume(false);
					offsetFile.setParent(evidence);

					//offsetFile.setDeleted(parentEvidence.isDeleted());

                    offsetFile.setCreationDate(objVFH.getCreationDate());
                    offsetFile.setModificationDate(objVFH.getModificationDate());

					offsetFile.setMediaType(h264MediaType);

					offsetFile.setFileOffset(objVFH.getDataOffset());


					if (evidence.getIdInDataSource() != null) {
						offsetFile.setIdInDataSource(evidence.getIdInDataSource());
						offsetFile.setInputStreamFactory(evidence.getInputStreamFactory());
					}

					// optimization to not create more temp files
					if (evidence.hasTmpFile()) {
						try {
							offsetFile.setParentTmpFile(evidence.getTempFile());
							offsetFile.setParentOffset(objVFH.getDataOffset());
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
            if (getExtractSystemLog()){
                int i = 0;
                for (SystemLogHeader objSLH : HikvisionFSExtractor.getSystemLogHeaderList()) {

                    evidence.setHasChildren(true);

                    Item newItem = new Item();
                    newItem.setName(objSLH.getName());
                    newItem.setPath(evidence.getPath() + "/" + objSLH.getName());
                    newItem.setParent(evidence);               
                    newItem.setSumVolume(false);
                    newItem.setSubItem(true);
                    newItem.setSubitemId(++i);
                    
                    newItem.setCreationDate(objSLH.getCreationDate());
                    newItem.setMediaType(txtLogType);

                    ExportFileTask extractor = new ExportFileTask();
                    extractor.setWorker(worker);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(objSLH.getDescription().getBytes());
                    ByteArrayInputStream is2 = new ByteArrayInputStream(baos.toByteArray());
                    extractor.extractFile(is2, newItem, evidence.getLength());

                    totalProcessed.incrementAndGet();

                    worker.processNewItem(newItem);

                }
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



