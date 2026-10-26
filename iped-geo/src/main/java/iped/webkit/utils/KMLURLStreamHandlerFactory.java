package iped.webkit.utils;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class KMLURLStreamHandlerFactory implements URLStreamHandlerFactory {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("ipedgeo".equals(protocol)) {
            return new KMLURLStreamHandler();
        }

        // null makes URL use the JDK built-in handler for other protocols
        return null;
    }

}
