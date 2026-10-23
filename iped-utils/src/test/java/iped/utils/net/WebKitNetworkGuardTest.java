package iped.utils.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Test;

public class WebKitNetworkGuardTest {

    private static final String SOCKET_STREAM = WebKitNetworkGuard.WEBKIT_NETWORK_PACKAGE + "SocketStreamHandle";
    private static final String HTTP2_LOADER = WebKitNetworkGuard.WEBKIT_NETWORK_PACKAGE + "HTTP2Loader";

    private static final List<Proxy> DELEGATED = List.of(Proxy.NO_PROXY);

    private static final ProxySelector DELEGATE = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return DELEGATED;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    };

    @After
    public void tearDown() {
        WebKitNetworkGuard.clearAllowedForTests();
    }

    private static WebKitNetworkGuard guard(String caller) {
        return new WebKitNetworkGuard(DELEGATE, () -> caller);
    }

    private static boolean blocked(String caller, String url) {
        try {
            guard(caller).select(URI.create(url));
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Test
    public void testNonWebKitRequestsAreDelegated() {
        // MinIO, remote transcription, Elastic... are not affected
        assertSame(DELEGATED, guard(null).select(URI.create("http://10.0.0.1:9000/bucket")));
    }

    @Test
    public void testWebKitRequestsBlockedByDefault() {
        assertTrue(blocked(WebKitNetworkGuard.URL_LOADER, "http://example.com/tracker.png"));
        assertTrue(blocked(WebKitNetworkGuard.URL_LOADER + "$1", "https://example.com/"));
    }

    @Test
    public void testWebSocketsAndHttp2LoaderAlwaysBlocked() {
        WebKitNetworkGuard.allow(Pattern.compile("https://example\\.com/"));
        assertFalse(blocked(WebKitNetworkGuard.URL_LOADER, "https://example.com/a.js"));
        assertTrue(blocked(SOCKET_STREAM, "https://example.com/a.js"));
        assertTrue(blocked(HTTP2_LOADER, "https://example.com/a.js"));
    }

    @Test
    public void testTileTemplates() {
        WebKitNetworkGuard.allowUrlTemplate("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png");
        WebKitNetworkGuard.allowUrlTemplate("http://r{s}.ortho.tiles.virtualearth.net/tiles/r{quad}.png?g=1");
        WebKitNetworkGuard.allowUrlTemplate("https://tile.openstreetmap.org/${z}/${x}/${y}.png");
        String loader = WebKitNetworkGuard.URL_LOADER;

        assertFalse(blocked(loader, "https://a.tile.openstreetmap.org/5/10/12.png"));
        assertFalse(blocked(loader, "http://r3.ortho.tiles.virtualearth.net/tiles/r0213.png?g=1"));
        assertFalse(blocked(loader, "https://tile.openstreetmap.org/5/10/12.png"));

        // placeholders can't change the host
        assertTrue(blocked(loader, "https://evil.net/x.tile.openstreetmap.org/5/10/12.png"));
        assertTrue(blocked(loader, "https://evil.net@a.tile.openstreetmap.org.evil.net/5/10/12.png"));
        assertTrue(blocked(loader, "https://a.tile.openstreetmap.org:8080/5/10/12.png"));
        assertTrue(blocked(loader, "https://tile.openstreetmap.org.evil.net/5/10/12.png"));
        assertTrue(blocked(loader, "https://evil.net/?https://tile.openstreetmap.org/5/10/12.png"));
    }

    @Test
    public void testAllowIsIdempotent() {
        for (int i = 0; i < 3; i++) {
            WebKitNetworkGuard.allowUrlTemplate("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png");
        }
        assertEquals(1, WebKitNetworkGuard.allowedCountForTests());
    }

    @Test
    public void testTemplateToPattern() {
        assertNull(WebKitNetworkGuard.templateToPattern(null));
        assertNull(WebKitNetworkGuard.templateToPattern(" "));
        Pattern p = WebKitNetworkGuard.templateToPattern("https://{s}.example.com/a.b?x=1");
        // regex metacharacters in the template are literal
        assertTrue(p.matcher("https://a.example.com/a.b?x=1").matches());
        assertFalse(p.matcher("https://a.exampleXcom/a.b?x=1").matches());
    }

    /**
     * End to end with the real HttpURLConnection, as used by WebKit URLLoader:
     * the request must fail before any connection is made.
     */
    @Test
    public void testHttpURLConnectionNeverConnects() throws Exception {
        ProxySelector previous = ProxySelector.getDefault();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(500);
            ProxySelector.setDefault(guard(WebKitNetworkGuard.URL_LOADER));
            URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/tracker.png");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            try {
                conn.getResponseCode();
                fail("connection should have been blocked");
            } catch (IOException e) {
                // newer Java versions wrap the selector exception
                assertTrue(e.getCause() instanceof IllegalArgumentException);
            } catch (IllegalArgumentException e) {
                // older Java propagates it; WebKit URLLoader reports it as a load error too
            } catch (RuntimeException e) {
                assertTrue(e.getCause() instanceof IllegalArgumentException);
            }
            try {
                server.accept().close();
                fail("the server received a connection");
            } catch (SocketTimeoutException e) {
                // expected: nothing connected
            }
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    public void testInstall() {
        ProxySelector previous = ProxySelector.getDefault();
        try {
            WebKitNetworkGuard.install();
            WebKitNetworkGuard.install();
            assertTrue(ProxySelector.getDefault() instanceof WebKitNetworkGuard);
            assertEquals("false", System.getProperty(WebKitNetworkGuard.USE_HTTP2_LOADER_PROP));
            // outside WebKit, requests go to the previous selector
            ProxySelector.getDefault().select(URI.create("http://example.com/"));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }
}
