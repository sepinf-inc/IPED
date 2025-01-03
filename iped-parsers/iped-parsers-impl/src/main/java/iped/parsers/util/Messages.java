package iped.parsers.util;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.text.MessageFormat;

public class Messages {

    private static final String BUNDLE_NAME = "iped-parsers-messages"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static String get(String key) {
        if (RESOURCE_BUNDLE == null) {
            String str = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
            Locale locale = str != null ? Locale.forLanguageTag(str) : Locale.getDefault();
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, locale);
        }
        try {
            return RESOURCE_BUNDLE.getString(key);

        } catch (MissingResourceException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String getString(String key, Object... args) {
        String value = get(key);
        if (args != null)
            value = MessageFormat.format(value, args);
        return value;
    }    
}
