package iped.engine.util;

import javax.net.ssl.SSLContext;

import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

/**
 * To be used just for testing purposes not in production!
 */
public class SSLFix {
    public static SSLContext getUnsecureSSLContext() {
        try {
            SSLContextBuilder sslBuilder = SSLContexts.custom().loadTrustMaterial(null, (x509Certificates, s) -> true);
            return sslBuilder.build();
        } catch (Exception e) {
            return null;
        }
    }

}