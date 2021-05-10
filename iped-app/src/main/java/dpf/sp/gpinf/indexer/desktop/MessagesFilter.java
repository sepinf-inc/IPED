package dpf.sp.gpinf.indexer.desktop;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class MessagesFilter {

    private static final String BUNDLE_NAME = "iped-filters"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private MessagesFilter() {
    }

    public static String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (MissingResourceException e) {
            return defaultValue;
        }
    }

    public static String get(String key) {

        if (RESOURCE_BUNDLE == null) {
            String localeStr = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
            Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
            RESOURCE_BUNDLE = iped3.util.Messages.getExternalBundle(BUNDLE_NAME, locale);
        }
        return RESOURCE_BUNDLE.getString(key);
    }

}
