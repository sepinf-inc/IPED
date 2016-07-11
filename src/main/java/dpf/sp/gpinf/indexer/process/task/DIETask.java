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
import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.search.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;
import gpinf.die.AbstractDie;
import gpinf.die.RandomForestPredictor;

/**
 * Tarefa de Detecção de Imagens Explícitas (DIE).
 *
 * @author Wladimir Leite
 */
public class DIETask extends AbstractTask {

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
  private static final String taskName = "Detecção de Imagens Explícitas (DIE)";

  /**
   * Nome do atributo incluído com o resultado (score de 1 a 1000) da detecção.
   */
  public static String DIE_SCORE = "scoreNudez";

  /**
   * Nome do atributo incluído com a classificação (de 1 a 5) do resultado da detecção.
   */
  public static String DIE_CLASS = "classeNudez";

  /**
   * Indica se a tarefa está habilitada ou não.
   */
  private static boolean taskEnabled = false;

  /**
   * Objeto estático de inicialização. Necessário para garantir que seja feita apenas uma vez.
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
   * Objeto estático com total de tempo gasto no processamento de imagens, em milisegundos.
   */
  private static final AtomicLong totalTime = new AtomicLong();

  /**
   * Construtor.
   */
  public DIETask(Worker worker) {
    super(worker);
  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
  }
  
  /**
   * Inicializa a tarefa de detecção de nudez.
   */
  @Override
  public void init(Properties confParams, File confDir) throws Exception {
    //Inicialização sincronizada
    synchronized (init) {
      if (!init.get()) {
        //Verifica se tarefa está habilitada
        String value = confParams.getProperty("ledDie");
        if (value == null) {
          Log.info(taskName, "Tarefa desabilitada.");
          init.set(true);
          return;
        }
        File dieDat = new File(value);
        if (!dieDat.exists() || !dieDat.canRead()) {
          Log.error(taskName, "Arquivo de dados do DIE não encontrado em " + dieDat.getAbsolutePath());
          Log.info(taskName, "Tarefa desabilitada.");
          init.set(true);
          return;
        }
        //Cria objeto responsável pela detecção
        predictor = RandomForestPredictor.load(dieDat, -1);
        if (predictor == null) {
          Log.error(taskName, "Erro inicializando arquivo de dados do DIE: " + dieDat.getAbsolutePath());
          Log.info(taskName, "Tarefa desabilitada.");
          init.set(true);
          return;
        }
        //Cria objeto responsável pela extração de features
        die = AbstractDie.loadImplementation(dieDat);
        if (die == null) {
          Log.error(taskName, "Erro inicializando classe do DIE: " + dieDat.getAbsolutePath());
          Log.info(taskName, "Tarefa desabilitada.");
          init.set(true);
          return;
        }
        taskEnabled = true;
        Log.info(taskName, "Tarefa habilitada.");
        Log.info(taskName, "Árvores carregadas: " + predictor.size());
        init.set(true);
      }
    }
  }

  /**
   * Finalização da tarefa. Apenas grava algumas informações sobre o processamento no Log.
   */
  public void finish() throws Exception {
    synchronized (finished) {
      if (taskEnabled && !finished.get()) {
        Log.info(taskName, "Total de Imagens Processadas: " + totalProcessed);
        Log.info(taskName, "Total de Imagens Não Processadas: " + totalFailed);
        long total = totalProcessed.longValue() + totalFailed.longValue();
        if (total != 0) {
          Log.info(taskName, "Tempo de Processamento Médio por Imagem (em milisegundos): " + (totalTime.longValue() / total));
        }
        finished.set(true);
      }
    }
  }

  /**
   * Método principal do processamento. Primeiramente verifica se o tipo de arquivo é imagem. Depois
   * chama método de detecção.
   */
  @Override
  protected void process(EvidenceFile evidence) throws Exception {
    //Verifica se está habilitado e se o tipo de arquivo é tratado
    if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null) {
      return;
    }

    //Chama o método de detecção
    try {
      long t = System.currentTimeMillis();
      File thumbFile = Util.getFileFromHash(new File(output, ImageThumbTask.thumbsFolder), evidence.getHash(), "jpg");
      BufferedImage img;
      if (thumbFile.exists()) {
        img = ImageIO.read(thumbFile);
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
    return mediaType.getType().equals("image") || mediaType.toString().endsWith("msmetafile") || mediaType.toString().endsWith("x-emf");
  }

  /**
   * Obtém a imagem do arquivo. Se tiver miniatura utiliza, senão pega versão redimensionada.
   */
  private BufferedImage getBufferedImage(EvidenceFile evidence) {
    BufferedImage img = null;
    try {
      GalleryValue value = new GalleryValue(null, null, -1);
      if (evidence.getMediaType().getSubtype().startsWith("jpeg")) {
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = ImageUtil.getThumb(stream, value);
        } finally {
          IOUtil.closeQuietly(stream);
        }
      }
      if (img == null) {
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = ImageUtil.getSubSampledImage(stream, die.getExpectedImageSize(), die.getExpectedImageSize(), value);
        } finally {
          IOUtil.closeQuietly(stream);
        }
      }
      if (img == null) {
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = new GraphicsMagicConverter().getImage(stream, die.getExpectedImageSize());
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
