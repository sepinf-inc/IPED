package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import dpf.sp.gpinf.indexer.Configuration;

public class MessagesFilter {

    private static final String BUNDLE_NAME = "filters"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private MessagesFilter() {
    }

    private static ResourceBundle getExternalBundle(String bundleName, Locale locale) throws MalformedURLException {
        File file = new File(Configuration.getInstance().configPath, "conf/localization");
        URL[] urls = { file.toURI().toURL() };
        ClassLoader loader = new URLClassLoader(urls);
        return ResourceBundle.getBundle(bundleName, locale, loader);
    }

    public static String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (MissingResourceException e) {
            return defaultValue;
        }
    }

    public static String get(String key) {

        try {
            if (RESOURCE_BUNDLE == null) {
                String localeStr = System.getProperty("iped-locale"); //$NON-NLS-1$
                Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
                RESOURCE_BUNDLE = getExternalBundle(BUNDLE_NAME, locale);
            }
            return RESOURCE_BUNDLE.getString(key);

        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void resetLocale() {
        RESOURCE_BUNDLE = null;
    }
}
