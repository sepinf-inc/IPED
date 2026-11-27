package iped.geo.googlemaps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.Test;

public class GoogleTileURLConnectionTest {

    private static final String SCHEME = GoogleTileURLStreamHandlerProvider.PROTOCOL + ":";

    // 2x2 lossless WebP image with a single red color
    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ/Y/+ByKi/wEA");

    @Test
    public void testWebPConvertedToPNG() throws IOException {
        assertTrue(GoogleTileURLConnection.isWebP(WEBP));
        byte[] png = GoogleTileURLConnection.toPNG(WEBP);
        assertNotNull(png);
        assertFalse(GoogleTileURLConnection.isWebP(png));
        assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G' }, new byte[] { png[0], png[1], png[2], png[3] });
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
    }

    @Test
    public void testOtherFormatsNotDetectedAsWebP() {
        assertFalse(GoogleTileURLConnection.isWebP(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }));
        assertFalse(GoogleTileURLConnection.isWebP("RIFF0000WAVEfmt ".getBytes()));
        assertFalse(GoogleTileURLConnection.isWebP(new byte[0]));
    }

    @Test
    public void testOnlyGoogleTileUrlsAccepted() {
        String tile = "https://maps.googleapis.com/maps/vt?pb=!1m5!1m4!1i12!2i1503!3i2230!4i256";
        assertEquals(tile, GoogleTileURLStreamHandlerProvider.getTileUrl(SCHEME + tile));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(tile));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(SCHEME + "https://example.com/maps/vt?pb=1"));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(SCHEME + "https://maps.googleapis.com.example.com/maps/vt?pb=1"));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(SCHEME + "http://maps.googleapis.com/maps/vt?pb=1"));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(SCHEME + "file:///etc/passwd"));
        assertNull(GoogleTileURLStreamHandlerProvider.getTileUrl(null));
    }

    @Test
    public void testSchemeRegisteredAndOtherUrlsRefused() throws Exception {
        URL url = new URL(SCHEME + "https://example.com/image.webp");
        try {
            url.openConnection().getInputStream();
            throw new AssertionError("URL should have been refused");
        } catch (IOException e) {
            // expected
        }
    }

}
