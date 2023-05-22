package iped.app.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.viewers.AttachmentSearcherImpl;
import iped.data.IItem;
import iped.exception.IPEDException;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.util.Util;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;

public class ExternalFileOpen {

    private static Logger LOGGER = LoggerFactory.getLogger(ExternalFileOpen.class);

    public static void open(final int luceneId) {
        new Thread() {
            public void run() {
                IItem item = App.get().appCase.getItemByLuceneID(luceneId);
                String itemReferenceQuery = item.getMetadata().get(ExtraProperties.LINKED_ITEMS);
                if (itemReferenceQuery != null
                        && (MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.METADATA_ENTRY)
                            || MediaTypes.isInstanceOf(item.getMediaType(), Win10MailParser.WIN10_MAIL_ATTACH))) {
                    item = new AttachmentSearcherImpl().getItem(itemReferenceQuery);
                    if (item == null)
                        return;
                }
                try {
                    if (IOUtil.isToOpenExternally(item.getName(), item.getType())) {
                        LOGGER.info("Externally Opening file " + item.getPath()); //$NON-NLS-1$
                        File file = Util.getFileRenamedToExt(item.getTempFile(), item.getType());
                        file.setReadOnly();
                        if (IOUtil.isTemporaryFile(file)) {
                            file.deleteOnExit();
                        }
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
