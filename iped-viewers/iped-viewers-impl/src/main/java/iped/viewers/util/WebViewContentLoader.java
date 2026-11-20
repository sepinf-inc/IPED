package iped.viewers.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.charsetdetector.StandardHtmlEncodingDetector;
import org.w3c.dom.Document;
import org.w3c.dom.html.HTMLDocument;

import javafx.scene.web.WebEngine;

/**
 * Loads HTML from evidence into a WebView with a Content Security Policy that
 * blocks all network access of that document, without changing the HTML.
 * <p>
 * WebKitNetworkGuard blocks network access of all WebViews, except for the
 * URLs allowed for the map, and it can't know which WebView made a request. So
 * the items viewers also get this policy, which doesn't allow those URLs.
 * <p>
 * A meta tag is the only way to set the policy of a local document. It is put
 * in an empty document (the shell), loaded first, and the HTML is then written
 * into that same document, which keeps its policy. The policy doesn't apply to
 * navigation of the whole page, still handled by WebKitNetworkGuard only.
 */
public class WebViewContentLoader {

    /**
     * Only inline content and local resources. No scheme used to reach the network
     * is listed, so they are blocked for all kinds of resources.
     */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'unsafe-inline' 'unsafe-eval' data: blob: file:; form-action 'none'";

    // no doctype, the one of the written HTML (or its absence) defines the rendering mode
    private static final String SHELL_HTML = "<html><head><meta http-equiv=\"Content-Security-Policy\" content=\""
            + CONTENT_SECURITY_POLICY + "\"></head><body></body></html>";

    /** default of WebKit for documents not declaring their encoding */
    private static final Charset DEFAULT_CHARSET = Charset.forName("windows-1252");

    private static final int CHUNK_SIZE = 1 << 16;

    private static File shellFile;

    /**
     * @return the empty document carrying the policy, to be loaded before
     *         {@link #write(WebEngine, File)} is called.
     */
    public static synchronized File getShellFile() throws IOException {
        if (shellFile == null || !shellFile.exists()) {
            File file = File.createTempFile("iped-viewer-", ".html");
            file.deleteOnExit();
            Files.write(file.toPath(), SHELL_HTML.getBytes(StandardCharsets.US_ASCII));
            shellFile = file;
        }
        return shellFile;
    }

    /** @return if the shell document is the one loaded at this location. */
    public static synchronized boolean isShell(String location) {
        return shellFile != null && location != null && location.endsWith(shellFile.getName());
    }

    /**
     * Replaces the content of the shell document, already loaded, by this HTML. Must
     * be called in the JavaFX thread.
     */
    public static void write(WebEngine webEngine, File html) throws IOException {
        Document doc = webEngine.getDocument();
        if (!(doc instanceof HTMLDocument) || !isShell(webEngine.getLocation())) {
            throw new IOException("Document with the content security policy not loaded");
        }
        HTMLDocument htmlDoc = (HTMLDocument) doc;
        Charset charset = detectCharset(html);
        htmlDoc.open();
        try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(html.toPath())), charset)) {
            char[] buf = new char[CHUNK_SIZE];
            // room for a high surrogate left from the previous chunk
            int start = 0, read;
            boolean first = true;
            while ((read = reader.read(buf, start, buf.length - start)) != -1) {
                int len = start + read;
                int offset = 0;
                if (first && len > 0) {
                    first = false;
                    if (buf[0] == '﻿') {
                        // byte order mark
                        offset = 1;
                    }
                }
                // do not split a surrogate pair between two chunks
                boolean splitPair = len > offset && Character.isHighSurrogate(buf[len - 1]);
                int end = splitPair ? len - 1 : len;
                if (end > offset) {
                    htmlDoc.write(new String(buf, offset, end - offset));
                }
                if (splitPair) {
                    buf[0] = buf[len - 1];
                    start = 1;
                } else {
                    start = 0;
                }
            }
            if (start == 1) {
                htmlDoc.write(new String(buf, 0, 1));
            }
        } finally {
            htmlDoc.close();
        }
    }

    /**
     * WebKit is not decoding the file anymore, so do it as browsers do: byte order
     * mark, then encoding declared in the first bytes, then the default one.
     */
    static Charset detectCharset(File html) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(html.toPath()))) {
            Charset charset = new StandardHtmlEncodingDetector().detect(is, new Metadata());
            return charset != null ? charset : DEFAULT_CHARSET;
        }
    }

}
