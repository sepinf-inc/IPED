package iped3.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.ResourceBundle;

import iped3.configuration.IConfigurationDirectory;

public abstract class Messages {

    public static final String LOCALE_SYS_PROP = "iped-locale";
    public static final String BUNDLES_FOLDER = "localization";
    public static final String BUNDLES_FOLDER_PREFIX = "iped-app/resources/";

    private static final String BUNDLE_NAME = "iped-basicprops"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static ResourceBundle getExternalBundle(String bundleName, Locale locale) {
        File file = null;
        String baseFolder = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        if (baseFolder != null) {
            file = new File(baseFolder, BUNDLES_FOLDER);
        } else {
            File baseFile = new File(System.getProperty("user.dir"));
            do {
                baseFile = baseFile.getParentFile();
                file = new File(baseFile, BUNDLES_FOLDER_PREFIX + BUNDLES_FOLDER);
            } while (!file.exists());
        }
        try {
            URL[] urls = { file.toURI().toURL() };
            ClassLoader loader = new URLClassLoader(urls);
            return ResourceBundle.getBundle(bundleName, locale, loader);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getString(String key) {
        if (RESOURCE_BUNDLE == null) {
            String localeStr = System.getProperty(LOCALE_SYS_PROP); // $NON-NLS-1$
            Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
            RESOURCE_BUNDLE = getExternalBundle(BUNDLE_NAME, locale);
        }
        return RESOURCE_BUNDLE.getString(key);
    }
}
