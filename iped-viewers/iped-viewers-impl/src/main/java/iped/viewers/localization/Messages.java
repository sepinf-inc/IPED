package iped.viewers.localization;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.localization.LocaleResolver;

public class Messages {

    private static final String BUNDLE_NAME = "iped-viewer-messages"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static String getString(String key) {
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
}
