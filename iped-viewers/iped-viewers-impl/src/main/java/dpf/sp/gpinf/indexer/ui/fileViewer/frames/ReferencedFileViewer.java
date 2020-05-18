package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.tika.Tika;

import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AttachmentSearcher;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import iped3.IItem;
import iped3.io.IStreamSource;
import iped3.util.ExtraProperties;

public class ReferencedFileViewer extends Viewer{
    
    private ViewersRepository multiViewer;
    private AttachmentSearcher attachSearcher;
    private IItem lastItem;
    private Tika tika;
    
    public ReferencedFileViewer(ViewersRepository multiViewer, AttachmentSearcher attachSearcher) {
        super();
        this.multiViewer = multiViewer;
        this.attachSearcher = attachSearcher;
    }

    @Override
    public String getName() {
        return "ReferenceFileViewer";
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_ATTACHMENT.toString().equals(contentType);
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
            return;
        }
        
        if(content instanceof IItem) {
            IItem item = (IItem) content;
            String query = item.getMetadata().get(ExtraProperties.REFERENCED_FILE_QUERY);
            lastItem = attachSearcher.getItem(query);
            if(lastItem.getViewFile() != null) {
                FileContentSource viewContent = new FileContentSource(lastItem.getViewFile());
                multiViewer.loadFile(viewContent, detectType(lastItem.getViewFile()), highlightTerms);
            }else
                multiViewer.loadFile(lastItem, lastItem.getMediaType().toString(), highlightTerms);
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
