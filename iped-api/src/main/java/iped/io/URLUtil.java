package iped.io;

import java.net.URL;
import java.security.ProtectionDomain;

public class URLUtil {
    /**
     * Return a URL from a Class. This method was created to handle Windows unmapped
     * network paths, like \\server\case. It adds another "//" before the actual
     * path name, to avoid an IllegalArgumentException: URI has an authority
     * component. See issue #1336.
     */
    public static URL getURL(Class<?> clazz) {
        return getURL(clazz.getProtectionDomain());
    }

    /**
     * Return a URL from a ProtectionDomain.
     */
    public static URL getURL(ProtectionDomain domain) {
        URL url = domain.getCodeSource().getLocation();
        if (url != null && "file".equalsIgnoreCase(url.getProtocol()) && url.getPath() != null
                && url.getPath().startsWith("//")) {
            try {
                URL newUrl = new URL("file://" + url.getPath());
                if (newUrl != null) {
                    url = newUrl;
                }
            } catch (Exception e) {
            }
        }
        return url;
    }
}
