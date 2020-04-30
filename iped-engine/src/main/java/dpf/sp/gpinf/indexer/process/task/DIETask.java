/*
 * Copyright 2015-2016, Wladimir Leite
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

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
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
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Log;
import gpinf.die.AbstractDie;
import gpinf.die.RandomForestPredictor;
import iped3.IItem;

/**
 * Tarefa de Detecção de Imagens Explícitas (DIE).
 *
 * @author Wladimir Leite
 */
public class DIETask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(DIETask.class);

    /**
     * Instância da classe reponsável pelo processo de detecção.
     */
    private static RandomForestPredictor predictor;

    /**
     * Instância da classe reponsável pela extração de features.
     */
    private static AbstractDie die;

    /**
     * Nome da tarefa.
     */
    private static final String taskName = "Explicit Image detection (DIE)"; //$NON-NLS-1$

    /**
     * Nome do atributo incluído com o resultado (score de 1 a 1000) da detecção.
     */
    public static String DIE_SCORE = "scoreNudez"; //$NON-NLS-1$

    /**
     * Nome do atributo incluído com a classificação (de 1 a 5) do resultado da
     * detecção.
     */
    public static String DIE_CLASS = "classeNudez"; //$NON-NLS-1$

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

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
     * Objeto estático com total de imagens processadas.
     */
    private static final AtomicLong totalProcessed = new AtomicLong();

    /**
     * Objeto estático com total de imagens que falharam.
     */
    private static final AtomicLong totalFailed = new AtomicLong();

    /**
     * Objeto estático com total de tempo gasto no processamento de imagens, em
     * milisegundos.
     */
    private static final AtomicLong totalTime = new AtomicLong();

    private static final String ENABLE_PARAM = "enableLedDie"; //$NON-NLS-1$
    
    private static GraphicsMagicConverter graphicsMagicConverter = new GraphicsMagicConverter();

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    /**
     * Inicializa a tarefa de detecção de nudez.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        // Inicialização sincronizada
        synchronized (init) {
            if (!init.get()) {
                // Verifica se tarefa está habilitada
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
                    Log.info(taskName, "Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                File dieDat = new File(diePath.trim());
                if(!dieDat.exists())
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

                // Cria objeto responsável pela detecção
                predictor = RandomForestPredictor.load(dieDat, -1);
                if (predictor == null)
                    throw new IPEDException("Error loading DIE database file: " + dieDat.getAbsolutePath()); //$NON-NLS-1$

                // Cria objeto responsável pela extração de features
                die = AbstractDie.loadImplementation(dieDat);
                if (die == null)
                    throw new IPEDException("Error loading DIE implementation: " + dieDat.getAbsolutePath()); //$NON-NLS-1$

                Log.info(taskName, "Task enabled."); //$NON-NLS-1$
                Log.info(taskName, "Trees loaded: " + predictor.size()); //$NON-NLS-1$
                init.set(true);
            }
        }
    }

    /**
     * Finalização da tarefa. Apenas grava algumas informações sobre o processamento
     * no Log.
     */
    public void finish() throws Exception {
    	synchronized (finished) {
    		graphicsMagicConverter.close();
            if (taskEnabled && !finished.get()) {
                Log.info(taskName, "Total images processed: " + totalProcessed); //$NON-NLS-1$
                Log.info(taskName, "Total images not processed: " + totalFailed); //$NON-NLS-1$
                long total = totalProcessed.longValue() + totalFailed.longValue();
                if (total != 0) {
                    Log.info(taskName,
                            "Mean processing time per image (milliseconds): " + (totalTime.longValue() / total)); //$NON-NLS-1$
                }
                finished.set(true);
            }
        }
    }

    /**
     * Método principal do processamento. Primeiramente verifica se o tipo de
     * arquivo é imagem. Depois chama método de detecção.
     */
    @Override
    protected void process(IItem evidence) throws Exception {
        // Verifica se está habilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase()
                || evidence.getHash() == null) {
            return;
        }
        if (evidence.getExtraAttribute(ImageThumbTask.THUMB_TIMEOUT) != null)
            return;

        // Chama o método de detecção
        try {
            long t = System.currentTimeMillis();
            BufferedImage img;
            if (evidence.getThumb() != null) {
                img = ImageIO.read(new ByteArrayInputStream(evidence.getThumb()));
            } else {
                img = getBufferedImage(evidence);
            }
            List<Float> features = die.extractFeatures(img);
            if (features != null) {
                double p = predictor.predict(features);
                int score = (int) Math.round(p * 1000);
                if (score < 1) {
                    score = 1;
                }
                evidence.setExtraAttribute(DIE_SCORE, score);
                int classe = score / 200 + 1;
                if (classe > 5) {
                    classe = 5;
                } else if (classe < 1) {
                    classe = 1;
                }
                evidence.setExtraAttribute(DIE_CLASS, classe);
                totalProcessed.incrementAndGet();
            } else {
                totalFailed.incrementAndGet();
            }
            t = System.currentTimeMillis() - t;
            totalTime.addAndGet(t);
        } catch (Exception e) {
            Log.warning(taskName, e.toString());
            Log.debug(taskName, e);
        }
    }

    /**
     * Verifica se é imagem.
     */
    public static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image"); //$NON-NLS-1$
    }

    /**
     * Obtém a imagem do arquivo. Se tiver miniatura utiliza, senão pega versão
     * redimensionada.
     */
    private BufferedImage getBufferedImage(IItem evidence) {
        BufferedImage img = null;
        try {
            if (ImageThumbTask.extractThumb && evidence.getMediaType().getSubtype().startsWith("jpeg")) { //$NON-NLS-1$
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
                    img = graphicsMagicConverter.getImage(stream, die.getExpectedImageSize());
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
