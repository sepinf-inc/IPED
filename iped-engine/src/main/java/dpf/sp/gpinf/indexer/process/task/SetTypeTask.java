package dpf.sp.gpinf.indexer.process.task;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.parsers.util.Util;
import iped3.IItem;
import iped3.configuration.Configurable;
import iped3.util.ExtraProperties;

/**
 * Seta o tipo (extensão correta) dos itens com base no seu mediaType
 * reconhecido.
 *
 */
public class SetTypeTask extends AbstractTask {

    public static final String EXT_MISMATCH = "extMismatch"; //$NON-NLS-1$

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
            evidence.setType(ext);
            Boolean isDecodedData = (Boolean) evidence.getExtraAttribute(ExtraProperties.DECODED_DATA);
            if (isDecodedData == null || !isDecodedData) {
                evidence.setExtraAttribute(EXT_MISMATCH, !ext.equals(evidence.getExt()));
            }
        }

    }

    private String getExtBySig(IItem evidence) throws TikaException, IOException {

        String origExt = evidence.getExt();
        if (!origExt.isEmpty()) {
            origExt = "." + origExt;
        }
        MediaType mediaType = evidence.getMediaType();
        String ext = Util.getTrueExtension(origExt, mediaType);
        return ext;

    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

}
