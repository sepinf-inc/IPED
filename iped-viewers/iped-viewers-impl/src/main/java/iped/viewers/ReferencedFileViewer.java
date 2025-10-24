package iped.viewers;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.apache.tika.Tika;

import iped.data.IItem;
import iped.io.IStreamSource;
import iped.parsers.ares.AresParser;
import iped.parsers.discord.DiscordParser;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.emule.PartMetParser;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.threema.ThreemaParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.FileContentSource;
import iped.viewers.api.AbstractViewer;
import iped.viewers.api.AttachmentSearcher;
import iped.viewers.localization.Messages;

public class ReferencedFileViewer extends AbstractViewer {

    private static final String REFERENCE_NOT_FOUND = Messages.getString("ReferenceViewer.FileNotFound");
    private static final String REFERENCE_NOT_SUPPORTED = Messages.getString("ReferenceViewer.NotSupported");

    private JLabel typeNotSupported;

    private MultiViewer multiViewer;
    private AttachmentSearcher attachSearcher;
    private IItem lastItem;
    private Tika tika;
    private Runnable actionIfNotLoaded;

    public ReferencedFileViewer(MultiViewer multiViewer, AttachmentSearcher attachSearcher, Runnable actionIfNotLoaded) {
        super();
        this.multiViewer = multiViewer;
        this.attachSearcher = attachSearcher;
        this.typeNotSupported = new JLabel();
        this.actionIfNotLoaded = actionIfNotLoaded;
        this.getPanel().add(typeNotSupported);
    }

    @Override
    public String getName() {
        return "ReferenceFileViewer";
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_ATTACHMENT.toString().equals(contentType)
                || TelegramParser.TELEGRAM_ATTACHMENT.toString().equals(contentType)
                || SkypeParser.ATTACHMENT_MIME_TYPE.equals(contentType)
                || DiscordParser.ATTACH_MIME_TYPE.equals(contentType)
                || KnownMetParser.KNOWN_MET_ENTRY_MIME_TYPE.equals(contentType)
                || PartMetParser.PART_MET_ENTRY_MIME_TYPE.equals(contentType)
                || AresParser.ARES_ENTRY_MIME_TYPE.equals(contentType)
                || ShareazaLibraryDatParser.LIBRARY_DAT_ENTRY_MIME_TYPE.equals(contentType)
                || MediaTypes.UFED_ATTACHMENT_MIME.toString().equals(contentType)
                || Win10MailParser.WIN10_MAIL_ATTACH.toString().equals(contentType)
                || ThreemaParser.THREEMA_ATTACHMENT.toString().equals(contentType);
    }

    @Override
    public void init() {
        // TODO Auto-generated method stub

    }

    @Override
    public void dispose() {
        if (lastItem != null)
            lastItem.dispose();
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

        if (content == null) {
            if (lastItem != null) {
                lastItem.dispose();
            }
            return;
        }

        typeNotSupported.setVisible(false);

        if (content instanceof IItem) {
            IItem item = (IItem) content;
            String query = item.getMetadata().get(ExtraProperties.LINKED_ITEMS);
            query = appendUfedFileId(item, query);
            if (query == null) {
                SwingUtilities.invokeLater(actionIfNotLoaded);
                return;
            }
            lastItem = attachSearcher.getItem(query);
            if (lastItem == null) {
                typeNotSupported.setText(REFERENCE_NOT_FOUND + query);
                typeNotSupported.setVisible(true);
                SwingUtilities.invokeLater(actionIfNotLoaded);
            } else if (lastItem.getViewFile() != null) {
                FileContentSource viewContent = new FileContentSource(lastItem.getViewFile());
                String mediaType = detectType(lastItem.getViewFile());
                load(viewContent, mediaType, highlightTerms);
            } else
                load(lastItem, lastItem.getMediaType().toString(), highlightTerms);
        }

    }

    private String appendUfedFileId(IItem item, String query) {
        String ufedFileId = item.getMetadata().get(ExtraProperties.UFED_FILE_ID);
        if (ufedFileId != null) {
            String ufedQuery = attachSearcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + ufedFileId + "\"";
            if (query != null) {
                return "(" + query + ") " + ufedQuery;
            } else {
                return ufedQuery;
            }
        }
        return query;
    }

    private void load(IStreamSource content, String mediaType, Set<String> highlightTerms) {
        if (multiViewer.isSupportedType(mediaType)) {
            multiViewer.loadFile(content, mediaType, highlightTerms);
        } else {
            typeNotSupported.setText(REFERENCE_NOT_SUPPORTED + mediaType);
            typeNotSupported.setVisible(true);
            SwingUtilities.invokeLater(actionIfNotLoaded);
        }
    }

    private String detectType(File file) {
        if (tika == null)
            tika = new Tika();
        try {
            return tika.detect(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        // TODO Auto-generated method stub

    }

}
