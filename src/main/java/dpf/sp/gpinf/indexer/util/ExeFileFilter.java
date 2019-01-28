package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.FileFilter;

public class ExeFileFilter implements FileFilter{

    @Override
    public boolean accept(File pathname) {
        return pathname.getName().toLowerCase().endsWith(".exe"); //$NON-NLS-1$
    }

}
