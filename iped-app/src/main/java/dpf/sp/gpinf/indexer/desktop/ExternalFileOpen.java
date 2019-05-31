package dpf.sp.gpinf.indexer.desktop;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.IPEDException;
import iped3.IItem;

public class ExternalFileOpen {

    private static Logger LOGGER = LoggerFactory.getLogger(ExternalFileOpen.class);

    public static void open(final int luceneId) {
        new Thread() {
            public void run() {
                IItem item = App.get().appCase.getItemByLuceneID(luceneId);
                try {
                    File file = item.getTempFile();
                    file.setReadOnly();
                    LOGGER.info("Externally Opening file " + item.getPath()); //$NON-NLS-1$
                    open(file);

                } catch (IOException e) {
                    e.printStackTrace();

                } catch (IPEDException e) {
                    CopiarArquivos.salvarArquivo(luceneId);
                }
            }
        }.start();
    }

    public static void open(File file) {
        try {
            if (file != null) {
                Desktop.getDesktop().open(file.getCanonicalFile());
            }

        } catch (Exception e) {
            // e.printStackTrace();
            try {
                if (System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                    Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", //$NON-NLS-1$ //$NON-NLS-2$
                            "\"" + file.getCanonicalFile() + "\"" }); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    Runtime.getRuntime().exec(new String[] { "xdg-open", file.toURI().toURL().toString() }); //$NON-NLS-1$
                }

            } catch (IOException e2) {
                e2.printStackTrace();
                throw new IPEDException(e2);
            }
        }
    }

}
