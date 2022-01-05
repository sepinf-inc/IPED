package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.filetypes.GenericFileType;
import iped3.IItem;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.parsers.util.Util;

/**
 * Seta o tipo (extensão correta) dos itens com base no seu mediaType
 * reconhecido.
 *
 */
public class SetTypeTask extends AbstractTask {

    public SetTypeTask() {
    }

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
    public void init(Properties confProps, File confDir) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

}
