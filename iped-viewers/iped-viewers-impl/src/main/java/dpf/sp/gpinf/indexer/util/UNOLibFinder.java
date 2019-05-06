package dpf.sp.gpinf.indexer.util;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class UNOLibFinder {
    
    public static void addUNOJars(String path, List<File> jars) throws URISyntaxException{
        
        ArrayList<URL> urls = new ArrayList<>();
        
        callUnoinfo(path, urls);
        
        //add path to officebean jar
        addUrl(urls, new String(path + "/program/classes/officebean.jar"));
        
        for(URL url : urls)
            jars.add(new File(url.toURI()));
    }
    
    private static void callUnoinfo(String path, ArrayList<URL> urls) {
        Process p;
        try {
            p = Runtime.getRuntime().exec(
                new String[] {
                    new File( new File(path, "program"),
                              "unoinfo").getPath(), "java" });
        } catch (IOException e) {
            System.err.println(
                UNOLibFinder.class.getName() + "::getCustomLoader: exec" +
                " unoinfo: " + e);
            return;
        }
        //FIXME: perhaps remove this one & the whole Drain class entirely
        //we're not doing anything w/ content of stderr anyway
        new Drain(p.getErrorStream()).start();
        int code;
        byte[] buf = new byte[1000];
        int n = 0;
        try {
            InputStream s = p.getInputStream();
            code = s.read();
            for (;;) {
                if (n == buf.length) {
                    if (n > Integer.MAX_VALUE / 2) {
                        System.err.println(
                            UNOLibFinder.class.getName() + "::getCustomLoader:" +
                            " too much unoinfo output");
                        return;
                    }
                    byte[] buf2 = new byte[2 * n];
                    System.arraycopy(buf, 0, buf2, 0, n);
                    buf = buf2;
                }
                int k = s.read(buf, n, buf.length - n);
                if (k == -1) {
                    break;
                }
                n += k;
            }
        } catch (IOException e) {
            System.err.println(
                UNOLibFinder.class.getName() + "::getCustomLoader: reading" +
                " unoinfo output: " + e);
            return;
        }
        int ev;
        try {
            ev = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(
                UNOLibFinder.class.getName() + "::getCustomLoader: waiting for" +
                " unoinfo: " + e);
            return;
        }
        if (ev != 0) {
            System.err.println(
                UNOLibFinder.class.getName() + "::getCustomLoader: unoinfo"
                + " exit value " + ev);
            return;
        }
        String s;
        if (code == '0') {
            s = new String(buf);
        } else if (code == '1') {
            try {
                s = new String(buf, "UTF-16LE");
            } catch (UnsupportedEncodingException e) {
                System.err.println(
                    UNOLibFinder.class.getName() + "::getCustomLoader:" +
                    " transforming unoinfo output: " + e);
                return;
            }
        } else {
            System.err.println(
                UNOLibFinder.class.getName() + "::getCustomLoader: bad unoinfo"
                + " output");
            return;
        }
        addUrls(urls, s, "\0");
    }
    
    private static void addUrls(ArrayList<URL> urls, String data, String delimiter) {
        StringTokenizer tokens = new StringTokenizer( data, delimiter );
        while ( tokens.hasMoreTokens() ) {
            addUrl( urls, tokens.nextToken() );
        }
    }

    private static void addUrl(ArrayList<URL> urls, String singlePath) {
        try {
            urls.add( new File( singlePath).toURI().toURL() );
        } catch ( MalformedURLException e ) {
            // don't add this class path entry to the list of class loader
            // URLs
            System.err.println( UNOLibFinder.class.getName() +
                "::getCustomLoader: bad pathname: " + e );
        }
    }
    
    private static final class Drain extends Thread {
        public Drain(InputStream stream) {
            super("unoinfo stderr drain");
            this.stream = stream;
        }

        @Override
        public void run() {
            try {
                while (stream.read() != -1) {}
            } catch (IOException e) { /* ignored */ }
        }

        private final InputStream stream;
    }

}
