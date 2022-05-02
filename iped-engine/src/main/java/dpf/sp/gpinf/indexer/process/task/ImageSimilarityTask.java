package dpf.sp.gpinf.indexer.process.task;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gpinf.similarity.ImageSimilarity;
import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.config.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.EnableTaskProperty;
import dpf.sp.gpinf.indexer.config.ImageThumbTaskConfig;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.util.ExternalImageConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageMetadataUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import iped3.IItem;
import iped3.configuration.Configurable;
import iped3.exception.IPEDException;


/**
 * Image Similarity task.
 *
 * @author Wladimir Leite
 */
public class ImageSimilarityTask extends AbstractTask {

    public static final String enableParam = "enableImageSimilarity"; //$NON-NLS-1$

    public static final String SIMILARITY_FEATURES = "similarityFeatures"; //$NON-NLS-1$

    private static boolean taskEnabled = false;
    private static final AtomicBoolean init = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static final AtomicLong totalProcessed = new AtomicLong();
    private static final AtomicLong totalFailed = new AtomicLong();
    private static final AtomicLong totalTime = new AtomicLong();

    private ImageSimilarity imageSimilarity;

    private static final Logger logger = LoggerFactory.getLogger(ImageSimilarityTask.class);

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
        if (taskEnabled) {
            imageSimilarity = new ImageSimilarity();
        }
    }

    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                logger.info("Total images and videos processed: " + totalProcessed); //$NON-NLS-1$
                logger.info("Total images and videos not processed: " + totalFailed); //$NON-NLS-1$
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total != 0) {
                    logger.info("Average processing time (milliseconds/image): " + (totalTime.longValue() / total)); //$NON-NLS-1$
                }
            }
        }
    }

    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || !evidence.isToAddToCase() || evidence.getHash() == null) {
            return;
        }

        try {
            boolean isAnimationImage = isAnimationImage(evidence);
            byte[] features;
            List<byte[]> featuresList = new ArrayList<byte[]>();
            long t = System.currentTimeMillis();

            if (isImageType(evidence.getMediaType()) && !isAnimationImage){
                byte[] thumb = evidence.getThumb();
                if (thumb == null) {
                    return;
                }
                
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(thumb));
                features = imageSimilarity.extractFeatures(img);
                if (features != null) {
                    featuresList.add(features);
                    evidence.setExtraAttribute(SIMILARITY_FEATURES, featuresList);
                    totalProcessed.incrementAndGet();
                } else {
                    totalFailed.incrementAndGet();
                }
                
            } else if (isVideoType(evidence.getMediaType()) ||  isAnimationImage){ // Since only passes image and video, no aditional checks of type are made
                //For videos call the detection method for each extracted frame image (VideoThumbsTask must be enabled)
                File viewFile = evidence.getViewFile();
                if (viewFile != null && viewFile.exists()) {
                    List<BufferedImage> frames = ImageUtil.getFrames(viewFile);            
                    if (frames != null) {                                               
                        for (BufferedImage frame : frames) {
                            features = imageSimilarity.extractFeatures(frame);                        
                            if (features != null){
                                featuresList.add(features);                                
                            } 
                        }
                        if (featuresList != null) {
                            evidence.setExtraAttribute(SIMILARITY_FEATURES, featuresList);
                            totalProcessed.incrementAndGet();                            
                        } else {
                            totalFailed.incrementAndGet();
                        }
                    }    
                }                
            } else {
                return;
            }

            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);

        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }

    private static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image"); //$NON-NLS-1$
    }

    private static boolean isAnimationImage(IItem item) {
        return VideoThumbTask.isImageSequence(item.getMediaType().toString()) ||
                item.getMetadata().get(VideoThumbTask.ANIMATION_FRAMES_PROP) != null;
    }
     /**
     * Check if the evidence is a video.
     */
    public static boolean isVideoType(MediaType mediaType) {
        return MetadataUtil.isVideoType(mediaType);
    }
    
}
