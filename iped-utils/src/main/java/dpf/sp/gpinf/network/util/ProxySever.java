package dpf.sp.gpinf.network.util;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxy local que fecha conexões para Internet, bloqueando download de
 * resources externos de itens Html ao serem renderizados.
 * 
 * @author Nassif
 *
 */
public class ProxySever {

    private static Logger LOGGER = LoggerFactory.getLogger(ProxySever.class);

    private static ProxySever instance;

    private static volatile ServerSocket serverSocket;

    private static volatile String port;

    InetSocketAddress systemProxy;

    private ProxySever() {
    }

    public static ProxySever get() {
        if (instance == null) {
            instance = new ProxySever();
            instance.start();
        }
        return instance;
    }

    private void start() {

        if (serverSocket != null)
            return;

        detectSystemProxy();

        try {
            serverSocket = new ServerSocket(0);
            port = Integer.toString(serverSocket.getLocalPort());

        } catch (Exception e1) {
            e1.printStackTrace();
            // System.setSecurityManager(new AppSecurityManager());
        }

        if (serverSocket != null) {
            new Thread() {
                public void run() {
                    while (true) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            clientSocket.getOutputStream().close();
                            clientSocket.close();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        }

    }

    public void enable() {
        setProxy("127.0.0.1", port); //$NON-NLS-1$
    }

    public void disable() {
        if (systemProxy != null)
            setProxy(systemProxy.getHostName(), Integer.toString(systemProxy.getPort()));
        else
            setProxy("", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void setProxy(String host, String port) {
        System.setProperty("http.proxyHost", host); //$NON-NLS-1$
        System.setProperty("https.proxyHost", host); //$NON-NLS-1$
        System.setProperty("ftp.proxyHost", host); //$NON-NLS-1$
        System.setProperty("http.proxyPort", port); //$NON-NLS-1$
        System.setProperty("https.proxyPort", port); //$NON-NLS-1$
        System.setProperty("ftp.proxyPort", port); //$NON-NLS-1$
        System.setProperty("socksProxyHost", host); //$NON-NLS-1$
        System.setProperty("socksProxyPort", port); //$NON-NLS-1$
    }

    private void detectSystemProxy() {

        List<Proxy> l = null;
        try {
            System.setProperty("java.net.useSystemProxies", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            l = ProxySelector.getDefault().select(new URI("http://www.google.com")); //$NON-NLS-1$

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        if (l != null && l.size() > 0) {
            Proxy proxy = l.get(0);

            systemProxy = (InetSocketAddress) proxy.address();
            if (systemProxy == null) {
                LOGGER.info("No Proxy"); //$NON-NLS-1$
            } else {
                LOGGER.info("system proxy type: " + proxy.type()); //$NON-NLS-1$
                LOGGER.info("system proxy hostname: " + systemProxy.getHostName()); //$NON-NLS-1$
                LOGGER.info("system proxy port: " + systemProxy.getPort()); //$NON-NLS-1$
            }
        }
        System.setProperty("java.net.useSystemProxies", "false"); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
