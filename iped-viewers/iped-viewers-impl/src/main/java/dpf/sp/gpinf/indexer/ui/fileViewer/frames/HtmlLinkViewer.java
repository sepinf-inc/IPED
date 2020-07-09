package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;
import iped3.IItemId;
import iped3.io.IStreamSource;
import iped3.search.SelectionListener;
import javafx.application.Platform;

/**
 * Visualizador Html específico que abre links apontando para arquivos do caso,
 * como anexos transferidos em chats ou itens transferidos via P2P.
 * 
 * @author Nassif
 *
 */
public class HtmlLinkViewer extends HtmlViewer implements SelectionListener {

    private static Logger LOGGER = LoggerFactory.getLogger(HtmlLinkViewer.class);

    public static final String PREVIEW_WITH_LINKS_MIME = "application/x-preview-with-links"; //$NON-NLS-1$

    public static final String PREVIEW_WITH_LINKS_HEADER = "<!--Preview With Links-->"; //$NON-NLS-1$

    public static final String UFED_HTML_REPORT_MIME = "application/x-ufed-html-report"; //$NON-NLS-1$

    private AttachmentSearcher attachSearcher;

    private HashSet<String> hashes = new HashSet<>();

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

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        hashes.clear();
        if (content != null) {
            attachSearcher.createSelectionCache();
        }
        super.loadFile(content, terms);
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

        public boolean isChecked(String hash) {
            hashes.add(hash);
            return attachSearcher.isChecked(hash);
        }

    }

    @SuppressWarnings("restriction")
    private void runScript(String script) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webEngine.executeScript(script);
            }
        });
    }

    @SuppressWarnings("restriction")
    @Override
    protected void checkSelected() {
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) {var hash = x[i].name; x[i].checked = app.isChecked(hash);}";
        runScript(script);
    }

    @Override
    public void setSelected(IItemId item, boolean value) {
        if (hashes.isEmpty())
            return;
        String hash = attachSearcher.getHash(item);
        if (!hashes.contains(hash))
            return;
        String script = "var x = document.getElementsByName(\"" + hash + "\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = " + value + ";";
        runScript(script);
    }

    @Override
    public void clearSelected() {
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = false;";
        runScript(script);
    }

    @Override
    public void selectAll() {
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = true;";
        runScript(script);
    }

}
