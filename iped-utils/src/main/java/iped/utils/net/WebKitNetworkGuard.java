package iped.utils.net;

import java.io.IOException;
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
 * <li>HTTP(S) through com.sun.webkit.network.URLLoader (HttpURLConnection),
 * which turns the exception thrown here into a load error;</li>
 * <li>WebSockets through com.sun.webkit.network.SocketStreamHandle.</li>
 * </ul>
 * The HTTP/2 loader (java.net.http.HttpClient, default on Java 12+) may select
 * the proxy asynchronously, without WebKit frames on the stack, so it is
 * disabled by {@link #install()}. WebKit DNS prefetching is not implemented in
 * JavaFX, so there are no DNS lookups outside these paths.
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

    static final String WEBKIT_NETWORK_PACKAGE = "com.sun.webkit.network."; //$NON-NLS-1$
    static final String URL_LOADER = WEBKIT_NETWORK_PACKAGE + "URLLoader"; //$NON-NLS-1$

    /** Must be set before the first WebView is created, read once by JavaFX. */
    public static final String USE_HTTP2_LOADER_PROP = "com.sun.webkit.useHTTP2Loader"; //$NON-NLS-1$

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
        System.setProperty(USE_HTTP2_LOADER_PROP, "false"); //$NON-NLS-1$
        ProxySelector current = ProxySelector.getDefault();
        if (!(current instanceof WebKitNetworkGuard)) {
            ProxySelector.setDefault(new WebKitNetworkGuard(current, WebKitNetworkGuard::findWebKitCaller));
        }
    }

    /** Allows WebKit requests to URLs matching this pattern (from the start). */
    public static void allow(Pattern urlPattern) {
        if (urlPattern != null) {
            allowed.putIfAbsent(urlPattern.flags() + ":" + urlPattern.pattern(), urlPattern); //$NON-NLS-1$
        }
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

    static Pattern templateToPattern(String template) {
        if (template == null || template.isBlank()) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        Matcher m = Pattern.compile("\\$?\\{[^}]*\\}").matcher(template); //$NON-NLS-1$
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start()))).append("[^/?#@:]*"); //$NON-NLS-1$
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

    /** @return the innermost WebKit network class on the stack, or null. */
    private static String findWebKitCaller() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .filter(c -> c.startsWith(WEBKIT_NETWORK_PACKAGE)).findFirst().orElse(null));
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
        if (caller != null) {
            // only the HTTP loader is allowed, and only for registered URLs
            if (!caller.startsWith(URL_LOADER) || !isAllowedFromUrlLoader(uri)) {
                LOGGER.debug("Blocked network access from WebView content: {} ({})", uri, caller); //$NON-NLS-1$
                // HttpURLConnection reports it as IOException before connecting
                throw new IllegalArgumentException("Network access blocked for WebView content: " + uri); //$NON-NLS-1$
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
