package dpf.sp.gpinf.indexer.process.task;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil.BooleanWrapper;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;

public class ImageThumbTask extends AbstractTask {

    public static final String thumbsFolder = "thumbs"; //$NON-NLS-1$

    private static final String enableProperty = "enableImageThumbs"; //$NON-NLS-1$

    public static final String HAS_THUMB = "hasThumb"; //$NON-NLS-1$

    public static final String THUMB_TIMEOUT = "thumbTimeout"; //$NON-NLS-1$

    private static final String TASK_CONFIG_FILE = "ImageThumbsConfig.txt"; //$NON-NLS-1$
    
    private static final String SELECT_THUMB = "SELECT thumb FROM t1 WHERE id=?;";
    
    private static final String INSERT_THUMB = "INSERT INTO t1(id, thumb) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET thumb=? WHERE thumb IS NULL;";

    private static final int samplingRatio = 3;

    public static boolean extractThumb = true;

    private static ExecutorService executor = Executors.newCachedThreadPool();

    public int thumbSize = 160;

    public int galleryThreads = 1;

    public boolean logGalleryRendering = false;

    private boolean taskEnabled = false;
    
    private boolean storeThumbsInDb;

    private GraphicsMagicConverter graphicsMagicConverter;
    
    private static final Map<String,long[]> performanceStatsPerType = new HashMap<String,long[]>(); 
    private static final AtomicBoolean finished = new AtomicBoolean(false);
    private static final Logger logger = LoggerFactory.getLogger(ImageThumbTask.class);
    private static final int numStats = 22;
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        taskEnabled = Boolean.valueOf(confParams.getProperty(enableProperty));

        UTF8Properties properties = new UTF8Properties();
        File confFile = new File(confDir, TASK_CONFIG_FILE);
        properties.load(confFile);

        String value = properties.getProperty("externalConversionTool"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            if (!value.trim().equals("graphicsmagick")) { //$NON-NLS-1$
                GraphicsMagicConverter.USE_GM = false;
            }
        } else {
            GraphicsMagicConverter.enabled = false;
        }

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$
            GraphicsMagicConverter.setWinToolPathPrefix(Configuration.getInstance().appRoot);
        }

        value = properties.getProperty("imgConvTimeout"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            GraphicsMagicConverter.TIMEOUT = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("galleryThreads"); //$NON-NLS-1$
        if (value != null && !value.trim().equalsIgnoreCase("default")) { //$NON-NLS-1$
            galleryThreads = Integer.valueOf(value.trim());
        } else {
            galleryThreads = Runtime.getRuntime().availableProcessors();
        }

        value = properties.getProperty("imgThumbSize"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            thumbSize = Integer.valueOf(value.trim());
        }

        value = properties.getProperty("extractThumb"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            extractThumb = Boolean.valueOf(value.trim());
        }

        value = properties.getProperty("logGalleryRendering"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            logGalleryRendering = Boolean.valueOf(value.trim());
        }
        graphicsMagicConverter = new GraphicsMagicConverter(executor);
        
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public void finish() throws Exception {
        if (!executor.isShutdown())
            executor.shutdownNow();

        graphicsMagicConverter.close();
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                if (!performanceStatsPerType.isEmpty()) {
                    List<String> types = new ArrayList<String>(performanceStatsPerType.keySet());
                    Collections.sort(types);
                    int maxType = 9;
                    long[] maxCol = new long[numStats];
                    for (String type : types) {
                        maxType = Math.max(maxType, type.length());
                        long[] s = performanceStatsPerType.get(type);
                        for (int i = 0; i < s.length; i++) {
                            maxCol[i] = Math.max(maxCol[i], s[i]);
                            if (i % 2 == 0 && s[i] > 0) s[i + 1] = (int) Math.round(s[i + 1] / (double) s[i]);
                        }
                    }
                    int[] w = new int[numStats];
                    for (int i = 0; i < numStats; i++) {
                        w[i] = Math.max(i % 2 == 0 ? 3 : 4, String.valueOf(maxCol[i]).length());
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%" + maxType + "s", ""));
                    sb.append(String.format(" %" + (w[0] + w[1] + w[2] + w[3] + 3) + "s", "ExtractThumb"));
                    sb.append(String.format(" %" + (w[4] + w[5] + w[6] + w[7] + 3) + "s", "ReadSubsample"));
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

        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null || evidence.getThumb() != null) {
            return;
        }
        
        File thumbFile = null;
        
        IPEDConfig ipedConfig = (IPEDConfig)ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator().next();
        storeThumbsInDb = !caseData.containsReport() || !ipedConfig.isHtmlReportEnabled();
        
        if(storeThumbsInDb) {
            Connection con = ExportFileTask.getSQLiteStorageCon(output, evidence.getHashValue().getBytes());
            try(PreparedStatement ps = con.prepareStatement(SELECT_THUMB)){
                ps.setString(1, evidence.getHash());
                ResultSet rs = ps.executeQuery();
                if(rs.next()) {
                    byte[] thumb = rs.getBytes(1);
                    if(thumb != null) {
                        evidence.setThumb(thumb);
                        if(thumb.length > 0) {
                            evidence.setExtraAttribute(HAS_THUMB, true);
                        } else {
                            evidence.setExtraAttribute(HAS_THUMB, false);
                        }
                        return;
                    }
                }
            }
        }else {
            thumbFile = Util.getFileFromHash(new File(output, thumbsFolder), evidence.getHash(), "jpg"); //$NON-NLS-1$
            if (!thumbFile.getParentFile().exists()) {
                thumbFile.getParentFile().mkdirs();
            }

            //if exists, do not need to compute again
            if (thumbFile.exists()) {
                evidence.setThumb(Files.readAllBytes(thumbFile.toPath()));
                if (thumbFile.length() != 0) {
                    evidence.setExtraAttribute(HAS_THUMB, true);
                } else {
                    evidence.setExtraAttribute(HAS_THUMB, false);
                }
                return;
            }
        }

        Future<?> future = executor.submit(new ThumbCreator(evidence, thumbFile));
        try {
            future.get(GraphicsMagicConverter.TIMEOUT + 10, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            future.cancel(true);
            stats.incTimeouts();
            evidence.setExtraAttribute(THUMB_TIMEOUT, "true"); //$NON-NLS-1$
            logger.warn("Timeout creating thumb: " //$NON-NLS-1$
                    + evidence.getPath() + "(" + evidence.getLength() + " bytes)"); //$NON-NLS-1$ //$NON-NLS-2$
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
        long[] performanceStats = new long[numStats];
        File tmp = null;
        try {
            BufferedImage img = null;
            Dimension dimension = null;
            try (BufferedInputStream stream = evidence.getBufferedStream()) {
                dimension = ImageUtil.getImageFileDimension(stream);
            }
            if(extractThumb && isJpeg(evidence)) { //$NON-NLS-1$
                long t = System.currentTimeMillis();
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    img = ImageUtil.getThumb(stream);
                }
                performanceStats[img == null ? 2 : 0]++; 
                performanceStats[img == null ? 3 : 1] += System.currentTimeMillis() - t;
            }
            if (img == null) {
                long t = System.currentTimeMillis();
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    BooleanWrapper renderException = new BooleanWrapper();
                    img = ImageUtil.getSubSampledImage(stream, thumbSize * samplingRatio, thumbSize * samplingRatio,
                            renderException);
                    if (img != null && renderException.value)
                        evidence.setExtraAttribute("thumbException", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                performanceStats[img == null ? 6 : 4]++; 
                performanceStats[img == null ? 7 : 5] += System.currentTimeMillis() - t;
            }
            if (img == null) {
                long t = System.currentTimeMillis();
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    img = graphicsMagicConverter.getImage(stream, thumbSize * samplingRatio, true);
                    if (img != null)
                        evidence.setExtraAttribute("externalThumb", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                    dimension = null;
                } catch (TimeoutException e) {
                    stats.incTimeouts();
                    evidence.setExtraAttribute(THUMB_TIMEOUT, "true"); //$NON-NLS-1$
                    logger.warn("Timeout creating thumb: " //$NON-NLS-1$
                            + evidence.getPath() + "(" + evidence.getLength() + " bytes)"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                performanceStats[img == null ? 10 : 8]++; 
                performanceStats[img == null ? 11 : 9] += System.currentTimeMillis() - t;
            }
            
            if (img != null) {
                if (dimension != null && (dimension.width > thumbSize || dimension.height > thumbSize)
                        && Math.max(img.getWidth(), img.getHeight()) != thumbSize) {
                    long t = System.currentTimeMillis();
                    img = ImageUtil.resizeImage(img, thumbSize, thumbSize);
                    performanceStats[12]++; 
                    performanceStats[13] += System.currentTimeMillis() - t;
                }
                long t = System.currentTimeMillis();
                img = ImageUtil.getOpaqueImage(img);
                performanceStats[14]++; 
                performanceStats[15] += System.currentTimeMillis() - t;

                if(isJpeg(evidence)) {
                    // Ajusta rotacao da miniatura a partir do metadado orientacao
                    try (BufferedInputStream stream = evidence.getBufferedStream()) {
                        int orientation = ImageUtil.getOrientation(stream);
                        if (orientation > 0) {
                            t = System.currentTimeMillis();
                            img = ImageUtil.rotate(img, orientation);
                            performanceStats[16]++; 
                            performanceStats[17] += System.currentTimeMillis() - t;
                        }
                    }
                }

                t = System.currentTimeMillis();
                performanceStats[18]++; 
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos); //$NON-NLS-1$
                evidence.setThumb(baos.toByteArray());
                performanceStats[19] += System.currentTimeMillis() - t;
            }

            if(evidence.getThumb() == null) {
                evidence.setThumb(new byte[0]); //zero size thumb means thumb error
            }
            long t = System.currentTimeMillis();
            if(storeThumbsInDb) {
                Connection con = ExportFileTask.getSQLiteStorageCon(output, evidence.getHashValue().getBytes());
                try(PreparedStatement ps = con.prepareStatement(INSERT_THUMB)){
                    ps.setString(1, evidence.getHash());
                    ps.setBytes(2, evidence.getThumb());
                    ps.setBytes(3, evidence.getThumb());
                    ps.executeUpdate();
                }
            }else {
                tmp = File.createTempFile("iped", ".tmp", new File(output, thumbsFolder)); //$NON-NLS-1$ //$NON-NLS-2$
                Files.write(tmp.toPath(), evidence.getThumb());
            }
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
            logger.warn("Error creating thumb: " //$NON-NLS-1$
                    + evidence.getPath() + "(" + evidence.getLength() + " bytes) " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$

        } finally {
            if (tmp != null && !tmp.renameTo(thumbFile)) {
                tmp.delete();
            }
            
            if(evidence.getThumb() != null && evidence.getThumb().length > 0) {
                evidence.setExtraAttribute(HAS_THUMB, true);
            }else {
                evidence.setExtraAttribute(HAS_THUMB, false);
            }
        }
    }

}
