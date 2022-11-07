package iped.engine.task.similarity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.task.AbstractTask;

/**
 * Image Similarity task.
 *
 * @author Wladimir Leite
 */
public class ImageSimilarityTask extends AbstractTask {

    public static final String enableParam = "enableImageSimilarity"; //$NON-NLS-1$

    public static final String IMAGE_FEATURES = "imageFeatures"; //$NON-NLS-1$

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
        EnableTaskProperty result = ConfigurationManager.get().findObject(EnableTaskProperty.class);
        if(result == null) {
            result = new EnableTaskProperty(enableParam);
        }
        return Arrays.asList(result);
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
        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null) {
            return;
        }

        try {
            byte[] thumb = evidence.getThumb();
            if (thumb == null) {
                return;
            }
            long t = System.currentTimeMillis();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(thumb));
            byte[] features = imageSimilarity.extractFeatures(img);
            if (features != null) {
                evidence.setExtraAttribute(IMAGE_FEATURES, features);
                totalProcessed.incrementAndGet();
            } else {
                totalFailed.incrementAndGet();
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
}
