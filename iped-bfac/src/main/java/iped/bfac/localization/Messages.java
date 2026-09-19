package iped.bfac.localization;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.localization.LocaleResolver;

public class Messages {

    private static final String BUNDLE_NAME = "iped-bfac-messages"; //$NON-NLS-1$

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

    public static String getString(String key, Object... args) {
        String value = getString(key);
        if (args != null && args.length > 0) {
            value = MessageFormat.format(value, args);
        }
        return value;
    }
}
