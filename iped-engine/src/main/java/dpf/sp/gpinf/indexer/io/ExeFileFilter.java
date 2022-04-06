package dpf.sp.gpinf.indexer.io;

import java.io.File;
import java.io.FileFilter;

public class ExeFileFilter implements FileFilter {

    @Override
    public boolean accept(File pathname) {
        return pathname.getName().toLowerCase().endsWith(".exe"); //$NON-NLS-1$
    }

}
