/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task.video;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.VideoThumbsConfig;
import iped.engine.core.Statistics;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.ExportFileTask;
import iped.engine.task.ImageThumbTask;
import iped.engine.task.ThumbTask;
import iped.engine.task.die.DIETask;
import iped.engine.util.Util;
import iped.parsers.util.ISO6709Converter;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.ImageUtil;

/**
 * Tarefa de geração de imagem com miniaturas (thumbs) de cenas extraídas de
 * arquivos de vídeo.
 *
 * @author Wladimir Leite
 */
public class VideoThumbTask extends ThumbTask {
    
    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Executável, incluindo caminho do MPlayer.
     */
    private static String mplayer = "mplayer"; //$NON-NLS-1$

    /**
     * Caminho relativo para o MPlayer distribuído para Windows
     */
    public static final String MPLAYER_WIN_PATH = "tools/mplayer/mplayer.exe"; //$NON-NLS-1$

    /**
     * Property to flag frames extracted as subitems from videos.
     */
    private static final String VIDEO_THUMB_PROP = "videoThumbnail"; //$NON-NLS-1$

    /**
     * Category name of frames extracted as subitems from videos.
     */
    private static final String VIDEO_THUMB_CATEGORY = "Video Thumbnails"; //$NON-NLS-1$

    /**
     * Objeto estático de inicialização. Necessário para garantir que seja feita
     * apenas uma vez.
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Objeto estático para sincronizar finalização.
     */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * Objeto estático com total de videos processados .
     */
    private static final AtomicLong totalVideosProcessed = new AtomicLong();

    /**
     * Objeto estático com total de videos que falharam.
     */
    private static final AtomicLong totalVideosFailed = new AtomicLong();

    /**
     * Objeto estático com total de tempo gasto no processamento de vídeos, em
     * milisegundos.
     */
    private static final AtomicLong totalVideosTime = new AtomicLong();

    // Statistics for animated images
    private static final AtomicLong totalAnimatedImagesProcessed = new AtomicLong();
    private static final AtomicLong totalAnimatedImagesFailed = new AtomicLong();
    private static final AtomicLong totalAnimatedImagesTime = new AtomicLong();
    
    private static final AtomicLong totalTimeGallery = new AtomicLong();
    private static final AtomicLong totalGallery = new AtomicLong();

    private static final Logger logger = LoggerFactory.getLogger(VideoThumbTask.class);

    /**
     * Mapa com resultado do processamento dos vídeos
     */
    private static final HashMap<String, VideoProcessResult> processedVideos = new HashMap<String, VideoProcessResult>();

    private static final Map<String, String> videoToTikaMetadata = getVideoToTikaMetadata();

    /**
     * Instância da classe reponsável pelo processo de geração de thumbs.
     */
    private VideoThumbsMaker videoThumbsMaker;

    /**
     * Lista de configurações de extração a serem geradas por vídeo.
     */
    private List<VideoThumbsOutputConfig> configs;

    /**
     * Configuração principal de extração de cenas.
     */
    private VideoThumbsOutputConfig mainConfig;

    /**
     * Pasta de saída das imagens.
     */
    private File baseFolder;

    /**
     * Pasta temporária, utilizada como saído do MPlayer na extração de frames.
     */
    private File tmpFolder;

    /**
     * Nome do arquivo temporário de miniatura gerado. Após conclusão será renomeado
     * pra nome definitivo.
     */
    private String tempSuffix;

    /**
     * Property to be set if the evidence is a animated image (i.e. contain multiple
     * frames). Only set if the number of frames is greater than one.
     */
    public static final String ANIMATION_FRAMES_PROP = ExtraProperties.IMAGE_META_PREFIX + "AnimationFrames";
    
    private ISO6709Converter iso6709Converter = new ISO6709Converter();

    private VideoThumbsConfig videoConfig;

    private static final Map<String, String> getVideoToTikaMetadata() {
        Map<String, String> map = new HashMap<>();
        map.put("creation_time", TikaCoreProperties.CREATED.getName());
        map.put("title", TikaCoreProperties.TITLE.getName());
        map.put("artist", XMPDM.ARTIST.getName());
        map.put("album", XMPDM.ALBUM.getName());
        map.put("album_artist", XMPDM.ALBUM_ARTIST.getName());
        map.put("comment", XMPDM.LOG_COMMENT.getName());
        map.put("encoder", XMP.CREATOR_TOOL.getName());
        map.put("genre", XMPDM.GENRE.getName());
        return map;
    }

    private static String normalizeMetadata(String meta) {
        meta = videoToTikaMetadata.getOrDefault(meta, meta);
        if (meta.endsWith("-eng") || meta.endsWith("-por")) {
            meta = meta.substring(0, meta.length() - 4);
        }
        return meta;
    }

    public List<Configurable<?>> getConfigurables() {
        VideoThumbsConfig result = ConfigurationManager.get().findObject(VideoThumbsConfig.class);
        if(result == null) {
            result = new VideoThumbsConfig();
        }
        return Arrays.asList(result);
    }

    /**
     * Inicializa a tarefa de processamento de vídeos. Carrega configurações sobre o
     * tamanho/layout a ser gerado e camimnho do MPlayer, que é o programa
     * responsável pela extração de frames.
     */
    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        // Instância objeto responsável pela extração de frames e inicializa parâmetros
        // de utilização
        videoThumbsMaker = new VideoThumbsMaker();

        // Inicializa pasta temporarária e sufixo de arquivos temporários
        tmpFolder = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
        tempSuffix = Thread.currentThread().getId() + ".tmp"; //$NON-NLS-1$

        videoConfig = configurationManager.findObject(VideoThumbsConfig.class);

        // Inicialização sincronizada
        synchronized (init) {
            if (!init.get()) {
                // Verifica se tarefa está habilitada
                if (videoConfig.isEnabled()) {
                    taskEnabled = true;
                } else {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                    mplayer = Configuration.getInstance().appRoot + "/" + MPLAYER_WIN_PATH; // $NON-NLS-1$
                }
                videoThumbsMaker.setMPlayer(mplayer);

                // Testa se o MPlayer está funcionando
                String vmp = videoThumbsMaker.getVersion();
                if (vmp == null) {
                    logger.error("Error testing MPLAYER!"); //$NON-NLS-1$
                    logger.error("MPlayer Configured = " + mplayer); //$NON-NLS-1$
                    logger.error("Check mplayer path and try to run it from terminal."); //$NON-NLS-1$
                    taskEnabled = false;
                    init.set(true);
                }
                logger.info("Task enabled."); //$NON-NLS-1$
                logger.info("MPLAYER version: " + vmp); //$NON-NLS-1$
                init.set(true);
            }
        }

        // Não continua se tarefa foi desabilitada
        if (!taskEnabled) {
            return;
        }

        // Inicializa parâmetros
        videoThumbsMaker.setMPlayer(mplayer);
        videoThumbsMaker.setVerbose(videoConfig.isVerbose());
        videoThumbsMaker.setTimeoutFirstCall(videoConfig.getTimeoutFirst());
        videoThumbsMaker.setTimeoutProcess(videoConfig.getTimeoutProcess());
        videoThumbsMaker.setTimeoutInfo(videoConfig.getTimeoutInfo());
        videoThumbsMaker.setVideoThumbsOriginalDimension(videoConfig.getVideoThumbsOriginalDimension());
        videoThumbsMaker.setMaxDimensionSize(videoConfig.getMaxDimensionSize());

        // Cria configurações de extração de cenas
        configs = new ArrayList<VideoThumbsOutputConfig>();
        configs.add(mainConfig = new VideoThumbsOutputConfig(null, videoConfig.getWidth(), videoConfig.getColumns(), videoConfig.getRows(), 2));

        // Inicializa diretório de saída
        baseFolder = new File(output, "view"); //$NON-NLS-1$
        if (!baseFolder.exists()) {
            baseFolder.mkdirs();
        }
    }

    @Override
    public boolean isEnabled() {
        return videoConfig.isEnabled();
    }

    /**
     * Finalização da tarefa. Apenas grava algumas informações sobre o processamento
     * no Log.
     */
    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                processedVideos.clear();
                finished.set(true);

                // Videos statistics
                logger.info("Total videos processed: " + totalVideosProcessed); //$NON-NLS-1$
                logger.info("Total videos failed (MPlayer failed to create thumbs): " + totalVideosFailed); //$NON-NLS-1$
                long total = totalVideosProcessed.longValue() + totalVideosFailed.longValue();
                if (total > 0)
                    logger.info("Average video processing time (milliseconds/video): " //$NON-NLS-1$
                            + (totalVideosTime.longValue() / total));

                // Animated images statistics
                logger.info("Total animated images processed: " + totalAnimatedImagesProcessed); //$NON-NLS-1$
                logger.info(
                        "Total animated images failed (MPlayer failed to create thumbs): " + totalAnimatedImagesFailed); //$NON-NLS-1$
                total = totalAnimatedImagesProcessed.longValue() + totalAnimatedImagesFailed.longValue();
                if (total > 0)
                    logger.info("Average animated image processing time (milliseconds/image): " //$NON-NLS-1$
                            + (totalAnimatedImagesTime.longValue() / total));

                // Gallery thumb generation statistics
                total = totalGallery.longValue();
                if (total > 0) {
                    logger.info("Total gallery thumbs generated: " + total); //$NON-NLS-1$
                    logger.info("Average gallery thumb generation time (milliseconds/item): " //$NON-NLS-1$
                            + (totalTimeGallery.longValue() / total));
                }
            }
        }
    }

    /**
     * Método principal do processamento. Primeiramente verifica se o tipo de
     * arquivo é vídeo. Depois chama método da classe, informando o caminho do
     * arquivo de entrada e caminho completo de destino.
     */
    @Override
    protected void process(IItem evidence) throws Exception {

        if (evidence.getExtraAttribute(VIDEO_THUMB_PROP) != null) {
            evidence.setCategory(VIDEO_THUMB_CATEGORY);
        }

        // Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || (!isVideoType(evidence.getMediaType()) && !checkAnimatedImage(evidence)) || !evidence.isToAddToCase()
                || evidence.getHashValue() == null) {
            return;
        }

        if (caseData.isIpedReport() && evidence.getViewFile() != null && evidence.getViewFile().length() > 0) {
            return;
        }

        File mainOutFile = Util.getFileFromHash(baseFolder, evidence.getHash(), "jpg"); //$NON-NLS-1$

        // TODO: update this results reusage logic to work when frames as subitems is
        // enabled
        if (!videoConfig.getVideoThumbsSubitems()) {
            synchronized (processedVideos) {
                if (processedVideos.containsKey(evidence.getHash())) {
                    while (processedVideos.get(evidence.getHash()) == null) {
                        processedVideos.wait();
                    }
                    VideoProcessResult r = processedVideos.get(evidence.getHash());
                    evidence.setExtraAttribute(HAS_THUMB, r.isSuccess());
                    if (r.isSuccess()) {
                        saveMetadata(r, evidence.getMetadata());
                        evidence.setViewFile(mainOutFile);
                        File thumbFile = getThumbFile(evidence);
                        hasThumb(evidence, thumbFile);
                    }
                    return;
                }
                processedVideos.put(evidence.getHash(), null);
            }
        }

        // Chama o método de extração de cenas
        File mainTmpFile = null;
        VideoProcessResult r = null;
        try {
            if (!mainOutFile.getParentFile().exists()) {
                mainOutFile.getParentFile().mkdirs();
            }

            // if output file exists and subitems are disabled, reuse previous result
            if (mainOutFile.exists() && !videoConfig.getVideoThumbsSubitems()) {
                synchronized (processedVideos) {
                    r = processedVideos.get(evidence.getHash());
                }
            }
            if (r == null) {
                mainTmpFile = new File(mainOutFile.getParentFile(), evidence.getHash() + tempSuffix);
                mainConfig.setOutFile(mainTmpFile);

                //Check if it is an animated image
                int numFrames = 0;
                boolean isAnimated = isImageSequence(evidence.getMediaType().toString());
                if (!isAnimated) {
                    String strFrames = evidence.getMetadata().get(ANIMATION_FRAMES_PROP);
                    if (strFrames != null) {
                        numFrames = Integer.parseInt(strFrames);
                        if (numFrames > 0)
                            isAnimated = true;
                    }
                }

                long t = System.currentTimeMillis();
                r = videoThumbsMaker.createThumbs(evidence.getTempFile(), tmpFolder, configs, numFrames);
                t = System.currentTimeMillis() - t;

                if (r != null && isAnimated) {
                    //Clear video duration for animated images
                    r.setVideoDuration(-1);
                }

                if (r.isSuccess() && videoConfig.getVideoThumbsSubitems()) {
                    generateSubitems(evidence, mainConfig, r.getFrames(), r.getDimension());
                }

                if (r.isSuccess() && (mainOutFile.exists() || mainTmpFile.renameTo(mainOutFile))) {
                    (isAnimated ? totalAnimatedImagesProcessed : totalVideosProcessed).incrementAndGet();
                } else {
                    r.setSuccess(false);
                    (isAnimated ? totalAnimatedImagesFailed : totalVideosFailed).incrementAndGet();
                    if (r.isTimeout()) {
                        stats.incTimeouts();
                        evidence.setExtraAttribute(ImageThumbTask.THUMB_TIMEOUT, "true"); //$NON-NLS-1$
                        logger.warn("Timeout creating video thumbs: " + evidence.getPath() + "(" //$NON-NLS-1$ //$NON-NLS-2$
                                + evidence.getLength() + " bytes)"); //$NON-NLS-1$
                    }
                }
                (isAnimated ? totalAnimatedImagesTime : totalVideosTime).addAndGet(t);
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);

        } finally {
            // Tenta apaga possível temporários deixados "perdidos" (no caso normal eles
            // foram renomeados)
            if (mainTmpFile != null && mainTmpFile.exists()) {
                mainTmpFile.delete();
            }

            if (r == null)
                r = new VideoProcessResult(null);

            // Atualiza atributo HasThumb do item
            evidence.setExtraAttribute(HAS_THUMB, r.isSuccess());
            if (r.isSuccess()) {
                saveMetadata(r, evidence.getMetadata());
                evidence.setViewFile(mainOutFile);
            }

            // If enabled (galleryThumbWidth > 0) create a thumb to be shown in the gallery,
            // with fewer frames
            int galleryThumbWidth = videoConfig.getGalleryThumbWidth();
            if (galleryThumbWidth > 0 && r.isSuccess()) {
                try {
                    long t = System.currentTimeMillis();
                    Object[] read = ImageUtil.readJpegWithMetaData(mainOutFile);
                    BufferedImage fullImg = (BufferedImage) read[0];
                    String comment = (String) read[1];
                    int galleryThumbHeight = galleryThumbWidth / 30 * 29;
                    BufferedImage img = ImageUtil.getBestFramesFit(fullImg, comment, galleryThumbWidth,
                            galleryThumbHeight, videoConfig.getGalleryMinThumbs(), videoConfig.getGalleryMaxThumbs());

                    if (img != null && !img.equals(fullImg)) {
                        if (img.getWidth() > galleryThumbWidth || img.getHeight() > galleryThumbHeight) {
                            img = ImageUtil.resizeImage(img, galleryThumbWidth, galleryThumbHeight);
                        }
                        img = ImageUtil.getOpaqueImage(img);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(img, "jpg", baos); //$NON-NLS-1$
                        evidence.setThumb(baos.toByteArray());
                        File thumbFile = getThumbFile(evidence);
                        saveThumb(evidence, thumbFile);
                        t = System.currentTimeMillis() - t;
                        totalTimeGallery.addAndGet(t);
                        totalGallery.incrementAndGet();
                    }
                } catch (Throwable e) {
                    logger.warn(evidence.toString(), e);
                } finally {
                    updateHasThumb(evidence);
                }
            }

            if (!videoConfig.getVideoThumbsSubitems()) {
                // store processing result to be reused
                synchronized (processedVideos) {
                    processedVideos.put(evidence.getHash(), r);
                    processedVideos.notifyAll();
                }
            }

            // TODO: this deletes temp frames, so the logic to reuse frames from
            // duplicated videos should be updated
            r.close();

        }
    }

    private void saveMetadata(VideoProcessResult r, Metadata metadata) {
        long bitrate = r.getBitRate();
        if (bitrate != -1)
            metadata.set("bitrate", Long.toString(bitrate)); //$NON-NLS-1$
        float fps = r.getFPS();
        if (fps != -1)
            metadata.set("framerate", Float.toString(fps)); //$NON-NLS-1$
        String codec = r.getVideoCodec();
        if (codec != null && !codec.isEmpty())
            metadata.set("codec", codec); //$NON-NLS-1$
        String format = r.getVideoFormat();
        if (format != null && !format.isEmpty())
            metadata.set("format", format); //$NON-NLS-1$
        double duration = r.getVideoDuration();
        if (duration != -1)
            metadata.set(XMPDM.DURATION.getName(), Double.toString(duration / 1000)); // $NON-NLS-1$
        Dimension d = r.getDimension();
        if (d != null) {
            metadata.set(Metadata.IMAGE_WIDTH.getName(), Integer.toString(d.width)); // $NON-NLS-1$
            metadata.set(Metadata.IMAGE_LENGTH.getName(), Integer.toString(d.height)); // $NON-NLS-1$
        }
        int rot = r.getRotation();
        if (rot > 0) {
            metadata.set("rotation", Integer.toString(rot)); //$NON-NLS-1$
        }
        for (Entry<String, String> meta : r.getClipInfos().entrySet()) {
            String key = meta.getKey();
            key = normalizeMetadata(key);
            if ("location".equals(key)) {
                iso6709Converter.populateLocation(metadata, meta.getValue());
            } else {
                metadata.add(key, meta.getValue());
            }
        }

    }

    private void generateSubitems(IItem item, VideoThumbsOutputConfig config, List<File> frames, Dimension dimension)
            throws IOException {

        int w, h;

        // Setting dimension for video subitems
        if (videoConfig.getVideoThumbsOriginalDimension()) {
            w = dimension.width;
            h = dimension.height;
        } else {
            w = config.getThumbWidth();
            h = dimension.height * w / dimension.width;
        }
        if (w > videoConfig.getMaxDimensionSize()) {
            w = videoConfig.getMaxDimensionSize();
        }
        if (h > videoConfig.getMaxDimensionSize()) {
            h = videoConfig.getMaxDimensionSize();
        }

        item.setHasChildren(true);

        List<Double> framesNudityScore = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {

            File frame = frames.get(i);

            // create a new item and set parent-child relationship
            Item newItem = new Item();
            newItem.setParent(item);

            // set basic properties
            String seqStr = new String("00000" + i);
            String name = item.getName() + "_thumb_" + seqStr.substring(seqStr.length() - 5);
            newItem.setName(name);
            newItem.setPath(item.getPath() + ">>" + name);
            newItem.setExtraAttribute(VIDEO_THUMB_PROP, true);
            newItem.setSubItem(true);
            newItem.setSubitemId(i);

            newItem.setAccessDate(item.getAccessDate());
            newItem.setModificationDate(item.getModDate());
            newItem.setCreationDate(item.getCreationDate());
            newItem.setChangeDate(item.getChangeDate());

            ExportFileTask extractor = new ExportFileTask();
            extractor.setWorker(worker);

            // export thumb data to internal database
            BufferedImage img = adjustFrameDimension(ImageIO.read(frame), w, h);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
            extractor.extractFile(is, newItem, item.getLength());

            Statistics.get().incSubitemsDiscovered();
            // we don't add subitem size to processed items stats
            newItem.setSumVolume(false);

            // add new item to processing queue
            worker.processNewItem(newItem, ProcessTime.NOW);

            Double nudityScore = (Double) newItem.getTempAttribute(DIETask.DIE_RAW_SCORE);
            if (nudityScore != null) {
                framesNudityScore.add(nudityScore);
            }

        }

        if (!framesNudityScore.isEmpty()) {
            item.setTempAttribute(DIETask.DIE_RAW_SCORE, framesNudityScore);
        }

    }

    private BufferedImage adjustFrameDimension(BufferedImage original, int wFinal, int hFinal) {
        BufferedImage img = new BufferedImage(wFinal, hFinal, BufferedImage.TYPE_INT_BGR);
        Graphics2D g2 = (Graphics2D) img.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (original.getWidth() == wFinal && original.getHeight() == hFinal) {
            return original;
        } else {
            g2.drawImage(original, 0, 0, wFinal, hFinal, null);
        }
        return img;
    }

    /**
     * Verifica se é vídeo.
     */
    public static boolean isVideoType(MediaType mediaType) {
        return MetadataUtil.isVideoType(mediaType);
    }
    

    /**
     * Check if the evidence's mediaType is an image sequence.
     */
    public static boolean isImageSequence(String mediaType) {
        return mediaType.equals("image/heic-sequence") || mediaType.equals("image/heif-sequence");
    }

    /** 
     * Checks if the evidence is an animated image, and update
     * its metadata if this is the case.
     */
    private static boolean checkAnimatedImage(IItem evidence) {
        int numImages = -1;
        String mediaType = evidence.getMediaType().toString();

        if (isImageSequence(mediaType)) {
            return true;

        } else if (mediaType.equals("image/gif")) {
            ImageReader reader = null;
            try (BufferedInputStream is = evidence.getBufferedInputStream(); ImageInputStream iis = ImageIO.createImageInputStream(is)) {
                reader = ImageIO.getImageReaders(iis).next();
                reader.setInput(iis, false, true);
                numImages = reader.getNumImages(true);
            } catch (Exception e) {
            } finally {
                if (reader != null)
                    reader.dispose();
            }
        
        } else if (mediaType.equals("image/png")) {
            byte[] b = new byte[128];
            try (BufferedInputStream is = evidence.getBufferedInputStream()) {
                int read = IOUtils.read(is, b);
                for (int i = 0; i <= read - 8; i++) {
                    if (b[i] == 'a' && b[i + 1] == 'c' && b[i + 2] == 'T' && b[i + 3] == 'L') {
                        numImages = ((b[i + 5] & 0xFF) << 16) | ((b[i + 6] & 0xFF) << 8) | (b[i + 7] & 0xFF);
                        break;
                    }
                }
            } catch (Exception e) {
            }
        }
        
        if (numImages > 1) {
            // Set only for images with multiple animated frames
            evidence.getMetadata().set(ANIMATION_FRAMES_PROP, String.valueOf(numImages));
            return true;
        }
        return false;
    }
}
