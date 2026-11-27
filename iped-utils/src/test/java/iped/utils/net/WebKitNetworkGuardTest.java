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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
    public void testWebSocketsAlwaysBlocked() {
        WebKitNetworkGuard.allow(Pattern.compile("https://example\\.com/"));
        assertFalse(blocked(WebKitNetworkGuard.URL_LOADER, "https://example.com/a.js"));
        assertTrue(blocked(SOCKET_STREAM, "https://example.com/a.js"));
    }

    @Test
    public void testHttp2LoaderGetsUnreachableProxyForBlockedUrls() {
        WebKitNetworkGuard.allow(Pattern.compile("https://example\\.com/"));
        // an exception is not handled by this loader, so it must not be thrown
        assertSame(WebKitNetworkGuard.BLOCKED_PROXY, guard(HTTP2_LOADER).select(URI.create("https://evil.net/a.js")));
        assertSame(WebKitNetworkGuard.BLOCKED_PROXY, guard(HTTP2_LOADER + "$1").select(URI.create("http://example.com/a.js")));
        assertSame(DELEGATED, guard(HTTP2_LOADER).select(URI.create("https://example.com/a.js")));

        assertEquals(1, WebKitNetworkGuard.BLOCKED_PROXY.size());
        Proxy proxy = WebKitNetworkGuard.BLOCKED_PROXY.get(0);
        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertTrue(address.getAddress().isLoopbackAddress());
        assertEquals(0, address.getPort());
    }

    /**
     * End to end with the real HttpClient, as used by WebKit HTTP2Loader: the
     * request must fail without any connection to the server.
     */
    @Test
    public void testHttpClientNeverConnects() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(500);
            HttpClient client = HttpClient.newBuilder().proxy(guard(HTTP2_LOADER)).build();
            for (String scheme : new String[] { "http", "https" }) {
                URI uri = URI.create(scheme + "://127.0.0.1:" + server.getLocalPort() + "/tracker.png");
                try {
                    client.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.discarding())
                            .get(30, TimeUnit.SECONDS);
                    fail("request should have failed");
                } catch (ExecutionException e) {
                    assertTrue(e.getCause() instanceof IOException);
                }
            }
            try {
                server.accept().close();
                fail("the server received a connection");
            } catch (SocketTimeoutException e) {
                // expected: nothing connected
            }
        }
    }

    @Test
    public void testSocketOfAllowedUrl() {
        // HttpURLConnection selects again with socket://host:port for a direct connection
        WebKitNetworkGuard.allow(Pattern.compile("https://unpkg\\.com/leaflet@[^/]+/dist/"));
        String loader = WebKitNetworkGuard.URL_LOADER;

        assertTrue(blocked(loader, "socket://unpkg.com:443"));

        assertFalse(blocked(loader, "https://unpkg.com/leaflet@1.9.3/dist/leaflet.js"));
        assertFalse(blocked(loader, "socket://unpkg.com:443"));
        assertFalse(blocked(loader, "socket://UNPKG.com:443"));
        assertTrue(blocked(loader, "socket://unpkg.com:80"));
        assertTrue(blocked(loader, "socket://evil.net:443"));
        assertTrue(blocked(SOCKET_STREAM, "socket://unpkg.com:443"));

        // a blocked URL resets it
        assertTrue(blocked(loader, "https://unpkg.com/evil@1.0/dist/evil.js"));
        assertTrue(blocked(loader, "socket://unpkg.com:443"));

        WebKitNetworkGuard.allowUrlTemplate("http://tile.example.com:8080/{z}/{x}/{y}.png");
        assertFalse(blocked(loader, "http://tile.example.com:8080/1/2/3.png"));
        assertFalse(blocked(loader, "socket://tile.example.com:8080"));
        WebKitNetworkGuard.allowUrlTemplate("http://tile2.example.com/{z}/{x}/{y}.png");
        assertFalse(blocked(loader, "http://tile2.example.com/1/2/3.png"));
        assertFalse(blocked(loader, "socket://tile2.example.com:80"));
    }

    @Test
    public void testDisallow() {
        String loader = WebKitNetworkGuard.URL_LOADER;
        Pattern p = Pattern.compile("https://example\\.com/");
        WebKitNetworkGuard.allow(p);
        WebKitNetworkGuard.allowUrlTemplate("http://tile.example.com/{z}/{x}/{y}.png");
        assertFalse(blocked(loader, "https://example.com/a.js"));
        assertFalse(blocked(loader, "http://tile.example.com/1/2/3.png"));

        // an equal pattern removes it, Pattern has no equals()
        WebKitNetworkGuard.disallow(Pattern.compile("https://example\\.com/"));
        assertTrue(blocked(loader, "https://example.com/a.js"));
        assertFalse(blocked(loader, "http://tile.example.com/1/2/3.png"));

        WebKitNetworkGuard.disallowUrlTemplate("http://tile.example.com/{z}/{x}/{y}.png");
        assertTrue(blocked(loader, "http://tile.example.com/1/2/3.png"));
        assertEquals(0, WebKitNetworkGuard.allowedCountForTests());
    }

    @Test
    public void testMediaRequestsAlwaysBlocked() {
        // video and audio elements are loaded by JavaFX Media, not by the WebKit loader
        String locator = WebKitNetworkGuard.JFX_MEDIA_PACKAGE + "locator.Locator";
        assertTrue(WebKitNetworkGuard.isWebViewNetworkClass(locator));
        assertTrue(WebKitNetworkGuard.isWebViewNetworkClass(WebKitNetworkGuard.URL_LOADER));
        assertFalse(WebKitNetworkGuard.isWebViewNetworkClass("io.minio.MinioClient"));

        assertTrue(blocked(locator, "http://example.com/video.mp4"));
        assertTrue(blocked(locator, "socket://example.com:80"));
        // not even for URLs allowed for the map
        WebKitNetworkGuard.allow(Pattern.compile("https://example\\.com/"));
        assertTrue(blocked(locator, "https://example.com/audio.mp3"));
        assertTrue(blocked(locator, "socket://example.com:443"));
    }

    @Test
    public void testSocketAllowedOnlyOnSameThread() throws InterruptedException {
        WebKitNetworkGuard.allow(Pattern.compile("https://example\\.com/"));
        String loader = WebKitNetworkGuard.URL_LOADER;
        assertFalse(blocked(loader, "https://example.com/a.js"));
        boolean[] otherThreadBlocked = new boolean[1];
        Thread t = new Thread(() -> otherThreadBlocked[0] = blocked(loader, "socket://example.com:443"));
        t.start();
        t.join();
        assertTrue(otherThreadBlocked[0]);
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
            // outside WebKit, requests go to the previous selector
            ProxySelector.getDefault().select(URI.create("http://example.com/"));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }
}
