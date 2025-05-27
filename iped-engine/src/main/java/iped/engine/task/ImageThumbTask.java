package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ImageThumbTaskConfig;
import iped.engine.util.Util;
import iped.properties.MediaTypes;
import iped.utils.ExternalImageConverter;
import iped.utils.ImageUtil;
import iped.utils.ImageUtil.BooleanWrapper;
import iped.viewers.util.ImageMetadataUtil;

public class ImageThumbTask extends ThumbTask {

    public static final String THUMB_TIMEOUT = "thumbTimeout"; //$NON-NLS-1$

    private static final int TIMEOUT_DELTA = 5;

    private static final int samplingRatio = 3;

    private static final int numStats = 22;

    private static final Logger logger = LoggerFactory.getLogger(ImageThumbTask.class);

    private static final Map<String, long[]> performanceStatsPerType = new HashMap<String, long[]>();
    private static final AtomicBoolean logInit = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private ImageThumbTaskConfig imgThumbConfig;

    private ExternalImageConverter externalImageConverter;
    private static final AtomicBoolean extConvPropInit = new AtomicBoolean(false);

    private int compression;
    private int thumbSize;
    private int maxViewImageSize;
    private Set<String> mimesToCreateView;

    public ImageThumbTaskConfig getImageThumbConfig() {
        return imgThumbConfig;
    }

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new ImageThumbTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        imgThumbConfig = configurationManager.findObject(ImageThumbTaskConfig.class);

        synchronized (extConvPropInit) {
            if (!extConvPropInit.get()) {
                System.setProperty(ExternalImageConverter.enabledProp, String.valueOf(imgThumbConfig.isEnableExternalConv()));
                System.setProperty(ExternalImageConverter.useGMProp, String.valueOf(imgThumbConfig.isUseGraphicsMagick()));
                System.setProperty(ExternalImageConverter.lowDensityProp, String.valueOf(imgThumbConfig.getLowResDensity()));
                System.setProperty(ExternalImageConverter.highDensityProp, String.valueOf(imgThumbConfig.getHighResDensity()));
                System.setProperty(ExternalImageConverter.magickAreaLimitProp, String.valueOf(imgThumbConfig.getMaxMPixelsInMemory()));
                System.setProperty(ExternalImageConverter.minTimeoutProp, String.valueOf(imgThumbConfig.getMinTimeout()));
                System.setProperty(ExternalImageConverter.timeoutPerMBProp, String.valueOf(imgThumbConfig.getTimeoutPerMB()));
                
                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                    System.setProperty(ExternalImageConverter.winToolPathPrefixProp,
                            Configuration.getInstance().appRoot);
                }

                File tmpDir = new File(System.getProperty("java.io.tmpdir"), "ext-conv"); //$NON-NLS-1$ //$NON-NLS-2$
                tmpDir.mkdirs();
                System.setProperty(ExternalImageConverter.tmpDirProp, tmpDir.getAbsolutePath());
                startTmpDirCleaner(tmpDir);

                extConvPropInit.set(true);
            }
        }

        externalImageConverter = new ExternalImageConverter(executor);
        thumbSize = imgThumbConfig.getThumbSize();
        compression = imgThumbConfig.getCompression();
        maxViewImageSize = imgThumbConfig.getMaxViewImageSize();
        mimesToCreateView = imgThumbConfig.getMimesToCreateView();

        synchronized (logInit) {
            if (isEnabled() && !logInit.get()) {
                logInit.set(true);
                logger.info("Thumb Size: " + thumbSize); //$NON-NLS-1$
                logger.info("Extract Thumb: " + imgThumbConfig.isExtractThumb()); //$NON-NLS-1$
            }
        }

        // Use memory instead of files to cache image streams
        // tests have shown up to 3x thumb creation speed up.
        ImageIO.setUseCache(false);

        // Install a new EXIF reader to read thumb data.
        // must be installed at the beginning of the processing, see #532.
        ImageMetadataUtil.updateExifReaderToLoadThumbData();

        // Set ImageIO plugins priority order.
        ImageUtil.updateImageIOPluginsPriority();
    }

    private static void startTmpDirCleaner(File tmpDir) {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    long t = System.currentTimeMillis() - 60000;
                    File[] subFiles = tmpDir.listFiles();
                    if (subFiles != null) {
                        for (File tmp : subFiles) {
                            if (tmp.lastModified() < t)
                                tmp.delete();
                        }
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean isEnabled() {
        return imgThumbConfig.isEnabled();
    }

    @Override
    public void finish() throws Exception {
        if (!executor.isShutdown())
            executor.shutdownNow();

        externalImageConverter.close();
        synchronized (finished) {
            if (isEnabled() && !finished.get()) {
                finished.set(true);
                if (!performanceStatsPerType.isEmpty()) {
                    List<String> types = new ArrayList<String>(performanceStatsPerType.keySet());
                    Collections.sort(types);
                    long[] totals = new long[numStats];
                    for (String type : types) {
                        long[] s = performanceStatsPerType.get(type);
                        for (int i = 0; i < s.length; i++) {
                            totals[i] += s[i];
                        }
                    }
                    types.add("TOTAL");
                    performanceStatsPerType.put("TOTAL", totals);
                    int maxType = 9;
                    long[] maxCol = new long[numStats];
                    for (String type : types) {
                        maxType = Math.max(maxType, type.length());
                        long[] s = performanceStatsPerType.get(type);
                        for (int i = 0; i < s.length; i++) {
                            maxCol[i] = Math.max(maxCol[i], s[i]);
                            if (i % 2 == 0 && s[i] > 0)
                                s[i + 1] = (int) Math.round(s[i + 1] / (double) s[i]);
                        }
                    }
                    int[] w = new int[numStats];
                    for (int i = 0; i < numStats; i++) {
                        w[i] = Math.max(i % 2 == 0 ? 3 : 4, String.valueOf(maxCol[i]).length());
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%" + maxType + "s", ""));
                    sb.append(String.format(" %" + (w[0] + w[1] + w[2] + w[3] + 3) + "s", "ReuseThumb"));
                    sb.append(String.format(" %" + (w[4] + w[5] + w[6] + w[7] + 3) + "s", "InternalRead"));
                    sb.append(String.format(" %" + (w[8] + w[9] + w[10] + w[11] + 3) + "s", "ExternalRead"));
                    sb.append(String.format(" %" + (w[12] + w[13] + 1) + "s", "Resize"));
                    sb.append(String.format(" %" + (w[14] + w[15] + 1) + "s", "Opaque"));
                    sb.append(String.format(" %" + (w[16] + w[17] + 1) + "s", "Rotate"));
                    sb.append(String.format(" %" + (w[18] + w[19] + 1) + "s", "Write"));
                    sb.append(String.format(" %" + (w[20] + w[21] + 1) + "s", "Store"));
                    sb.append("\n");
                    sb.append(String.format("%" + maxType + "s", ""));
                    sb.append(String.format(" %" + (w[0] + w[1] + 1) + "s", "Success"));
                    sb.append(String.format(" %" + (w[2] + w[3] + 1) + "s", "Fail"));
                    sb.append(String.format(" %" + (w[4] + w[5] + 1) + "s", "Success"));
                    sb.append(String.format(" %" + (w[6] + w[7] + 1) + "s", "Fail"));
                    sb.append(String.format(" %" + (w[8] + w[9] + 1) + "s", "Success"));
                    sb.append(String.format(" %" + (w[10] + w[11] + 1) + "s", "Fail"));
                    sb.append("\n");
                    sb.append(String.format("%" + maxType + "s", "ImageType"));
                    for (int i = 0; i < numStats; i++) {
                        sb.append(String.format(" %" + w[i] + "s", i % 2 == 0 ? "Cnt" : "Time"));
                    }
                    sb.append("\n");
                    for (String type : types) {
                        sb.append(String.format("%" + maxType + "s", type));
                        long[] s = performanceStatsPerType.get(type);
                        for (int i = 0; i < s.length; i++) {
                            sb.append(String.format(" %" + w[i] + "s", s[i]));
                        }
                        sb.append("\n");
                    }
                    logger.info("ImageThumbTask detailed statistics:\n\n" + sb); //$NON-NLS-1$
                }
            }
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled() || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHashValue() == null || evidence.getThumb() != null) {
            return;
        }

        File thumbFile = getThumbFile(evidence);
        if (hasThumb(evidence, thumbFile)) {
            File viewFile = getViewFile(evidence, "jpg");
            if (viewFile.exists()) {
                evidence.setViewFile(viewFile);
            }
            return;
        }

        Future<?> future = executor.submit(new ThumbCreator(evidence, thumbFile));
        try {
            int timeout = TIMEOUT_DELTA + externalImageConverter.getTotalTimeout(evidence.getLength());
            future.get(timeout, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            future.cancel(true);
            stats.incTimeouts();
            evidence.setExtraAttribute(THUMB_TIMEOUT, "true"); //$NON-NLS-1$
            logger.warn("Timeout creating thumb: " + evidence); //$NON-NLS-1$
        }

    }

    /**
     * Verifica se é imagem.
     */
    public static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image") || //$NON-NLS-1$
                mediaType.toString().equals("application/coreldraw") || //$NON-NLS-1$
                mediaType.toString().equals("application/x-vnd.corel.zcf.draw.document+zip"); //$NON-NLS-1$
    }

    private class ThumbCreator implements Runnable {

        IItem evidence;
        File thumbFile;

        public ThumbCreator(IItem evidence, File thumbFile) {
            this.evidence = evidence;
            this.thumbFile = thumbFile;
        }

        @Override
        public void run() {
            createImageThumb(evidence, thumbFile);
        }

    }

    public static boolean isJpeg(IItem item) {
        return item.getMediaType().getSubtype().startsWith("jpeg");
    }

    private void createImageThumb(IItem evidence, File thumbFile) {
        try {
            if (evidence.getLength() != null && evidence.getLength().longValue() == 0) {
                // If evidence length is zero, don't even try to create a thumb 
                saveThumb(evidence, thumbFile);
                return;
            }
            long[] performanceStats = new long[numStats];
            BufferedImage img = null;
            if (imgThumbConfig.isExtractThumb() && isJpeg(evidence)) { // $NON-NLS-1$
                long t = System.currentTimeMillis();
                try (BufferedInputStream stream = evidence.getBufferedInputStream()) {
                    img = ImageMetadataUtil.getThumb(stream);
                }
                performanceStats[img == null ? 2 : 0]++;
                performanceStats[img == null ? 3 : 1] += System.currentTimeMillis() - t;
            }
            if (img == null) {
                long t = System.currentTimeMillis();
                BooleanWrapper renderException = new BooleanWrapper();
                img = ImageUtil.getSubSampledImage(evidence, thumbSize * samplingRatio, renderException,
                        MediaTypes.getMimeTypeString(evidence));
                if (img != null && renderException.value) {
                    evidence.setExtraAttribute("thumbException", "true");
                }
                performanceStats[img == null ? 6 : 4]++;
                performanceStats[img == null ? 7 : 5] += System.currentTimeMillis() - t;
            }
            if (img == null) {
                // External Conversion
                long t = System.currentTimeMillis();

                // Create a high resolution view
                String mime = MediaTypes.getMimeTypeString(evidence);
                if (mime != null && mimesToCreateView.contains(mime)) {
                    try (BufferedInputStream stream = evidence.getBufferedInputStream()) {
                        img = externalImageConverter.getImage(stream, maxViewImageSize, true, evidence.getLength(),
                                true);
                    } catch (TimeoutException e) {
                        stats.incTimeouts();
                        evidence.setExtraAttribute(THUMB_TIMEOUT, "true");
                        logger.warn("Timeout creating view: " + evidence);
                    }
                    if (img != null) {
                        // Store view
                        File viewTmpFile = getViewFile(evidence, "tmp");
                        viewTmpFile.getParentFile().mkdirs();
                        ImageIO.write(img, "jpg", viewTmpFile);
                        File viewFile = getViewFile(evidence, "jpg");
                        viewTmpFile.renameTo(viewFile);
                        evidence.setViewFile(viewFile);
                    }
                }

                if (img == null) {
                    // No view was created through external conversion
                    try (BufferedInputStream stream = evidence.getBufferedInputStream()) {
                        img = externalImageConverter.getImage(stream, thumbSize, false, evidence.getLength(), true);
                    } catch (TimeoutException e) {
                        stats.incTimeouts();
                        evidence.setExtraAttribute(THUMB_TIMEOUT, "true");
                        logger.warn("Timeout creating thumb: " + evidence);
                    }
                }

                if (img != null) {
                    evidence.setExtraAttribute("externalThumb", "true");
                }
                performanceStats[img == null ? 10 : 8]++;
                performanceStats[img == null ? 11 : 9] += System.currentTimeMillis() - t;
            }

            if (img != null) {
                if (img.getWidth() > thumbSize || img.getHeight() > thumbSize) {
                    long t = System.currentTimeMillis();
                    img = ImageUtil.resizeImage(img, thumbSize, thumbSize);
                    performanceStats[12]++;
                    performanceStats[13] += System.currentTimeMillis() - t;
                }
                long t = System.currentTimeMillis();
                img = ImageUtil.getOpaqueImage(img);
                performanceStats[14]++;
                performanceStats[15] += System.currentTimeMillis() - t;

                // Ajusta rotacao da miniatura a partir do metadado orientacao
                try (BufferedInputStream stream = evidence.getBufferedInputStream()) {
                    int orientation = ImageMetadataUtil.getOrientation(stream);
                    if (orientation > 0) {
                        t = System.currentTimeMillis();
                        img = ImageUtil.applyOrientation(img, orientation);
                        performanceStats[16]++;
                        performanceStats[17] += System.currentTimeMillis() - t;
                    }
                }

                t = System.currentTimeMillis();
                performanceStats[18]++;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageUtil.writeCompressedJPG(img, baos, compression);
                evidence.setThumb(baos.toByteArray());
                performanceStats[19] += System.currentTimeMillis() - t;
            }

            long t = System.currentTimeMillis();
            saveThumb(evidence, thumbFile);
            performanceStats[20]++;
            performanceStats[21] += System.currentTimeMillis() - t;

            String type = evidence.getMediaType().toString();
            synchronized (performanceStatsPerType) {
                long[] s = performanceStatsPerType.get(type);
                if (s == null) {
                    performanceStatsPerType.put(type, performanceStats);
                } else {
                    for (int i = 0; i < s.length; i++) {
                        s[i] += performanceStats[i];
                    }
                }
            }

        } catch (Throwable e) {
            logger.warn(evidence.toString(), e);

        } finally {
            updateHasThumb(evidence);
        }
    }

    private File getViewFile(IItem evidence, String ext) {
        return Util.getFileFromHash(new File(output, MakePreviewTask.viewFolder), evidence.getHash(), ext);
    }
}
