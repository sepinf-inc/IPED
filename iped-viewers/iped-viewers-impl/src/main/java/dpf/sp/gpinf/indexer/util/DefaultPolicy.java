package dpf.sp.gpinf.indexer.util;

import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLPermission;
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;

import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlViewer;

/**
 * Custom application policy to block Internet access from html and other
 * viewers.
 * 
 * @author Nassif
 *
 */
public class DefaultPolicy extends Policy {

    private static final URI viewer = getHtmlViewerURI();

    private static URI getHtmlViewerURI() {
        try {
            return HtmlViewer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission perm) {

        try {

            if (domain.getCodeSource() == null || domain.getCodeSource().getLocation() == null) {
                return true;
            }
            URI from = domain.getCodeSource().getLocation().toURI();

            if (from.equals(viewer) && (perm instanceof SocketPermission || perm instanceof URLPermission)) {
                return false;
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();

        }

        return true;
    }

}
