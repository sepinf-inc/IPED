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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.video.VideoProcessResult;
import gpinf.video.VideoThumbsMaker;
import gpinf.video.VideoThumbsOutputConfig;
import iped3.IItem;
import iped3.util.ExtraProperties;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Tarefa de geração de imagem com miniaturas (thumbs) de cenas extraídas de
 * arquivos de vídeo.
 *
 * @author Wladimir Leite
 */
public class VideoThumbTask extends ThumbTask {

    /**
     * Instância da classe reponsável pelo processo de geração de thumbs.
     */
    private VideoThumbsMaker videoThumbsMaker;

    /**
     * Lista de configurações de extração a serem geradas por vídeo.
     */
    private List<VideoThumbsOutputConfig> configs;

    /**
     * Nome da tarefa.
     */
    private static final String taskName = "Video Thumbs Maker"; //$NON-NLS-1$

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
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Constante com o nome utilizado para o arquivo de propriedades.
     */
    private static final String configFileName = "VideoThumbsConfig.txt"; //$NON-NLS-1$

    /**
     * Executável, incluindo caminho do MPlayer.
     */
    private static String mplayer = "mplayer"; //$NON-NLS-1$

    /**
     * Caminho relativo para o MPlayer distribuído para Windows
     */
    public static String mplayerWin = "../mplayer/mplayer.exe"; //$NON-NLS-1$

    /**
     * Largura da imagem das cenas geradas.
     */
    private static int width = 200;

    /**
     * Número de colunas.
     */
    private static int columns = 3;

    /**
     * Número de linhas.
     */
    private static int rows = 6;

    /**
     * Timeout da primeira execução do MPlayer. É maior para tratar possível
     * processamento de fontes.
     */
    private static int timeoutFirst = 180000;

    /**
     * Timeout na chamada de obtenção de propriedades do vídeo.
     */
    private static int timeoutInfo = 10000;

    /**
     * Timeout na chamada normal de processamento do vídeo.
     */
    private static int timeoutProcess = 15000;

    /**
     * Opção de redirecionamento da saída do MPlayer para o log, apenas para
     * depuração de problemas.
     */
    private static boolean verbose = false;

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
    private static final AtomicLong totalProcessed = new AtomicLong();

    /**
     * Objeto estático com total de videos que falharam.
     */
    private static final AtomicLong totalFailed = new AtomicLong();

    /**
     * Objeto estático com total de tempo gasto no processamento de vídeos, em
     * milisegundos.
     */
    private static final AtomicLong totalTime = new AtomicLong();

    private static final AtomicLong totalTimeGallery = new AtomicLong();
    private static final AtomicLong totalGallery = new AtomicLong();

    private static int galleryThumbWidth = -1;
    private static int galleryMinThumbs = -1;
    private static int galleryMaxThumbs = -1;

    private static final Logger logger = LoggerFactory.getLogger(VideoThumbTask.class);

    /**
     * Mapa com resultado do processamento dos vídeos
     */
    private static final HashMap<String, VideoProcessResult> processedVideos = new HashMap<String, VideoProcessResult>();

    /**
     * Inicializa a tarefa de processamento de vídeos. Carrega configurações sobre o
     * tamanho/layout a ser gerado e camimnho do MPlayer, que é o programa
     * responsável pela extração de frames.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        // Instância objeto responsável pela extração de frames e inicializa parâmetros
        // de utilização
        videoThumbsMaker = new VideoThumbsMaker();

        // Inicializa pasta temporarária e sufixo de arquivos temporários
        tmpFolder = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
        tempSuffix = Thread.currentThread().getId() + ".tmp"; //$NON-NLS-1$

        // Inicialização sincronizada
        synchronized (init) {
            if (!init.get()) {
                // Verifica se tarefa está habilitada
                String value = confParams.getProperty("enableVideoThumbs"); //$NON-NLS-1$
                if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
                    taskEnabled = true;
                } else {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                // Lê parâmetros do arquivo de configuração
                UTF8Properties properties = new UTF8Properties();
                File confFile = new File(confDir, configFileName);
                try {
                    properties.load(confFile);

                    // Layout
                    value = properties.getProperty("Layout"); //$NON-NLS-1$
                    if (value != null) {
                        String[] vals = value.trim().split(","); //$NON-NLS-1$
                        if (vals.length == 3) {
                            width = Integer.parseInt(vals[0].trim());
                            columns = Integer.parseInt(vals[1].trim());
                            rows = Integer.parseInt(vals[2].trim());
                        }
                    }

                    // Verbose do MPlayer
                    value = properties.getProperty("Verbose"); //$NON-NLS-1$
                    if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
                        verbose = true;
                    }

                    // Timeouts
                    value = properties.getProperty("Timeouts"); //$NON-NLS-1$
                    if (value != null) {
                        String[] vals = value.trim().split(","); //$NON-NLS-1$
                        if (vals.length == 3) {
                            timeoutFirst = 1000 * Integer.parseInt(vals[0].trim());
                            timeoutInfo = 1000 * Integer.parseInt(vals[1].trim());
                            timeoutProcess = 1000 * Integer.parseInt(vals[2].trim());
                        }
                    }

                    // Layout
                    value = properties.getProperty("GalleryThumbs"); //$NON-NLS-1$
                    if (value != null) {
                        String[] vals = value.trim().split(","); //$NON-NLS-1$
                        if (vals.length == 3) {
                            galleryThumbWidth = Integer.parseInt(vals[0].trim());
                            galleryMinThumbs = Integer.parseInt(vals[1].trim());
                            galleryMaxThumbs = Integer.parseInt(vals[2].trim());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error loading conf file: " + confFile.getAbsolutePath()); //$NON-NLS-1$
                    taskEnabled = false;
                    init.set(true);
                    throw new IPEDException("Error loading conf file: " + confFile.getAbsolutePath()); //$NON-NLS-1$
                }

                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                    mplayer = Configuration.getInstance().appRoot + "/" + mplayerWin; //$NON-NLS-1$
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
        videoThumbsMaker.setVerbose(verbose);
        videoThumbsMaker.setTimeoutFirstCall(timeoutFirst);
        videoThumbsMaker.setTimeoutProcess(timeoutProcess);
        videoThumbsMaker.setTimeoutInfo(timeoutInfo);

        // Cria configurações de extração de cenas
        configs = new ArrayList<VideoThumbsOutputConfig>();
        configs.add(mainConfig = new VideoThumbsOutputConfig(null, width, columns, rows, 2));

        // Inicializa diretório de saída
        baseFolder = new File(output, "view"); //$NON-NLS-1$
        if (!baseFolder.exists()) {
            baseFolder.mkdirs();
        }
    }

    @Override
    public boolean isEnabled() {
        return taskEnabled;
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
                logger.info("Total videos processed: " + totalProcessed); //$NON-NLS-1$
                logger.info("Total videos failed (MPlayer failed to create thumbs): " + totalFailed); //$NON-NLS-1$
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total > 0) {
                    logger.info("Average processing time (milliseconds/video): " + (totalTime.longValue() / total)); //$NON-NLS-1$
                }
                total = totalGallery.longValue();
                if (total > 0) {
                    logger.info("Total gallery thumbs generated: " + total); //$NON-NLS-1$
                    logger.info("Average gallery thumb generation time (milliseconds/video): " + (totalTimeGallery.longValue() / total)); //$NON-NLS-1$
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
        // Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || !isVideoType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null) {
            return;
        }

        // Verifica se outro vídeo igual foi ou está em processamento
        synchronized (processedVideos) {
            if (processedVideos.containsKey(evidence.getHash())) {
                while (processedVideos.get(evidence.getHash()) == null) {
                    processedVideos.wait();
                }
                VideoProcessResult r = processedVideos.get(evidence.getHash());
                evidence.setExtraAttribute(HAS_THUMB, r.isSuccess());
                if (r.isSuccess())
                    saveMetadata(r, evidence.getMetadata());
                return;
            }
            
            processedVideos.put(evidence.getHash(), null);
        }

        // Chama o método de extração de cenas
        File mainTmpFile = null;
        File mainOutFile = null;
        VideoProcessResult r = null;
        try {
            mainOutFile = Util.getFileFromHash(baseFolder, evidence.getHash(), "jpg"); //$NON-NLS-1$
            if (!mainOutFile.getParentFile().exists()) {
                mainOutFile.getParentFile().mkdirs();
            }

            // Já está pasta? Então não é necessário gerar.
            if (mainOutFile.exists()) {
                synchronized (processedVideos) {
                    r = processedVideos.get(evidence.getHash());
                }
            }
            if (r == null) {
                mainTmpFile = new File(mainOutFile.getParentFile(), evidence.getHash() + tempSuffix);
                mainConfig.setOutFile(mainTmpFile);

                long t = System.currentTimeMillis();
                r = videoThumbsMaker.createThumbs(evidence.getTempFile(), tmpFolder, configs);
                t = System.currentTimeMillis() - t;
                if (r.isSuccess() && (mainOutFile.exists() || mainTmpFile.renameTo(mainOutFile))) {
                    totalProcessed.incrementAndGet();
                } else {
                    r.setSuccess(false);
                    totalFailed.incrementAndGet();
                    if (r.isTimeout()) {
                        stats.incTimeouts();
                        evidence.setExtraAttribute(ImageThumbTask.THUMB_TIMEOUT, "true"); //$NON-NLS-1$
                        logger.warn("Timeout creating video thumbs: " + evidence.getPath() + "(" //$NON-NLS-1$ //$NON-NLS-2$
                                + evidence.getLength() + " bytes)"); //$NON-NLS-1$
                    }
                }
                totalTime.addAndGet(t);
            }
        } catch (Exception e) {
            logger.warn(evidence + " : " + e.toString()); //$NON-NLS-1$
            logger.debug(taskName, e);

        } finally {
            // Tenta apaga possível temporários deixados "perdidos" (no caso normal eles
            // foram renomeados)
            if (mainTmpFile != null && mainTmpFile.exists()) {
                mainTmpFile.delete();
            }

            if (r == null)
                r = new VideoProcessResult();

            // Atualiza atributo HasThumb do item
            evidence.setExtraAttribute(HAS_THUMB, r.isSuccess());
            if (r.isSuccess())
                saveMetadata(r, evidence.getMetadata());

            // Guarda resultado do processamento
            synchronized (processedVideos) {
                processedVideos.put(evidence.getHash(), r);
                processedVideos.notifyAll();
            }

        }
        
        //If enabled (galleryThumbWidth>0) create a thumb to be shown in the gallery, with fewer frames
        if (galleryThumbWidth > 0 && mainOutFile != null && mainOutFile.exists()) {
            try {
                long t = System.currentTimeMillis();
                Object[] read = ImageUtil.readJpegWithMetaData(mainOutFile);
                BufferedImage fullImg = (BufferedImage)read[0];
                String comment = (String)read[1];
                int galleryThumbHeight = galleryThumbWidth / 30 * 29;
                BufferedImage img = ImageUtil.getBestFramesFit(fullImg, comment, galleryThumbWidth, galleryThumbHeight, 
                        galleryMinThumbs, galleryMaxThumbs);
                
                if (img != null && !img.equals(fullImg)) {
                    if (img.getWidth() > galleryThumbWidth || img.getHeight() > galleryThumbHeight) {
                        img = ImageUtil.resizeImage(img, galleryThumbWidth, galleryThumbHeight);
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos); //$NON-NLS-1$
                    evidence.setThumb(baos.toByteArray());
                    File thumbFile = getThumbFile(evidence);
                    saveThumb(evidence, thumbFile);
                    t = System.currentTimeMillis() - t;
                    totalTimeGallery.incrementAndGet();
                    totalGallery.incrementAndGet();
                }
            } catch (Throwable e) {
                logger.warn("Error creating thumb: " + evidence.getPath() + "(" + evidence.getLength() + " bytes) " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                logger.debug(taskName, e);
            }                
        }
    }

    private void saveMetadata(VideoProcessResult r, Metadata metadata) {
        long bitrate = r.getBitRate();
        if (bitrate != -1)
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "bitrate", Long.toString(bitrate)); //$NON-NLS-1$
        float fps = r.getFPS();
        if (fps != -1)
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "framerate", Float.toString(fps)); //$NON-NLS-1$
        String codec = r.getVideoCodec();
        if (codec != null && !codec.isEmpty())
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "codec", codec); //$NON-NLS-1$
        String format = r.getVideoFormat();
        if (format != null && !format.isEmpty())
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "format", format); //$NON-NLS-1$
        double duration = r.getVideoDuration();
        if (duration != -1)
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "duration", Double.toString(duration / 1000)); //$NON-NLS-1$
        Dimension d = r.getDimension();
        if (d != null) {
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "width", Integer.toString(d.width)); //$NON-NLS-1$
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + "height", Integer.toString(d.height)); //$NON-NLS-1$
        }
        for (Entry<String, String> meta : r.getClipInfos().entrySet()) {
            metadata.set(ExtraProperties.VIDEO_META_PREFIX + meta.getKey(), meta.getValue());
        }

    }

    /**
     * Verifica se é vídeo.
     */
    public static boolean isVideoType(MediaType mediaType) {
        return mediaType.getType().equals("video") //$NON-NLS-1$
                || mediaType.getBaseType().toString().equals("application/vnd.rn-realmedia"); //$NON-NLS-1$
    }
}
