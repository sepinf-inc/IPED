package iped.geo.googlemaps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/**
 * Loads a Google Maps tile and converts it to PNG if it is a WebP image, which
 * is not supported by the JavaFX WebKit. Other formats are returned unchanged.
 */
public class GoogleTileURLConnection extends URLConnection {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String[] FORWARDED_HEADERS = { "User-Agent", "Accept", "Accept-Language", "Referer" };
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;

    private byte[] content;
    private String contentType;

    protected GoogleTileURLConnection(URL url) {
        super(url);
    }

    @Override
    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }
        String tileUrl = GoogleTileURLStreamHandlerProvider.getTileUrl(url.toString());
        if (tileUrl == null) {
            throw new IOException("Not a Google Maps tile URL");
        }
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) new URI(tileUrl).toURL().openConnection();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        // Forwards headers set by the WebView, so the request is similar to the one the
        // WebView would send. Google refuses tile requests without a browser User-Agent.
        // Accept-Encoding is not forwarded, the response would not be decompressed.
        for (String header : FORWARDED_HEADERS) {
            String value = getRequestProperty(header);
            if (value != null) {
                conn.setRequestProperty(header, value);
            }
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        try (InputStream is = conn.getInputStream()) {
            content = is.readAllBytes();
            contentType = conn.getContentType();
        } finally {
            conn.disconnect();
        }
        if (isWebP(content)) {
            byte[] png = toPNG(content);
            if (png != null) {
                content = png;
                contentType = PNG_CONTENT_TYPE;
            }
        }
        connected = true;
    }

    static boolean isWebP(byte[] data) {
        return data.length >= 12
                && "RIFF".equals(new String(data, 0, 4, StandardCharsets.ISO_8859_1))
                && "WEBP".equals(new String(data, 8, 4, StandardCharsets.ISO_8859_1));
    }

    /**
     * @return the image encoded as PNG, or null if it could not be decoded.
     */
    static byte[] toPNG(byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            return null;
        }
        return out.toByteArray();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return new ByteArrayInputStream(content);
    }

    @Override
    public String getContentType() {
        try {
            connect();
        } catch (IOException e) {
            return null;
        }
        return contentType;
    }

    @Override
    public long getContentLengthLong() {
        try {
            connect();
        } catch (IOException e) {
            return -1;
        }
        return content.length;
    }

    @Override
    public String getHeaderField(String name) {
        if ("content-type".equalsIgnoreCase(name)) {
            return getContentType();
        }
        if ("content-length".equalsIgnoreCase(name)) {
            return Long.toString(getContentLengthLong());
        }
        return null;
    }

}
