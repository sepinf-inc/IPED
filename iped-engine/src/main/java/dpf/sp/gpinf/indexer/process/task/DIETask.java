package dpf.sp.gpinf.indexer.process.task;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.ImageThumbTaskConfig;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.die.AbstractDie;
import gpinf.die.RandomForestPredictor;
import iped3.IItem;

/**
 * Explicit Image Detection (DIE) Task .
 *
 * @author Wladimir Leite
 */
public class DIETask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(DIETask.class);

    /**
     * Object responsible for predicting if an image is explicit (i.e. contains nudity). 
     * It uses a binary classifier that returns a double value from 0 (normal image) to 1 (explicit).
     */
    private static RandomForestPredictor predictor;

    /**
     * Object responsible for extracting features from images.
     */
    private static AbstractDie die;

    /**
     * Field name used to store the detection result (a score from 1 to 1000, inclusive).
     */
    public static String DIE_SCORE = "scoreNudez"; //$NON-NLS-1$

    /**
     * Field name used to store a detection "class" (a value from 1 to 5, derived from the score).
     */
    public static String DIE_CLASS = "classeNudez"; //$NON-NLS-1$

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Static object to control (synchronize) initialization process (it should run only once for all threads).
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Static object to control (synchronize) the termination of the task.
     */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * Map to store videos scores, to avoid processing duplicated videos. 
     */
    private static final HashMap<String, Short> videoResults = new HashMap<String, Short>();
    
    // Static counters for the number of images/videos successfully processed/failed, and the total processing time.
    private static final AtomicLong totalImagesProcessed = new AtomicLong();
    private static final AtomicLong totalVideosProcessed = new AtomicLong();
    private static final AtomicLong totalImagesFailed = new AtomicLong();
    private static final AtomicLong totalVideosFailed = new AtomicLong();
    private static final AtomicLong totalImagesTime = new AtomicLong();
    private static final AtomicLong totalVideosTime = new AtomicLong();

    private static final String ENABLE_PARAM = "enableLedDie"; //$NON-NLS-1$

    private static GraphicsMagicConverter graphicsMagicConverter = new GraphicsMagicConverter();

    private boolean extractThumb;

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    /**
     * Initialize the task.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                String enableParam = confParams.getProperty(ENABLE_PARAM);
                if (enableParam != null)
                    taskEnabled = Boolean.valueOf(enableParam.trim());

                String diePath = confParams.getProperty("ledDie"); //$NON-NLS-1$
                if (taskEnabled && diePath == null)
                    throw new IPEDException("Configure DIE path on " + Configuration.LOCAL_CONFIG); //$NON-NLS-1$

                // backwards compatibility
                if (enableParam == null && diePath != null)
                    taskEnabled = true;

                if (!taskEnabled) {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                File dieDat = new File(diePath.trim());
                if (!dieDat.exists())
                    dieDat = new File(new File(Configuration.getInstance().appRoot), diePath.trim());
                if (!dieDat.exists() || !dieDat.canRead()) {
                    String msg = "Invalid DIE database file: " + dieDat.getAbsolutePath(); //$NON-NLS-1$
                    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
                    for (File source : args.getDatasources()) {
                        if (source.getName().endsWith(".iped")) {
                            logger.warn(msg);
                            taskEnabled = false;
                            return;
                        }
                    }
                    throw new IPEDException(msg);
                }

                // Instantiate detection object
                predictor = RandomForestPredictor.load(dieDat, -1);
                if (predictor == null)
                    throw new IPEDException("Error loading DIE database file: " + dieDat.getAbsolutePath()); //$NON-NLS-1$

                // Instantiate feature extraction object
                die = AbstractDie.loadImplementation(dieDat);
                if (die == null)
                    throw new IPEDException("Error loading DIE implementation: " + dieDat.getAbsolutePath()); //$NON-NLS-1$

                logger.info("Task enabled."); //$NON-NLS-1$
                logger.info("Model version: " + predictor.getVersion()); //$NON-NLS-1$
                logger.info("Trees loaded: " + predictor.size()); //$NON-NLS-1$
                init.set(true);
            }
        }

        ImageThumbTaskConfig imgThumbConfig = (ImageThumbTaskConfig) ConfigurationManager.getInstance()
                .findObjects(ImageThumbTaskConfig.class).iterator().next();
        extractThumb = imgThumbConfig.isExtractThumb();
    }

    /**
     * Finalize the task, logging some statistics.
     */
    public void finish() throws Exception {
        synchronized (finished) {
            graphicsMagicConverter.close();
            if (taskEnabled && !finished.get()) {
                die = null;
                predictor = null;
                long totalImages = totalImagesProcessed.longValue() + totalImagesFailed.longValue();
                if (totalImages != 0) {
                    logger.info("Total images processed: " + totalImagesProcessed); //$NON-NLS-1$
                    logger.info("Total images not processed: " + totalImagesFailed); //$NON-NLS-1$
                    logger.info("Average image processing time (ms/image): " + (totalImagesTime.longValue() / totalImages)); //$NON-NLS-1$
                }
                long totalVideos = totalVideosProcessed.longValue() + totalVideosFailed.longValue();
                if (totalVideos != 0) {
                    logger.info("Total videos processed: " + totalVideosProcessed); //$NON-NLS-1$
                    logger.info("Total videos not processed: " + totalVideosFailed); //$NON-NLS-1$
                    logger.info("Average video processing time (ms/video): " + (totalVideosTime.longValue() / totalVideos)); //$NON-NLS-1$
                }
                finished.set(true);
            }
        }
    }

    /**
     * Main task processing method. Check if the evidence should be processed (image or video) and then calls detection method itself (DIE). 
     */
    @Override
    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled  || !evidence.isToAddToCase() || evidence.getHash() == null 
                || !(isImageType(evidence.getMediaType()) || isVideoType(evidence.getMediaType()))) {
            return;
        }

        try {
            long t = System.currentTimeMillis();
            if (isImageType(evidence.getMediaType())) {
                if (evidence.getExtraAttribute(ImageThumbTask.THUMB_TIMEOUT) != null) return;

                //For images call the detection method passing the thumb image
                BufferedImage img = null;
                byte[] thumb = evidence.getThumb();
                if (thumb != null) {
                    if (thumb.length == 0) return;
                    img = ImageIO.read(new ByteArrayInputStream(evidence.getThumb()));
                } else {
                    img = getBufferedImage(evidence);
                }
                List<Float> features = die.extractFeatures(img);
                if (features != null) {
                    double p = predictor.predict(features);
                    update(evidence, predictionToScore(p));
                    totalImagesProcessed.incrementAndGet();
                } else {
                    totalImagesFailed.incrementAndGet();
                }
                t = System.currentTimeMillis() - t;
                totalImagesTime.addAndGet(t);

            } else if (isVideoType(evidence.getMediaType())) {
                Short prevResult = null;
                synchronized (videoResults) {
                    prevResult = videoResults.get(evidence.getHash());
                }
                if (prevResult != null) {
                    update(evidence, prevResult.intValue());
                    return;
                }
                //For videos call the detection method for each extracted frame image (VideoThumbsTask must be enabled)
                File viewFile = evidence.getViewFile();
                if (viewFile != null && viewFile.exists()) {
                    List<BufferedImage> frames = ImageUtil.getFrames(viewFile);
                    List<Double> pvideo = new ArrayList<Double>();
                    if (frames != null) {
                        for (BufferedImage frame : frames) {
                            List<Float> features = die.extractFeatures(frame);
                            if (features != null) {
                                double p = predictor.predict(features);
                                pvideo.add(p);
                            }
                        }
                    }
                    if (!pvideo.isEmpty()) {
                        double p = videoScore(pvideo);
                        int score = predictionToScore(p);
                        update(evidence, score);
                        totalVideosProcessed.incrementAndGet();
                        synchronized (videoResults) {
                            videoResults.put(evidence.getHash(), (short) score);
                        }
                    } else {
                        totalVideosFailed.incrementAndGet();
                    }
                    t = System.currentTimeMillis() - t;
                    totalVideosTime.addAndGet(t);
                }
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        }
    }

    /**
     * Combine the score of each video frame into a single score. 
     * It uses a weighted average, with higher weights for higher scores.
     */
    private double videoScore(List<Double> p) {
        Collections.sort(p);
        Collections.reverse(p);
        double weight = 1;
        double mult = 0.7;
        double div = 0;
        double sum = 0;
        for (double v : p) {
            div += weight;
            sum += v * weight;
            weight *= mult;
        }
        if (div > 0) sum /= div;
        return sum;
    }

    /**
     * Convert a raw prediction (double in [0,1]) into a score (integer in [1,1000]).
     */
    private static int predictionToScore(double p) {
        return Math.max(1, (int) Math.round(p * 1000));
    }

    /**
     * Update DIE attributes of a evidence.
     */
    private void update(IItem evidence, int score) throws Exception {
        evidence.setExtraAttribute(DIE_SCORE, score);
        int classe = Math.min(5, Math.max(1, score / 200 + 1));
        evidence.setExtraAttribute(DIE_CLASS, classe);
    }
    
    /**
     * Check if the evidence is an image.
     */
    public static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image"); //$NON-NLS-1$
    }
    
    /**
     * Check if the evidence is a video.
     */
    public static boolean isVideoType(MediaType mediaType) {
        return MetadataUtil.isVideoType(mediaType);
    }
    

    /**
     * Get an image from the evidence, possibly reusing its thumb.
     */
    private BufferedImage getBufferedImage(IItem evidence) {
        BufferedImage img = null;
        try {
            if (extractThumb && ImageThumbTask.isJpeg(evidence)) { // $NON-NLS-1$
                BufferedInputStream stream = evidence.getBufferedStream();
                try {
                    img = ImageUtil.getThumb(stream);
                } finally {
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
                BufferedInputStream stream = evidence.getBufferedStream();
                try {
                    img = ImageUtil.getSubSampledImage(stream, die.getExpectedImageSize(), die.getExpectedImageSize());
                } finally {
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
                BufferedInputStream stream = evidence.getBufferedStream();
                try {
                    img = graphicsMagicConverter.getImage(stream, die.getExpectedImageSize(), evidence.getLength());
                } finally {
                    IOUtil.closeQuietly(stream);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return img;
    }
}
