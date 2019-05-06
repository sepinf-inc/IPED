package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.filetypes.GenericFileType;
import iped3.Item;

import java.io.File;
import java.util.Properties;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;

import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Seta o tipo (extensão correta) dos itens com base no seu mediaType reconhecido.
 *
 */
public class SetTypeTask extends AbstractTask {

  TikaConfig tikaConfig;

  public SetTypeTask() {
    tikaConfig = TikaConfig.getDefaultConfig();
  }

  @Override
  public void process(Item evidence) throws Exception {

    if (evidence.getType() == null) {
      String ext = getExtBySig(evidence);
      if (!ext.isEmpty()) {
        if (ext.length() > 1 && evidence.isCarved() && !evidence.isSubItem() && evidence.getExt().isEmpty()) {
          evidence.setName(evidence.getName() + ext);
          evidence.setPath(evidence.getPath() + ext);
        }
        ext = ext.substring(1);
      }
      evidence.setType(new GenericFileType(ext));
    }

  }

  public String getExtBySig(Item evidence) {

    String ext = ""; //$NON-NLS-1$
    String ext1 = "." + evidence.getExt(); //$NON-NLS-1$
    MediaType mediaType = evidence.getMediaType();
    if (!mediaType.equals(MediaType.OCTET_STREAM)) {
      try {
        do {
          boolean first = true;
          for (String ext2 : tikaConfig.getMimeRepository().forName(mediaType.toString()).getExtensions()) {
            if (first) {
              ext = ext2;
              first = false;
            }
            if (ext2.equals(ext1)) {
              ext = ext1;
              break;
            }
          }

        } while (ext.isEmpty() && !MediaType.OCTET_STREAM.equals((mediaType = tikaConfig.getMediaTypeRegistry().getSupertype(mediaType))));
      } catch (MimeTypeException e) {
      }
    }

    if (ext.isEmpty() || ext.equals(".txt")) { //$NON-NLS-1$
      ext = ext1;
    }

    return ext.toLowerCase();

  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

}
