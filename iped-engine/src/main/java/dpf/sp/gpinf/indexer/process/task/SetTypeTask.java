package dpf.sp.gpinf.indexer.process.task;

import java.util.Collections;
import java.util.List;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import gpinf.dev.filetypes.GenericFileType;
import iped3.IItem;
import macee.core.Configurable;

/**
 * Seta o tipo (extensão correta) dos itens com base no seu mediaType
 * reconhecido.
 *
 */
public class SetTypeTask extends AbstractTask {

    private TikaConfig tikaConfig;

    @Override
    public void process(IItem evidence) throws Exception {

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

    public String getExtBySig(IItem evidence) {

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

                } while (ext.isEmpty() && !MediaType.OCTET_STREAM
                        .equals((mediaType = tikaConfig.getMediaTypeRegistry().getSupertype(mediaType))));
            } catch (MimeTypeException e) {
            }
        }

        if (ext.isEmpty() || ext.equals(".txt")) { //$NON-NLS-1$
            ext = ext1;
        }

        return ext.toLowerCase();

    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        tikaConfig = TikaConfig.getDefaultConfig();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

}
