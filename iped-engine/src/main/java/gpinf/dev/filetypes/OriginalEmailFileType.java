package gpinf.dev.filetypes;

import java.io.File;
import java.util.List;

import dpf.sp.gpinf.indexer.localization.Messages;
import iped3.IItem;

public class OriginalEmailFileType extends EvidenceFileType {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Override
    public String getLongDescr() {
        return Messages.getString("OriginalEmailFileType.ParentEmail"); //$NON-NLS-1$
    }

    @Override
    public void processFiles(File baseDir, List<IItem> items) {
        // TODO Auto-generated method stub

    }

}
