package iped.geo.googlemaps;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/**
 * Registers the URL scheme used by the map WebView to load Google Maps tiles
 * through {@link GoogleTileURLConnection}, which converts WebP tiles to PNG.
 * Google serves roadmap, terrain and hybrid tiles as WebP, a format the JavaFX
 * WebKit can not decode.
 */
public class GoogleTileURLStreamHandlerProvider extends URLStreamHandlerProvider {

    public static final String PROTOCOL = "ipedgoogletile";

    /** Only tile URLs starting with this prefix are loaded by the handler. */
    public static final String TILE_URL_PREFIX = "https://maps.googleapis.com/maps/vt?";

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (!PROTOCOL.equals(protocol)) {
            return null;
        }
        return new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                return new GoogleTileURLConnection(url);
            }
        };
    }

    /**
     * @return the tile URL to be loaded, or null if the given URL is not a Google
     *         Maps tile URL using the custom scheme.
     */
    static String getTileUrl(String url) {
        String prefix = PROTOCOL + ":";
        if (url == null || !url.startsWith(prefix)) {
            return null;
        }
        String tileUrl = url.substring(prefix.length());
        return tileUrl.startsWith(TILE_URL_PREFIX) ? tileUrl : null;
    }

    /**
     * @return a script that makes the Google Maps tile images of the page be loaded
     *         using the custom scheme. It must run before the Google Maps scripts.
     *         The tile coordinates are kept in the URL, so GoogleMutant still
     *         recognizes the tiles.
     */
    public static String getImageSrcRewriteScript() {
        return "(function(){"
                + "var desc=Object.getOwnPropertyDescriptor(HTMLImageElement.prototype,'src');"
                + "Object.defineProperty(HTMLImageElement.prototype,'src',{"
                + "configurable:true,enumerable:desc.enumerable,"
                + "get:function(){return desc.get.call(this);},"
                + "set:function(value){"
                + "if(typeof value==='string'&&value.indexOf('" + TILE_URL_PREFIX + "')===0){value='" + PROTOCOL + ":'+value;}"
                + "desc.set.call(this,value);}"
                + "});"
                + "})();";
    }

}
