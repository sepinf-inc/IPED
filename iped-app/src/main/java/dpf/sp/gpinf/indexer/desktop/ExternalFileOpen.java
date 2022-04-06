package dpf.sp.gpinf.indexer.desktop;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.ui.fileViewer.frames.AttachmentSearcherImpl;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.exception.IPEDException;
import iped3.IItem;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;

public class ExternalFileOpen {

    private static Logger LOGGER = LoggerFactory.getLogger(ExternalFileOpen.class);

    public static void open(final int luceneId) {
        new Thread() {
            public void run() {
                IItem item = App.get().appCase.getItemByLuceneID(luceneId);
                String itemReferenceQuery = item.getMetadata().get(ExtraProperties.LINKED_ITEMS);
                if (itemReferenceQuery != null
                        && MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.CHAT_MESSAGE_MIME)) {
                    item = new AttachmentSearcherImpl().getItem(itemReferenceQuery);
                    if (item == null)
                        return;
                }
                try {
                    if (IOUtil.isToOpenExternally(item.getName(), item.getType())) {
                        LOGGER.info("Externally Opening file " + item.getPath()); //$NON-NLS-1$
                        File file = item.getTempFile();
                        file.setReadOnly();
                        open(file);
                    }

                } catch (IOException e) {
                    e.printStackTrace();

                } catch (IPEDException e) {
                    CopyFiles.saveFile(luceneId);
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
