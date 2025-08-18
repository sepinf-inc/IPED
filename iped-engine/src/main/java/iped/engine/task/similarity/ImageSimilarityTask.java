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
import iped.engine.task.HashTask;
import iped.engine.task.ImageThumbTask;
import jep.NDArray;

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

                checkDependency(HashTask.class);
                checkDependency(ImageThumbTask.class);
                
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

    private static int maximum = 0;
    private static int minimum = 0;
    private static Object lock = new Object();

    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null) {
            return;
        }

        Object prev = evidence.getExtraAttribute(IMAGE_FEATURES);
        if (prev != null && prev instanceof byte[]) {
            return;
        }

        Object o = evidence.getExtraAttribute("imageToVector");
        if (o != null && o instanceof NDArray) {
            NDArray imgToVec = (NDArray) o;
            System.out.print("New vector dimension: ");
            for (int d : imgToVec.getDimensions()) {
                System.out.print(d + ", ");
            }
            System.out.println();
            
            byte[] bytes = new byte[((float[]) imgToVec.getData()).length];
            System.out.print("img2vec features: ");
            int i = 0;
            int min = 0, max = 0;
            for (float f : (float[]) imgToVec.getData()) {
                bytes[i++] = (byte) Math.round(Math.max(-128, Math.min(f * 15 - 64, 127)));
                System.out.print(bytes[i - 1] + " ");
                
                // Feature range tracking
                if (bytes[i - 1] < min)
                    min = bytes[i - 1];
                if (bytes[i - 1] > max)
                    max = bytes[i - 1];
            }
            System.out.println();
            synchronized (lock) {
                if (min < minimum) {
                    minimum = min;
                }
                if (max > maximum) {
                    maximum = max;
                }
            }
            logger.error("Features Value Range: Min = " + minimum + " Max = " + maximum);

            imageSimilarity.numFeatures = bytes.length;
            evidence.setExtraAttribute(IMAGE_FEATURES, bytes);
            evidence.setExtraAttribute("imageToVector", 1);
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
                // evidence.setExtraAttribute(IMAGE_FEATURES, features);
                System.out.print("original features: ");
                for (byte b : features) {
                    System.out.print(b + " ");
                }
                System.out.println();
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
