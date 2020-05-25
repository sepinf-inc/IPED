package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.swing.JLabel;

import org.apache.tika.Tika;

import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import iped3.IItem;
import iped3.io.IStreamSource;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;

public class ReferencedFileViewer extends Viewer{
    
    private String labelPrefix;
    
    private JLabel typeNotSupported;
    
    private ViewersRepository multiViewer;
    private AttachmentSearcher attachSearcher;
    private IItem lastItem;
    private Tika tika;
    
    public ReferencedFileViewer(ViewersRepository multiViewer, AttachmentSearcher attachSearcher) {
        super();
        this.multiViewer = multiViewer;
        this.attachSearcher = attachSearcher;
        this.labelPrefix = Messages.getString("ReferenceViewer.NotSupported");
        this.typeNotSupported = new JLabel();
        this.getPanel().add(typeNotSupported);
    }

    @Override
    public String getName() {
        return "ReferenceFileViewer";
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_ATTACHMENT.toString().equals(contentType) ||
                SkypeParser.ATTACHMENT_MIME_TYPE.equals(contentType) ||
                MediaTypes.UFED_MESSAGE_ATTACH_MIME.toString().equals(contentType);
    }

    @Override
    public void init() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void dispose() {
        if(lastItem != null) lastItem.dispose();
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        
        if(content == null) {
            if(lastItem != null) lastItem.dispose();
            typeNotSupported.setVisible(false);
            return;
        }
        
        if(content instanceof IItem) {
            IItem item = (IItem) content;
            String query = item.getMetadata().get(ExtraProperties.REFERENCED_FILE_QUERY);
            lastItem = attachSearcher.getItem(query);
            if(lastItem == null) {
                typeNotSupported.setVisible(false);
            }else if(lastItem.getViewFile() != null) {
                FileContentSource viewContent = new FileContentSource(lastItem.getViewFile());
                String mediaType = detectType(lastItem.getViewFile());
                load(viewContent, mediaType, highlightTerms);
            }else
                load(lastItem, lastItem.getMediaType().toString(), highlightTerms);
        }
        
    }
    
    private void load(IStreamSource content, String mediaType, Set<String> highlightTerms) {
        if(multiViewer.isSupportedType(mediaType)) {
            multiViewer.loadFile(content, mediaType, highlightTerms);
        }else {
            typeNotSupported.setText(labelPrefix + mediaType);
            typeNotSupported.setVisible(true);
        }
    }
    
    private String detectType(File file) {
        if(tika == null)
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
