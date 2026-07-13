package iped.app.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.localization.LocaleResolver;

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
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());
        }
        return RESOURCE_BUNDLE.getString(key);
    }

}
