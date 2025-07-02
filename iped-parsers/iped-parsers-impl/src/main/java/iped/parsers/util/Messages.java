package iped.parsers.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.localization.LocaleResolver;

import java.text.MessageFormat;

public class Messages {

    private static final String BUNDLE_NAME = "iped-parsers-messages"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static String get(String key) {
        if (RESOURCE_BUNDLE == null) {
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());
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
