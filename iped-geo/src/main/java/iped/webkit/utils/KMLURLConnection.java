package iped.webkit.utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class KMLURLConnection extends URLConnection {

    protected KMLURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() throws IOException {
        System.out.println("Connected:" + this.url);
    }

}
