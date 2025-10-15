package iped.engine.task.dvr;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
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
import iped.engine.config.DVRTaskConfig;

import iped.engine.task.dvr.hikvision.*;
import iped.engine.task.dvr.wfs.*;
import iped.engine.task.dvr.dhfs.*;


/**
 * DVRTask.
 *
 * @author guilherme.dutra
 */
public class DVRTask extends AbstractTask {

    protected static MediaType HVFS_MEDIA_TYPE = MediaType.application("hikvisionfs"); //$NON-NLS-1$
    protected static MediaType WFS_MEDIA_TYPE = MediaType.application("wfs"); //$NON-NLS-1$
    protected static MediaType DHFS_MEDIA_TYPE = MediaType.application("dhfs"); //$NON-NLS-1$

	private static final MediaType h264MediaType = MediaType.application("video/x-msvideo"); //$NON-NLS-1$
    private static final MediaType txtLogType = MediaType.parse("text/plain"); 

    private static boolean taskEnabled = false;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static final AtomicLong totalProcessed = new AtomicLong();
    private static final AtomicLong totalFailed = new AtomicLong();
    private static final AtomicLong totalTime = new AtomicLong();

    private DVRTaskConfig DVRTaskConfig;

    private static final Logger logger = LoggerFactory.getLogger(DVRTask.class);
		
    public static enum FSType {
        HVFS, WFS, DHFS, NONE
    }

    @Override
    public boolean isEnabled() {
        return DVRTaskConfig.isEnabled();
    }

    public boolean getExtractDataBlockHVFS(){
        return DVRTaskConfig.getExtractDataBlockHVFS();
    }

    public boolean getExtractSystemLogsHVFS(){
        return DVRTaskConfig.getExtractSystemLogsHVFS();
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new DVRTaskConfig());
    }

    public void init(ConfigurationManager configurationManager) throws Exception {
        
        DVRTaskConfig = configurationManager.findObject(DVRTaskConfig.class);
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = DVRTaskConfig.isEnabled();

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

        FSType fsType = getFsType(evidence.getMediaType());

        if (!taskEnabled || !isAcceptedType(fsType) || !evidence.isToAddToCase()
                || evidence.getHash() == null || (evidence.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) != null)) {
            return;
        }
     
        switch(fsType){
            case HVFS:
                HVFSProcess(evidence);
                break;
            case WFS:
                WFSProcess(evidence);
                break;
            case DHFS:
                DHFSProcess(evidence);
                break;                
            case NONE:
            default:
                break;
        }
        

    }

    protected void HVFSProcess(IItem evidence) throws Exception {
        try {
			
			SeekableInputStream is = evidence.getSeekableInputStream();
						
			long t = System.currentTimeMillis();
						
			HikvisionFSExtractor HikvisionFSExtractor = new HikvisionFSExtractor();			
			
			HikvisionFSExtractor.init(is);

            boolean extractDataBlock = getExtractDataBlockHVFS();
			
			for (DataBlockEntry objDBE : HikvisionFSExtractor.getDataBlockEntryList()) {

				for (VideoFileHeader objVFH : HikvisionFSExtractor.getVideoFileHeaderList(objDBE, HikvisionFSExtractor.getDataBlockSize() )) {
					
                    if (!extractDataBlock && objVFH.getType() == VideoFileHeader.DATA_BLOCK){
                        continue;
                    }

					Item item = new Item();
					item.setName(objVFH.getName());
					item.setPath(evidence.getPath() + "/" + objVFH.getName());
					item.setLength(objVFH.getDataSize());
					item.setSumVolume(false);
					item.setParent(evidence);

					//item.setDeleted(parentEvidence.isDeleted());

                    item.setCreationDate(objVFH.getCreationDate());
                    item.setModificationDate(objVFH.getModificationDate());

					item.setMediaType(h264MediaType);

					item.setFileOffset(objVFH.getDataOffset());


					if (evidence.getIdInDataSource() != null) {
						item.setIdInDataSource(evidence.getIdInDataSource());
						item.setInputStreamFactory(evidence.getInputStreamFactory());
					}

					// optimization to not create more temp files
					if (evidence.hasTmpFile()) {
						try {
							item.setParentTmpFile(evidence.getTempFile(), (Item) evidence);
							item.setParentOffset(objVFH.getDataOffset());
						} catch (IOException e) {
							// ignore
                            ;
						}
					}
					evidence.setHasChildren(true);

					item.setExtraAttribute(IndexItem.PARENT_TRACK_ID, Util.getTrackID(evidence));
					totalProcessed.incrementAndGet();

					worker.processNewItem(item);
					
					
				}
				
				//Free some memory - ONFI8 table can allocate GIGAS of memory
				objDBE.clear();
                				
			}
			

            //Read System Logs            
            if (getExtractSystemLogsHVFS()){
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

    protected void WFSProcess(IItem evidence) throws Exception {

        try {
			
			SeekableInputStream is = evidence.getSeekableInputStream();
						
			long t = System.currentTimeMillis();

					
			WFSExtractor WFSExtractor = new WFSExtractor();			
			
			WFSExtractor.init(is);	

            ArrayList<Descriptor> descriptionList = WFSExtractor.getDescriptorList();                        

            for (Descriptor objVFH : descriptionList) {
                    
                if (objVFH.isVideoDescriptor()){

                    Item item = new Item();
                    item.setName(objVFH.getName());
                    item.setPath(evidence.getPath() + "/" + objVFH.getName());
                    item.setLength(objVFH.getLength());
                    item.setSumVolume(false);
                    item.setParent(evidence);                  

                    item.setCreationDate(objVFH.getCreationDate());
                    item.setModificationDate(objVFH.getModificationDate());
                    item.setMediaType(h264MediaType);

                    //item.setFileOffset(0);

                    if (evidence.getIdInDataSource() != null) {
                        String identifier = Long.toString((1L << 63) | (Long.valueOf(evidence.getIdInDataSource()) << 50) | (objVFH.getUUID()));
                        item.setIdInDataSource(identifier);
                        item.setInputStreamFactory(evidence.getInputStreamFactory());
                    }

                    // optimization to not create more temp files
                    /*
                    if (evidence.hasTmpFile()) {
                        try {
                            item.setParentTmpFile(evidence.getTempFile(), (Item) evidence);
                            item.setParentOffset(objVFH.getDataOffset());
                        } catch (IOException e) {
                            // ignore
                            ;
                        }
                    }
                    */
                    evidence.setHasChildren(true);

                    item.setExtraAttribute(IndexItem.PARENT_TRACK_ID, Util.getTrackID(evidence));
                    totalProcessed.incrementAndGet();

                    worker.processNewItem(item);
                    item = null;
                }
                
                
            }

			WFSExtractor.clear();			
			WFSExtractor = null;
						
            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);			
			
			
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }


    protected void DHFSProcess(IItem evidence) throws Exception {

        try {
			
			SeekableInputStream is = evidence.getSeekableInputStream();
						
			long t = System.currentTimeMillis();

					
			DHFSExtractor DHFSExtractor = new DHFSExtractor();			
			
			DHFSExtractor.init(is);	

			DHFSExtractor.clear();			
			DHFSExtractor = null;
						
            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);			
			
			
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }


    }

    private static FSType getFsType(MediaType mediaType) {

        FSType ret = FSType.NONE;

        if (mediaType.getBaseType().equals(HVFS_MEDIA_TYPE))
            ret = FSType.HVFS;
		if (mediaType.getBaseType().equals(WFS_MEDIA_TYPE))
            ret = FSType.WFS;
		if (mediaType.getBaseType().equals(DHFS_MEDIA_TYPE))
           ret = FSType.DHFS;

        return ret;
    }

    private static boolean isAcceptedType(FSType fstype) {

        if (fstype!=FSType.NONE){
            return true;
        }

        return false;

    }

}
