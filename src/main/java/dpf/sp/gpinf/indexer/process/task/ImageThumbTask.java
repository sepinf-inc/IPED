package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil.BooleanWrapper;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

public class ImageThumbTask extends AbstractTask {

  public ImageThumbTask(Worker worker) {
    super(worker);
  }

  public static final String thumbsFolder = "thumbs";

  private static final String enableProperty = "enableImageThumbs";

  public static final String HAS_THUMB = "hasThumb";

  public static final String THUMB_TIMEOUT = "thumbTimeout";

  private static final String TASK_CONFIG_FILE = "ImageThumbsConfig.txt";
  
  private static final int samplingRatio = 3;
  
  public static boolean extractThumb = true;

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

    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
      GraphicsMagicConverter.setWinToolPathPrefix(Configuration.appRoot);
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
    
    value = properties.getProperty("extractThumb");
    if (value != null && !value.trim().isEmpty()) {
    	extractThumb = Boolean.valueOf(value.trim());
    }
    
  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
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
    return mediaType.getType().equals("image");
  }

  private void createImageThumb(EvidenceFile evidence, File thumbFile) {

    File tmp = null;
    try {
      BufferedImage img = null;
      Dimension dimension = null;
      try (BufferedInputStream stream = evidence.getBufferedStream()){
    	  dimension = ImageUtil.getImageFileDimension(stream);
      }
      if (extractThumb && evidence.getMediaType().getSubtype().startsWith("jpeg")) {
        try (BufferedInputStream stream = evidence.getBufferedStream()){
          img = ImageUtil.getThumb(stream);
        }
      }
      if (img == null) {
        try (BufferedInputStream stream = evidence.getBufferedStream()){
          BooleanWrapper renderException = new BooleanWrapper();
          img = ImageUtil.getSubSampledImage(stream, thumbSize * samplingRatio, thumbSize * samplingRatio, renderException);
          if(img != null && renderException.value)
        	  evidence.setExtraAttribute("thumbException", "true");
        }
      }
      if (img == null && !ImageUtil.jdkImagesSupported.contains(evidence.getMediaType().toString())) {
        try (BufferedInputStream stream = evidence.getBufferedStream()){
          img = new GraphicsMagicConverter().getImage(stream, thumbSize * samplingRatio, true);
          if(img != null)
        	  evidence.setExtraAttribute("externalThumb", "true");
          dimension = null;
        } catch (TimeoutException e) {
          stats.incTimeouts();
          evidence.setExtraAttribute(THUMB_TIMEOUT, "true");
          Log.warning(getClass().getSimpleName(), "Timeout ao gerar miniatura externamente: "
              + evidence.getPath() + "(" + evidence.getLength() + " bytes)");
        }
      }

      tmp = File.createTempFile("iped", ".tmp", new File(output, thumbsFolder));

      if (img != null) {
        if (dimension != null && (dimension.width > thumbSize || dimension.height > thumbSize) && Math.max(img.getWidth(), img.getHeight()) != thumbSize) {
          img = ImageUtil.resizeImage(img, thumbSize, thumbSize);
        }
        img = ImageUtil.getOpaqueImage(img);

        ImageIO.write(img, "jpg", tmp);
      }

    } catch (Throwable e) {
      Log.warning(getClass().getSimpleName(), "Erro ao gerar miniatura: "
          + evidence.getPath() + "(" + evidence.getLength() + " bytes) " + e.toString());

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
