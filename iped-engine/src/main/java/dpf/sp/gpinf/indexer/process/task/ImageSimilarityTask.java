package dpf.sp.gpinf.indexer.process.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gpinf.similarity.ImageSimilarity;
import iped3.IItem;

/**
 * Image Similarity task.
 *
 * @author Wladimir Leite
 */
public class ImageSimilarityTask extends AbstractTask {

    private static final String taskName = "Image Similarity"; //$NON-NLS-1$
    private static final String enableParam = "enableImageSimilarity"; //$NON-NLS-1$
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

    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                String enabled = confParams.getProperty(enableParam);
                if (enabled != null) taskEnabled = Boolean.valueOf(enabled.trim());

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
                logger.info("Total images processed: " + totalProcessed); //$NON-NLS-1$
                logger.info("Total images not processed: " + totalFailed); //$NON-NLS-1$
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total != 0) {
                    logger.info("Average processing time (milliseconds/image): " + (totalTime.longValue() / total)); //$NON-NLS-1$
                }
            }
        }
    }

    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null) {
            return;
        }

        try {
            byte[] thumb = evidence.getThumb();
            if (thumb == null) {
                return;
            }
            long t = System.currentTimeMillis();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(thumb));
            byte[][] features = imageSimilarity.extractFeatures(img);
            if (features != null) {
                evidence.setSimilarity(features[0], true);
                evidence.setSimilarity(features[1], false);
                totalProcessed.incrementAndGet();
            } else {
                totalFailed.incrementAndGet();
            }
            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);
        } catch (Exception e) {
            logger.warn(e.toString());
            logger.debug(taskName, e);
        }
    }

    private static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image"); //$NON-NLS-1$
    }
}
