package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.CustomDetector;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;
import gpinf.dev.data.EvidenceFile;

/**
 * Análise de assinatura utilizando biblioteca Apache Tika.
 */
public class SignatureTask extends AbstractTask {

  private static Logger LOGGER = LoggerFactory.getLogger(SignatureTask.class);

  public static boolean processFileSignatures = true;
  
  CustomDetector detector;

  public SignatureTask(Worker worker) {
    super(worker);
    detector = new CustomDetector();
  }
  
  @Override
  public boolean isEnabled() {
    return processFileSignatures;
  }

  public void process(EvidenceFile evidence) {

    if (evidence.isDir()) {
      evidence.setMediaType(MediaType.OCTET_STREAM);
    }

    MediaType type = evidence.getMediaType();
    if (type == null) {
      Metadata metadata = new Metadata();
      metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
      try {
        if (processFileSignatures) {
          TikaInputStream tis = null;
          try {
            tis = evidence.getTikaStream();
            type = detector.detect(tis, metadata).getBaseType();

          } catch (IOException e) {
            LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                evidence.getLength(), e.toString());
          } finally {
            //Fecha handle p/ renomear subitem p/ hash posteriormente. Demais itens são fechados via evidence.dispose()
            if (evidence.isSubItem()) {
              IOUtil.closeQuietly(tis);
            }
          }
        }

        //Caso seja item office07 cifrado e tenha extensão específica, refina o tipo
        if (type != null && type.toString().equals("application/x-tika-ooxml-protected") //$NON-NLS-1$
            && "docx xlsx pptx".contains(evidence.getExt().toLowerCase())) { //$NON-NLS-1$
          type = MediaType.application("x-tika-ooxml-protected-" + evidence.getExt().toLowerCase()); //$NON-NLS-1$
        }

        if (type == null) {
          type = detector.detect(null, metadata).getBaseType();
        }

      } catch (Exception | OutOfMemoryError e) {
        type = MediaType.OCTET_STREAM;

        LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
            evidence.getPath(), evidence.getLength(), e.toString());
      }
    }
    evidence.setMediaType(detector.getMediaTypeRegistry().normalize(type));
  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {
    String value = confProps.getProperty("processFileSignatures"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      processFileSignatures = Boolean.valueOf(value);
    }
  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

}
