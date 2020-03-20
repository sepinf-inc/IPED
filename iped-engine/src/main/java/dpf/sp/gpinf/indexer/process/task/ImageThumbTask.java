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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil.BooleanWrapper;
import dpf.sp.gpinf.indexer.util.Log;
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
    
    private static final String INSERT_THUMB = "INSERT INTO t1(id, thumb) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET thumb=?;";

    private static final int samplingRatio = 3;

    public static boolean extractThumb = true;

    private static ExecutorService executor = Executors.newCachedThreadPool();

    public int thumbSize = 160;

    public int galleryThreads = 1;

    public boolean logGalleryRendering = false;

    private boolean taskEnabled = false;
    
    private boolean storeThumbsInDb;

    private GraphicsMagicConverter graphicsMagicConverter;

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
            int dbSuffix = (evidence.getHashValue().getBytes()[0] & 0xFF) >> 4;
            Connection con = (Connection)caseData.getCaseObject(ExportFileTask.STORAGE_CON_PREFIX + dbSuffix);
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
            Log.warning(getClass().getSimpleName(), "Timeout creating thumb: " //$NON-NLS-1$
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

    private void createImageThumb(IItem evidence, File thumbFile) {

        File tmp = null;
        try {
            BufferedImage img = null;
            Dimension dimension = null;
            try (BufferedInputStream stream = evidence.getBufferedStream()) {
                dimension = ImageUtil.getImageFileDimension(stream);
            }
            if(extractThumb && evidence.getMediaType().getSubtype().startsWith("jpeg")) { //$NON-NLS-1$
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    img = ImageUtil.getThumb(stream);
                }
            }
            if (img == null) {
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    BooleanWrapper renderException = new BooleanWrapper();
                    img = ImageUtil.getSubSampledImage(stream, thumbSize * samplingRatio, thumbSize * samplingRatio,
                            renderException);
                    if (img != null && renderException.value)
                        evidence.setExtraAttribute("thumbException", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            if (img == null) {
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    img = graphicsMagicConverter.getImage(stream, thumbSize * samplingRatio, true);
                    if (img != null)
                        evidence.setExtraAttribute("externalThumb", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                    dimension = null;
                } catch (TimeoutException e) {
                    stats.incTimeouts();
                    evidence.setExtraAttribute(THUMB_TIMEOUT, "true"); //$NON-NLS-1$
                    Log.warning(getClass().getSimpleName(), "Timeout creating thumb: " //$NON-NLS-1$
                            + evidence.getPath() + "(" + evidence.getLength() + " bytes)"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            
            if (img != null) {
                if (dimension != null && (dimension.width > thumbSize || dimension.height > thumbSize)
                        && Math.max(img.getWidth(), img.getHeight()) != thumbSize) {
                    img = ImageUtil.resizeImage(img, thumbSize, thumbSize);
                }
                img = ImageUtil.getOpaqueImage(img);

                // Ajusta rotacao da miniatura a partir do metadado orientacao
                try (BufferedInputStream stream = evidence.getBufferedStream()) {
                    int orientation = ImageUtil.getOrientation(stream);
                    if (orientation > 0) {
                        img = ImageUtil.rotate(img, orientation);
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos); //$NON-NLS-1$
                evidence.setThumb(baos.toByteArray());
            }
            
            if(evidence.getThumb() == null) {
                evidence.setThumb(new byte[0]); //zero size thumb means thumb error
            }
            if(storeThumbsInDb) {
                int dbSuffix = (evidence.getHashValue().getBytes()[0] & 0xFF) >> 4;
                Connection con = (Connection)caseData.getCaseObject(ExportFileTask.STORAGE_CON_PREFIX + dbSuffix);
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

        } catch (Throwable e) {
            Log.warning(getClass().getSimpleName(), "Error creating thumb: " //$NON-NLS-1$
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
