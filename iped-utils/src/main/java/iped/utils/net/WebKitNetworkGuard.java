package iped.utils.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blocks network access from JavaFX WebView (WebKit) content, so HTML from
 * evidence can't reach remote servers. Replaces the SecurityManager based
 * policy, removed in Java 24 (JEP 486), see #1522.
 * <p>
 * All WebKit network requests call {@link ProxySelector#select(URI)} with the
 * full URL before any DNS lookup or connection:
 * <ul>
 * <li>HTTP(S) through com.sun.webkit.network.HTTP2Loader
 * (java.net.http.HttpClient), the default loader. It selects the proxy when the
 * request is sent, in the thread of WebKit, and does not follow redirects by
 * itself: each one is a new request made by WebKit;</li>
 * <li>other schemes, and HTTP(S) if the HTTP/2 loader is disabled, through
 * com.sun.webkit.network.URLLoader (HttpURLConnection), which turns the
 * exception thrown here into a load error;</li>
 * <li>WebSockets through com.sun.webkit.network.SocketStreamHandle.</li>
 * </ul>
 * Media of {@code <video>} and {@code <audio>} elements is not loaded by
 * WebKit, but by JavaFX Media (com.sun.media.jfxmedia), which opens its own
 * connections. IPED doesn't use JavaFX Media for anything else, so all its
 * network requests are blocked.
 * <p>
 * WebKit DNS prefetching is not implemented in JavaFX, so there are no DNS
 * lookups outside these paths.
 * <p>
 * Only requests made by WebKit are checked; everything else (MinIO, remote
 * transcription, Elastic, etc.) is passed to the previous selector unchanged.
 * WebKit requests are allowed only for URLs matching patterns registered with
 * {@link #allow(Pattern)}, i.e. the online map resources.
 * <p>
 * For a direct connection, HttpURLConnection opens a plain Socket, which calls
 * select() again with a {@code socket://host:port} URI. It is allowed only for
 * the host and port of the URL just allowed on the same thread.
 */
public class WebKitNetworkGuard extends ProxySelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebKitNetworkGuard.class);

    static final String WEBKIT_NETWORK_PACKAGE = "com.sun.webkit.network.";
    static final String URL_LOADER = WEBKIT_NETWORK_PACKAGE + "URLLoader";
    static final String HTTP2_LOADER = WEBKIT_NETWORK_PACKAGE + "HTTP2Loader";

    /**
     * Returned to the HTTP/2 loader for blocked URLs: nothing listens on port 0, so
     * the request fails when connecting to this proxy and the URL host is not even
     * resolved. An exception thrown to this loader is not handled: it reaches the
     * JavaFX thread and stops the loading of the other resources of the page.
     */
    static final List<Proxy> BLOCKED_PROXY = List.of(
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)));
    /** loads the media of video and audio elements of the WebView content */
    static final String JFX_MEDIA_PACKAGE = "com.sun.media.jfxmedia.";

    private static final StackWalker WALKER = StackWalker.getInstance();

    /** keyed by regex and flags, Pattern has no equals() */
    private static final Map<String, Pattern> allowed = new ConcurrentHashMap<>();

    /** last URL allowed for the URLLoader on this thread, to check its socket */
    private static final ThreadLocal<URI> lastAllowedUrl = new ThreadLocal<>();

    private static final String SOCKET_SCHEME = "socket";

    private final ProxySelector delegate;
    private final Supplier<String> webKitCaller;

    WebKitNetworkGuard(ProxySelector delegate, Supplier<String> webKitCaller) {
        this.delegate = delegate;
        this.webKitCaller = webKitCaller;
    }

    /**
     * Installs the guard as the default ProxySelector. Must be called before the
     * first WebView is created. Safe to call more than once.
     */
    public static synchronized void install() {
        ProxySelector current = ProxySelector.getDefault();
        if (!(current instanceof WebKitNetworkGuard)) {
            ProxySelector.setDefault(new WebKitNetworkGuard(current, WebKitNetworkGuard::findWebKitCaller));
        }
    }

    /** Allows WebKit requests to URLs matching this pattern (from the start). */
    public static void allow(Pattern urlPattern) {
        if (urlPattern != null) {
            allowed.putIfAbsent(key(urlPattern), urlPattern);
        }
    }

    /** Removes a pattern added by {@link #allow(Pattern)}. */
    public static void disallow(Pattern urlPattern) {
        if (urlPattern != null) {
            allowed.remove(key(urlPattern));
        }
    }

    private static String key(Pattern urlPattern) {
        return urlPattern.flags() + ":" + urlPattern.pattern();
    }

    /**
     * Allows a map tile URL template, e.g.
     * {@code https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png}. Placeholders
     * like {s}, {z}, ${x} or {quad} match part of one URL segment, without '/', '?',
     * '#', '@' or ':', so a match can't add user info or a port to the host.
     */
    public static void allowUrlTemplate(String template) {
        Pattern p = templateToPattern(template);
        if (p != null) {
            allow(p);
        }
    }

    /** Removes a template added by {@link #allowUrlTemplate(String)}. */
    public static void disallowUrlTemplate(String template) {
        disallow(templateToPattern(template));
    }

    static Pattern templateToPattern(String template) {
        if (template == null || template.isBlank()) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        Matcher m = Pattern.compile("\\$?\\{[^}]*\\}").matcher(template);
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start()))).append("[^/?#@:]*");
            last = m.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    static boolean isAllowed(URI uri) {
        String url = uri.toString();
        for (Pattern p : allowed.values()) {
            if (p.matcher(url).lookingAt()) {
                return true;
            }
        }
        return false;
    }

    static void clearAllowedForTests() {
        allowed.clear();
        lastAllowedUrl.remove();
    }

    static int allowedCountForTests() {
        return allowed.size();
    }

    /**
     * @return the innermost WebKit network or JavaFX Media class on the stack, or
     *         null.
     */
    private static String findWebKitCaller() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .filter(WebKitNetworkGuard::isWebViewNetworkClass).findFirst().orElse(null));
    }

    static boolean isWebViewNetworkClass(String className) {
        return className.startsWith(WEBKIT_NETWORK_PACKAGE) || className.startsWith(JFX_MEDIA_PACKAGE);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** socket://host:port of the URL allowed just before on this thread */
    static boolean isSocketOfLastAllowedUrl(URI socket) {
        URI url = lastAllowedUrl.get();
        return url != null && url.getHost() != null && url.getHost().equalsIgnoreCase(socket.getHost())
                && port(url) == socket.getPort();
    }

    private boolean isAllowedFromUrlLoader(URI uri) {
        if (SOCKET_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return isSocketOfLastAllowedUrl(uri);
        }
        if (isAllowed(uri)) {
            lastAllowedUrl.set(uri);
            return true;
        }
        lastAllowedUrl.remove();
        return false;
    }

    @Override
    public List<Proxy> select(URI uri) {
        String caller = webKitCaller.get();
        if (caller != null && caller.startsWith(HTTP2_LOADER)) {
            // only for registered URLs
            if (!isAllowed(uri)) {
                LOGGER.debug("Blocked network access from WebView content: {} ({})", uri, caller);
                return BLOCKED_PROXY;
            }
        } else if (caller != null) {
            // besides the HTTP/2 loader, only the URL loader is allowed, for registered URLs
            if (!caller.startsWith(URL_LOADER) || !isAllowedFromUrlLoader(uri)) {
                LOGGER.debug("Blocked network access from WebView content: {} ({})", uri, caller);
                // HttpURLConnection reports it as IOException before connecting
                throw new IllegalArgumentException("Network access blocked for WebView content: " + uri);
            }
        }
        return delegate != null ? delegate.select(uri) : List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        if (delegate != null) {
            delegate.connectFailed(uri, sa, ioe);
        }
    }
}
