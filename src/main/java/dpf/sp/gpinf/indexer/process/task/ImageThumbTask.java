package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.search.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

public class ImageThumbTask extends AbstractTask {

  public ImageThumbTask(Worker worker) {
    super(worker);
  }

  public static final String thumbsFolder = "thumbs";

  private static final String enableProperty = "enableImageThumbs";

  private static final String externalToolPath = "externalToolPath";

  public static final String HAS_THUMB = "hasThumb";

  public static final String THUMB_TIMEOUT = "imgThumbTimeout";

  private static final String TASK_CONFIG_FILE = "ImageThumbsConfig.txt";

  private static final int samplingRatio = 3;

  public int thumbSize = 160;

  public int galleryThreads = 1;

  private boolean taskEnabled = false;

  @Override
  public void init(Properties confParams, File confDir) throws Exception {

    taskEnabled = Boolean.valueOf(confParams.getProperty(enableProperty));

    UTF8Properties properties = new UTF8Properties();
    File confFile = new File(confDir, TASK_CONFIG_FILE);
    properties.load(confFile);

    String value = properties.getProperty("externalConversionTool");
    if (value != null && !value.trim().isEmpty()) {
      if (!value.trim().equals("graphicsmagick")) {
        GraphicsMagicConverter.USE_GM = false;
      }
    } else {
      GraphicsMagicConverter.enabled = false;
    }

    value = properties.getProperty(externalToolPath);
    if (value != null && !value.trim().isEmpty()) {
      GraphicsMagicConverter.toolPathWin = new File(confDir.getParentFile(), value.trim()).getCanonicalPath();
    }

    value = properties.getProperty("imgConvTimeout");
    if (value != null && !value.trim().isEmpty()) {
      GraphicsMagicConverter.TIMEOUT = Integer.valueOf(value.trim());
    }

    value = properties.getProperty("galleryThreads");
    if (value != null && !value.trim().equalsIgnoreCase("default")) {
      galleryThreads = Integer.valueOf(value.trim());
    } else {
      galleryThreads = Runtime.getRuntime().availableProcessors();
    }

    value = properties.getProperty("imgThumbSize");
    if (value != null && !value.trim().isEmpty()) {
      thumbSize = Integer.valueOf(value.trim());
    }
  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  protected void process(EvidenceFile evidence) throws Exception {

    if (!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null) {
      return;
    }

    File thumbFile = Util.getFileFromHash(new File(output, thumbsFolder), evidence.getHash(), "jpg");
    if (!thumbFile.getParentFile().exists()) {
      thumbFile.getParentFile().mkdirs();
    }

    //Já está na pasta? Então não é necessário gerar.
    if (thumbFile.exists()) {
      if (thumbFile.length() != 0) {
        evidence.setExtraAttribute(HAS_THUMB, true);
      } else {
        evidence.setExtraAttribute(HAS_THUMB, false);
      }
      return;
    }

    createImageThumb(evidence, thumbFile);

  }

  /**
   * Verifica se é imagem.
   */
  public static boolean isImageType(MediaType mediaType) {
    return mediaType.getType().equals("image")
        || mediaType.toString().endsWith("msmetafile")
        || mediaType.toString().endsWith("x-emf");
  }

  private void createImageThumb(EvidenceFile evidence, File thumbFile) {

    File tmp = null;
    try {
      GalleryValue value = new GalleryValue(null, null, -1);
      BufferedImage img = null;
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
          img = ImageUtil.getSubSampledImage(stream, thumbSize * samplingRatio, thumbSize * samplingRatio, value);
        } finally {
          IOUtil.closeQuietly(stream);
        }
      }
      if (img == null) {
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = new GraphicsMagicConverter().getImage(stream, thumbSize * samplingRatio, true);
          value = null;
        } catch (TimeoutException e) {
          stats.incTimeouts();
          evidence.setExtraAttribute(THUMB_TIMEOUT, "true");
          Log.warning(getClass().getSimpleName(), "Timeout ao gerar miniatura externamente: "
              + evidence.getPath() + "(" + evidence.getLength() + " bytes)");

        } finally {
          IOUtil.closeQuietly(stream);
        }
      }

      tmp = File.createTempFile("iped", ".tmp", new File(output, thumbsFolder));

      if (img != null) {
        if (value != null && (value.originalW > thumbSize || value.originalH > thumbSize) && Math.max(img.getWidth(), img.getHeight()) != thumbSize) {
          img = ImageUtil.resizeImage(img, thumbSize, thumbSize);
        }
        img = ImageUtil.getOpaqueImage(img);

        ImageIO.write(img, "jpg", tmp);
      }

<<<<<<< HEAD
    } catch (Throwable e) {
=======
    } catch (Exception e) {
>>>>>>> 4855b2f... Versão estável do desmembramento por pacote.
      Log.warning(getClass().getSimpleName(), "Erro ao gerar miniatura: "
          + evidence.getPath() + "(" + evidence.getLength() + " bytes)");

    } finally {
      if (tmp != null && !tmp.renameTo(thumbFile)) {
        tmp.delete();
      }

      if (thumbFile.exists() && thumbFile.length() != 0) {
        evidence.setExtraAttribute(HAS_THUMB, true);
      } else {
        evidence.setExtraAttribute(HAS_THUMB, false);
      }
    }
  }

}
