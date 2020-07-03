package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;

/**
 * Visualizador Html específico que abre links apontando para arquivos do caso,
 * como anexos transferidos em chats ou itens transferidos via P2P.
 * 
 * @author Nassif
 *
 */
public class HtmlLinkViewer extends HtmlViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(HtmlLinkViewer.class);

    public static final String PREVIEW_WITH_LINKS_MIME = "application/x-preview-with-links"; //$NON-NLS-1$

    public static final String PREVIEW_WITH_LINKS_HEADER = "<!--Preview With Links-->"; //$NON-NLS-1$

    public static final String UFED_HTML_REPORT_MIME = "application/x-ufed-html-report"; //$NON-NLS-1$

    private AttachmentSearcher attachSearcher;

    public HtmlLinkViewer(AttachmentSearcher attachSearcher) {
        this.attachSearcher = attachSearcher;
        this.fileOpenApp = new AttachmentOpen();
        this.enableJavascript = true;
        this.enableProxy = false;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_CHAT.toString().equals(contentType)
                || SkypeParser.CONVERSATION_MIME_TYPE.toString().equals(contentType)
                || SkypeParser.FILETRANSFER_MIME_TYPE.toString().equals(contentType)
                || UFED_HTML_REPORT_MIME.equals(contentType) || PREVIEW_WITH_LINKS_MIME.equals(contentType);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected int getMaxHtmlSize() {
        return Integer.MAX_VALUE;
    }

    public class AttachmentOpen extends FileOpen {

        public void open(final String luceneQuery) {

            File file = attachSearcher.getTmpFile(luceneQuery);
            if (file == null) {
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            LOGGER.info("Attachment not found by query " + luceneQuery); //$NON-NLS-1$
                            JOptionPane.showMessageDialog(null, Messages.getString("HtmlLinkViewer.AttachNotFound")); //$NON-NLS-1$
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                this.openFile(file);
            }
        }

        public void check(String luceneQuery, boolean checked) {
            attachSearcher.checkItem(luceneQuery, checked);
        }

    }

}
