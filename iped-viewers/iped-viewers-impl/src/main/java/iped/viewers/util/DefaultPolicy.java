package iped.viewers.util;

import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLPermission;
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import iped.io.URLUtil;
import iped.viewers.HtmlViewer;

/**
 * Custom application policy to block Internet access from html and other
 * viewers.
 * 
 * @author Nassif
 *
 */
public class DefaultPolicy extends Policy {

    private static final URI viewer = getHtmlViewerURI();

    private List<Permission> allowedPerms = new ArrayList<>();

    private static URI getHtmlViewerURI() {
        try {
            return URLUtil.getURL(HtmlViewer.class).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void addAllowedPermission(Permission perm) {
        allowedPerms.add(perm);
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission perm) {

        try {

            if (domain.getCodeSource() == null || URLUtil.getURL(domain) == null) {
                return true;
            }

            for (Permission p : allowedPerms) {
                if (p.implies(perm)) {
                    return true;
                }
            }

            URI from = URLUtil.getURL(domain).toURI();
            if (from.equals(viewer) && (perm instanceof SocketPermission || perm instanceof URLPermission)) {
                return false;
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();

        }

        return true;
    }

}
