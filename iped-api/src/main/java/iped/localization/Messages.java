package iped.localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

import iped.io.URLUtil;

public class Messages {

    public static final String BUNDLES_FOLDER = "localization";
    public static final String BUNDLES_FOLDER_PREFIX = "iped-app/resources/";

    private Messages() {
    }

    public static ResourceBundle getExternalBundle(String bundleName, Locale locale) {
        File file = null;
        try {
            URL url = URLUtil.getURL(Messages.class);
            file = new File(new File(url.toURI()).getParentFile().getParentFile(), BUNDLES_FOLDER);
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
        }
        if (file != null && !file.exists()) {
            File baseFile = new File(System.getProperty("user.dir"));
            do {
                baseFile = baseFile.getParentFile();
                file = new File(baseFile, BUNDLES_FOLDER_PREFIX + BUNDLES_FOLDER);
            } while (!file.exists());
        }
        try {
            URL[] urls = { file.toURI().toURL() };
            ClassLoader loader = new URLClassLoader(urls);
            return ResourceBundle.getBundle(bundleName, locale, loader, new UTF8Control());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static class UTF8Control extends Control {

        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
                boolean reload) throws IllegalAccessException, InstantiationException, IOException {
            // The below is a copy of the default implementation.
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}
