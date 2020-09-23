package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.util.Set;

import javax.swing.JLabel;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;

public class NoJavaFXViewer extends Viewer {

    final static String NO_JAVAFX_MSG = "<html>" + Messages.getString("NoJavaFXViewer.Warn") + "</html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    public NoJavaFXViewer() {
        this.getPanel().add(new JLabel(NO_JAVAFX_MSG));
    }

    @Override
    public String getName() {
        return "NoJavaFX"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("text/html") || contentType.equals("application/xhtml+xml") //$NON-NLS-1$ //$NON-NLS-2$
                || contentType.equals("text/asp") || contentType.equals("text/aspdotnet") //$NON-NLS-1$ //$NON-NLS-2$
                || contentType.equals("message/rfc822") || contentType.equals("message/x-emlx") //$NON-NLS-1$ //$NON-NLS-2$
                || contentType.equals("message/outlook-pst") || contentType.equals("application/messenger-plus"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void init() {

    }

    @Override
    public void dispose() {
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        // TODO Auto-generated method stub
    }

    @Override
    public int getHitsSupported() {
        return -1;
    }
}
