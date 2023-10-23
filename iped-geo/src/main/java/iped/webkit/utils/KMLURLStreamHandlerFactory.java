package iped.webkit.utils;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class KMLURLStreamHandlerFactory implements URLStreamHandlerFactory {
    private static String PREFIX = "sun.net.www.protocol";

    public URLStreamHandler createDefaultURLStreamHandler(String protocol) {
        String name = PREFIX + "." + protocol + ".Handler";
        try {
            @SuppressWarnings("deprecation")
            Object o = Class.forName(name).newInstance();
            return (URLStreamHandler) o;
        } catch (ClassNotFoundException x) {
            // ignore
        } catch (Exception e) {
            // For compatibility, all Exceptions are ignored.
            // any number of exceptions can get thrown here
        }
        return null;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("ipedgeo".equals(protocol)) {
            return new KMLURLStreamHandler();
        }

        return createDefaultURLStreamHandler(protocol);
    }

}
