package iped.viewers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import iped.parsers.threema.ThreemaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import iped.data.IItem;
import iped.data.IItemId;
import iped.data.SelectionListener;
import iped.io.IStreamSource;
import iped.parsers.discord.DiscordParser;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.shareaza.ShareazaDownloadParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.util.Util;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.utils.IOUtil;
import iped.viewers.api.AttachmentSearcher;
import iped.viewers.localization.Messages;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;

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

    protected AttachmentSearcher attachSearcher;

    private HashSet<String> mediaHashesInView = new HashSet<>();

    private boolean cheking = false;

    public HtmlLinkViewer(AttachmentSearcher attachSearcher) {
        this.attachSearcher = attachSearcher;
        this.fileHandler = new AttachmentHandler();
        this.enableJavascript = true;

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                    @Override
                    public void changed(ObservableValue<? extends Worker.State> ov, Worker.State oldState,
                            Worker.State newState) {
                        if (newState == Worker.State.SUCCEEDED) {
                            updateSelection();
                            // imprecise, not needed for current chat reports after #633
                            // scrollToPosition();
                        }
                        if (newState == Worker.State.RUNNING) {
                            mediaHashesInView.clear();
                        }
                    }
                });
                // some htmls need this to javascript work early
                webEngine.documentProperty().addListener(new ChangeListener<Document>() {

                    @Override
                    public void changed(ObservableValue<? extends Document> observable, Document oldValue,
                            Document newValue) {
                        addJavascriptListener(webEngine);
                    }

                });
                // but other htmls need this to javascript work early
                webEngine.getLoadWorker().progressProperty().addListener(new ChangeListener<Number>() {

                    @Override
                    public void changed(ObservableValue<? extends Number> observable, Number oldValue,
                            Number newValue) {
                        addJavascriptListener(webEngine);
                        updateSelection();
                        if (newValue.floatValue() > 0) {
                            // imprecise, not needed for current chat reports after #633
                            // scrollToPosition();
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_CHAT.toString().equals(contentType)
                || ThreemaParser.THREEMA_CHAT.toString().equals(contentType)
                || SkypeParser.CONVERSATION_MIME_TYPE.equals(contentType)
                || SkypeParser.FILETRANSFER_MIME_TYPE.equals(contentType)
                || UFED_HTML_REPORT_MIME.equals(contentType) || PREVIEW_WITH_LINKS_MIME.equals(contentType)
                || TelegramParser.TELEGRAM_CHAT.toString().equals(contentType)
                || Win10MailParser.WIN10_MAIL_MSG.toString().equals(contentType)
                || DiscordParser.CHAT_MIME_TYPE_HTML.equals(contentType)
                || ShareazaDownloadParser.SHAREAZA_DOWNLOAD_META.equals(contentType);
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
        if (content != null) {
            attachSearcher.updateSelectionCache();
        }
        super.loadFile(content, terms);
    }

    public class AttachmentHandler extends FileHandler {

        public void open(final String luceneQuery) {

            IItem item = attachSearcher.getItem(luceneQuery);
            if (!IOUtil.isToOpenExternally(item.getName(), item.getType())) {
                return;
            }
            File file = null;
            try {
                file = Util.getFileWithRightExt(item);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
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
            cheking = true;
            attachSearcher.checkItem(luceneQuery, checked);
            cheking = false;
        }

        public boolean isChecked(String hash) {
            return attachSearcher.isChecked(hash);
        }

    }

    private void runScript(String script) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webEngine.executeScript(script);
            }
        });
    }

    private void updateSelection() {
        JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
        window.setMember("updatedHashes", mediaHashesInView); //$NON-NLS-1$
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) {var hash = x[i].name; if(updatedHashes.add(hash)) x[i].checked = app.isChecked(hash);}";
        webEngine.executeScript(script);
    }

    @Override
    public void setSelected(IItemId item, boolean value) {
        if (cheking || mediaHashesInView.isEmpty())
            return;
        String hash = attachSearcher.getHash(item);
        if (!mediaHashesInView.contains(hash))
            return;
        String script = "var x = document.getElementsByName(\"" + hash + "\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = " + value + ";";
        runScript(script);
    }

    @Override
    public void clearAll() {
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
