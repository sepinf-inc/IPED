package iped.geo.googlemaps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.imageio.ImageIO;

/**
 * Loads a Google Maps tile and converts it to PNG if it is a WebP image, which
 * is not supported by the JavaFX WebKit. Other formats are returned unchanged.
 */
public class GoogleTileURLConnection extends URLConnection {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String[] FORWARDED_HEADERS = { "User-Agent", "Accept", "Accept-Language", "Referer" };
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Shared by all tile requests, so they are multiplexed in one HTTP/2 connection
     * instead of each parallel request opening its own connection. Created on the
     * first request, when the default proxy selector is already configured.
     */
    private static class ClientHolder {
        // Redirects are not followed: they would be requested by the threads of the
        // client, out of the control of the network guard of the WebView.
        static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(CONNECT_TIMEOUT).build();
    }

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
        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(new URI(tileUrl)).timeout(REQUEST_TIMEOUT);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException(e);
        }
        // Forwards headers set by the WebView, so the request is similar to the one the
        // WebView would send. Google refuses tile requests without a browser User-Agent.
        // Accept-Encoding is not forwarded, the response would not be decompressed.
        for (String header : FORWARDED_HEADERS) {
            String value = getRequestProperty(header);
            if (value != null) {
                request.setHeader(header, value);
            }
        }
        HttpResponse<byte[]> response;
        try {
            response = ClientHolder.CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected response code " + response.statusCode() + " loading map tile");
        }
        content = response.body();
        contentType = response.headers().firstValue("Content-Type").orElse(null);
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
